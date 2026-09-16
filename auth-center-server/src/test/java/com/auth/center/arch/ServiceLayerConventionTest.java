package com.auth.center.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 后端服务层「分层 + 命名」规范门禁（auth-center）—— 构建期机械校验，违反即 {@code mvn test} 失败。
 *
 * auth-center 是独立 Maven / Java21 构建，不在 bi-backend reactor 内，也不被 {@code BackendWideGuardTest}
 * 全树扫描覆盖，故在此模块内独立落一份等价门禁。零外部依赖（仅 JDK + junit-jupiter），离线可跑，不加载 Spring 上下文，直接扫描本模块 {@code
 * src/main/java} 源码。
 *
 *   - R1 —— {@code service} 包段（非 {@code impl} 段）下只允许接口 / 注解；class / enum / record 一律禁止，须移到
 *       {@code impl/}（业务实现）或 {@code util/}(静态工具) / {@code support/}(@Component) / {@code
 *       model/}(值对象) / {@code exception/}(异常)。
 *   - R2 —— {@code service} 包段下的接口必须以 {@code I} + 大写字母开头。
 *   - R3 —— 名为 {@code *Impl} 的类型必须落在 {@code impl} 包段内；gRPC 端点适配器（{@code grpc/} 包内， extends
 *       生成的 {@code *ImplBase}）豁免。
 *   - R4 —— {@code controller} 包段下禁止直接 import {@code *Mapper}；须经 service 层访问数据，避免绕过
 *       service 层、丢失事务边界。
 */
class ServiceLayerConventionTest {

    /** 匹配源码首个 {@code package x.y.z;} 声明。 */
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");

    /** 匹配 controller 包内对 *Mapper 的直接 import（R4）。 */
    private static final Pattern MAPPER_IMPORT =
            Pattern.compile("(?m)^\\s*import\\s+[\\w.]+\\.mapper\\.[A-Za-z0-9_]+Mapper\\s*;");

    @Test
    void serviceLayerLayeringAndNamingConventionHolds() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"), "src", "main", "java");
        if (!Files.isDirectory(root)) {
            return;
        }
        List<String> violations = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            for (Path file : javaFiles) {
                String fileName = file.getFileName().toString();
                String typeName = fileName.substring(0, fileName.length() - ".java".length());
                if (typeName.equals("package-info") || typeName.equals("module-info")) {
                    continue;
                }
                String source = Files.readString(file, StandardCharsets.UTF_8);
                Matcher pm = PACKAGE.matcher(source);
                if (!pm.find()) {
                    continue;
                }
                List<String> segments = Arrays.asList(pm.group(1).split("\\."));
                boolean inService = segments.contains("service");
                boolean inImpl = segments.contains("impl");
                // R4 —— controller 包段下禁止直接 import *Mapper（绕过 service 层、丢事务边界）。
                if (segments.contains("controller") && MAPPER_IMPORT.matcher(source).find()) {
                    violations.add(
                            "[R4 controller 直接依赖 *Mapper，应经 service 层] "
                                    + pm.group(1)
                                    + "."
                                    + typeName);
                }
                String kind = primaryTypeKind(source, typeName);
                if (kind == null) {
                    continue; // 主类型识别不到（极少），跳过以免误报
                }
                boolean isInterface = kind.equals("interface");
                boolean isAnnotation = kind.equals("@interface");
                // gRPC 端点适配器（extends 生成的 *ImplBase，gRPC SPI 强制 *Impl 命名）属传输/端点层，
                // 类比 REST 的 controller/，合法落在 grpc/ 包，豁免 R3。
                boolean isGrpcEndpoint = segments.contains("grpc");
                String fqn = pm.group(1) + "." + typeName;

                if (inService && !inImpl && !isInterface && !isAnnotation) {
                    violations.add(
                            "[R1 非接口类落在 service/，应移到 impl/ —— service 及其子包只放接口] "
                                    + fqn
                                    + " ("
                                    + kind
                                    + ")");
                }
                if (inService && !inImpl && isInterface && !typeName.matches("I[A-Z].*")) {
                    violations.add("[R2 service 层接口缺 I* 前缀] " + fqn);
                }
                if (typeName.endsWith("Impl") && !inImpl && !isGrpcEndpoint) {
                    violations.add("[R3 *Impl 未落在 impl/ 包] " + fqn);
                }
            }
        }
        if (!violations.isEmpty()) {
            Collections.sort(violations);
            fail(
                    "后端服务层分层 / 命名规范违规 "
                            + violations.size()
                            + " 处（详见 ARCHITECTURE.md §3.4）：\n  "
                            + String.join("\n  ", violations));
        }
    }

    /**
     * 返回与文件名同名的「主类型」种类。
     *
     * @param source 源码全文
     * @param typeName 期望的主类型名（= 文件名去后缀）
     * @return {@code interface} / {@code @interface} / {@code class} / {@code enum} / {@code
     *     record}；识别不到返回 {@code null}
     */
    private static String primaryTypeKind(String source, String typeName) {
        Matcher m =
                Pattern.compile(
                                "(?m)^[ \\t]*(?:(?:public|final|abstract|sealed|non-sealed|strictfp)[ \\t]+)*"
                                        + "(@interface|interface|class|enum|record)[ \\t]+"
                                        + Pattern.quote(typeName)
                                        + "\\b")
                        .matcher(source);
        return m.find() ? m.group(1) : null;
    }
}
