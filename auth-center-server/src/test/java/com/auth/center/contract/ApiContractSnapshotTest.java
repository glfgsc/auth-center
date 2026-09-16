package com.auth.center.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * API 合约快照测试 —— 自动发现 auth-center 服务所有 {@code @RequestMapping} 端点,序列化为 JSON 快照文件,与已提交的基线对比。
 *
 * 任何端点的 URL / HTTP 方法 / handler 签名变更都会导致此测试失败,迫使开发者显式更新快照文件,从而在 code review 阶段捕获不经意的 API 变动。
 *
 * 快照同时是前端合约测试(bi-front {@code src/api/__tests__/contract.test.ts})的数据源:前端跨目录读取 {@code
 * api-contract/endpoints.json},校验每个前端调用的 URL 都在后端注册。
 *
 * 首次运行时自动生成快照文件 —— 提交后即为基线。
 */
@SpringBootTest(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:auth_center_contract;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.flyway.enabled=false",
            // application.yml 的 redis.host 默认空串,Lettuce 工厂装配期即失败;
            // 给个占位 host 让工厂可创建(连接是懒建立的,测试不触达 Redis)。
            "spring.data.redis.host=localhost"
        })
@AutoConfigureMockMvc
class ApiContractSnapshotTest {

    private static final String SNAPSHOT_PATH = "src/test/resources/api-contract/endpoints.json";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private final ObjectMapper om = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * 枚举所有 {@code com.auth} 包下的 handler 端点,与快照文件对比。
     *
     * @throws Exception 文件 IO 或 JSON 序列化异常
     */
    @Test
    void endpointRegistryMatchesSnapshot() throws Exception {
        Map<RequestMappingInfo, HandlerMethod> methods = handlerMapping.getHandlerMethods();

        List<Map<String, Object>> endpoints =
                methods.entrySet().stream()
                        .filter(
                                e ->
                                        e.getValue()
                                                .getBeanType()
                                                .getPackageName()
                                                .startsWith("com.auth"))
                        .map(this::toDescriptor)
                        .sorted(
                                Comparator.comparing(
                                                (Map<String, Object> m) ->
                                                        ((List<?>) m.get("patterns"))
                                                                .stream()
                                                                        .map(Object::toString)
                                                                        .min(String::compareTo)
                                                                        .orElse(""))
                                        .thenComparing(
                                                m ->
                                                        ((List<?>) m.get("methods"))
                                                                .stream()
                                                                        .map(Object::toString)
                                                                        .min(String::compareTo)
                                                                        .orElse(""))
                                        // handler 作为全序兜底键:patterns+methods 相同时(如 GET 自动派生
                                        // HEAD 等)消除 map 迭代顺序导致的序列化非确定性,避免快照偶发漂移
                                        .thenComparing(m -> (String) m.get("handler")))
                        .collect(Collectors.toList());

        // 归一化行尾:Jackson INDENT_OUTPUT 按 System.lineSeparator() 换行(Windows=CRLF),
        // 而快照文件受 .gitattributes(eol=lf)约束为 LF。行尾不属于 API 合约,统一成 LF 比对,
        // 避免本测试在 Windows 上因 CRLF/LF 差异误报。
        String actual = om.writeValueAsString(endpoints).replace("\r\n", "\n");

        Path snapshotPath = Path.of(SNAPSHOT_PATH);
        if (!Files.exists(snapshotPath)) {
            Files.createDirectories(snapshotPath.getParent());
            Files.writeString(snapshotPath, actual, StandardCharsets.UTF_8);
            fail(
                    "Snapshot created at "
                            + snapshotPath.toAbsolutePath()
                            + " — commit it and re-run");
        }

        String expected =
                Files.readString(snapshotPath, StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(
                expected,
                actual,
                "API contract changed! If intentional, delete "
                        + SNAPSHOT_PATH
                        + " and re-run to regenerate.");
    }

    /**
     * 将单条 {@link RequestMappingInfo} + {@link HandlerMethod} 转为可序列化的描述。
     *
     * @param entry handler mapping 条目
     * @return 包含 patterns / methods / handler / returnType 的有序 Map
     */
    private Map<String, Object> toDescriptor(Map.Entry<RequestMappingInfo, HandlerMethod> entry) {
        RequestMappingInfo info = entry.getKey();
        HandlerMethod hm = entry.getValue();

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(
                "patterns", info.getPatternValues().stream().sorted().collect(Collectors.toList()));
        descriptor.put(
                "methods",
                info.getMethodsCondition().getMethods().stream()
                        .map(Enum::name)
                        .sorted()
                        .collect(Collectors.toList()));
        descriptor.put(
                "handler", hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName());
        descriptor.put("returnType", hm.getReturnType().getParameterType().getSimpleName());
        return descriptor;
    }
}
