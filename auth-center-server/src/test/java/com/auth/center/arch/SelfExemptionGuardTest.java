package com.auth.center.arch;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 「自我豁免」门禁 —— 预置权限集不得同时持有配置数据安全策略与绕过数据安全策略的能力。
 *
 * 为什么需要这条：授权体系里最难自查的一类缺陷是「配策略的人自己能绕过去」。它不报错、不越权、每个端点的鉴权都写得对，只有把两组能力放在一起看才暴露 —— 而人工评审几乎不会这么看。
 *
 * 具体到本仓：{@code platform_analyst} 曾同时持 {@code security:rls}/{@code security:cls}（配行列策略）与 {@code
 * datasource:edit}（当时兼管裸 SQL，绕 RLS/CLS）。拆出 {@code datasource:raw_query} 后，
 * 这条断言保证同类组合不会在下次加能力码时悄悄复活。
 *
 * 扫的是迁移脚本里的种子而不是运行期库：门禁要在 CI 拦住，不能依赖某个环境的数据状态。
 */
class SelfExemptionGuardTest {

    /** 能配置数据安全策略 —— 持有者定义「谁能看哪些行、哪些列」。 */
    private static final List<String> POLICY_AUTHORING =
            List.of("security:rls", "security:cls", "security:mask_rule");

    /**
     * 能绕过数据安全策略 —— 调用方自带 SQL，服务端无表锚可施策略，等价于该数据源全表全列可见。
     *
     * 新增任何「让调用方自带 SQL / 自带连接串」的能力码时必须加进本列表，否则门禁形同虚设。
     */
    private static final List<String> POLICY_BYPASS = List.of("datasource:raw_query");

    /**
     * 允许的例外 —— {@code admin} 是超管，它本就持全部能力，把它算进来只会让门禁永远红。
     *
     * 超管的自我豁免不是设计缺陷而是定义：它的约束手段是「谁能拿到 admin」，不是能力位切分。
     */
    private static final List<String> EXEMPT_SETS = List.of("admin");

    private static final Pattern SEED_UPDATE =
            Pattern.compile(
                    "UPDATE\\s+auth_permission_set\\s+SET\\s+capabilities\\s*=\\s*'(\\[[^']*\\])'"
                            + "\\s*WHERE\\s+code\\s*=\\s*'([\\w-]+)'",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    @Test
    @DisplayName("预置权限集不得同时持有「配策略」与「绕策略」的能力")
    void presetPermissionSetsMustNotSelfExempt() throws IOException {
        Path migrationDir = Paths.get("src/main/resources/db/migration");
        if (!Files.isDirectory(migrationDir)) {
            fail("找不到迁移目录: " + migrationDir.toAbsolutePath());
        }

        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.list(migrationDir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = SEED_UPDATE.matcher(sql);
                while (m.find()) {
                    String caps = m.group(1);
                    String setCode = m.group(2);
                    if (EXEMPT_SETS.contains(setCode)) {
                        continue;
                    }
                    List<String> authoring =
                            POLICY_AUTHORING.stream().filter(caps::contains).toList();
                    List<String> bypass = POLICY_BYPASS.stream().filter(caps::contains).toList();
                    if (!authoring.isEmpty() && !bypass.isEmpty()) {
                        violations.add(
                                f.getFileName()
                                        + " 的权限集 '"
                                        + setCode
                                        + "' 同时持有配策略能力 "
                                        + authoring
                                        + " 与绕策略能力 "
                                        + bypass
                                        + " —— 自己配的行列策略自己能绕过去");
                    }
                }
            }
        }

        if (!violations.isEmpty()) {
            fail(
                    "自我豁免违规 "
                            + violations.size()
                            + " 处（配数据安全策略的人不得同时拥有绕过它的能力）:\n  "
                            + String.join("\n  ", violations));
        }
    }
}
