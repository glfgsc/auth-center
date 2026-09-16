package com.auth.center.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link ClusterEndpointStartupValidator} 单测 —— 生产 profile 下回环地址须 fail-fast。
 *
 * 这道闸拦的是「配置没落位」的静默降级:镜像若构建自尚未接入 Nacos 的代码,{@code optional:nacos:} 会静默跳过,而清单里对应的 env
 * 已被删除,于是全部回落到镜像内的 localhost 默认值 —— 服务照常起来,但每一次跨服务调用都打向自己的容器。
 *
 * 误判和漏判在这里同样危险:判宽了会把正常配置拦下来,表现同样是「服务起不来」,而且更难解释(明明配对了)。故 {@code notLoopback} 那组把容易踩的形态逐个钉住 ——
 * 集群 DNS 名、含 localhost 字样的参数值、以 localhost 开头的真实主机名。
 */
class ClusterEndpointStartupValidatorTest {

    private static final String PROD_DB =
            "jdbc:mysql://mysql.bi-prod.svc.cluster.local:3306/auth_center?useSSL=false";
    private static final String LOCAL_DB =
            "jdbc:mysql://localhost:3306/auth_center?useSSL=false&serverTimezone=Asia/Shanghai";

    private static ClusterEndpointStartupValidator validator(
            String db, String redisHost, String casUrl, String profiles) throws Exception {
        ClusterEndpointStartupValidator v = new ClusterEndpointStartupValidator();
        set(v, "datasourceUrl", db);
        set(v, "redisHost", redisHost);
        set(v, "casPublicBaseUrl", casUrl);
        set(v, "activeProfiles", profiles);
        return v;
    }

    private static void set(Object target, String field, String value) throws Exception {
        Field f = ClusterEndpointStartupValidator.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    void prodWithLoopbackDatasourceFailsFast() throws Exception {
        // 核心场景:env 已删 + 镜像无 Nacos 通道 → 连接串回落到 localhost → 拒绝启动。
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> validator(LOCAL_DB, "redis.bi-prod", "", "prod").validate());
        // 报错必须自带排查路径,否则现场只知道「起不来」
        assertTrue(e.getMessage().contains("spring.datasource.url"), e.getMessage());
        assertTrue(e.getMessage().contains("Nacos"), e.getMessage());
    }

    @Test
    void prodWithLoopbackRedisFailsFast() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> validator(PROD_DB, "localhost", "", "prod").validate());
    }

    @Test
    void prodReportsEveryOffenderNotJustTheFirst() throws Exception {
        // 只报第一个会让人修一条重启一次,循环好几轮
        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                validator(LOCAL_DB, "127.0.0.1", "http://localhost:8090", "prod")
                                        .validate());
        assertTrue(e.getMessage().contains("spring.datasource.url"), e.getMessage());
        assertTrue(e.getMessage().contains("spring.data.redis.host"), e.getMessage());
        assertTrue(e.getMessage().contains("auth.cas.public-base-url"), e.getMessage());
    }

    @Test
    void prodWithClusterAddressesPasses() throws Exception {
        assertDoesNotThrow(
                () ->
                        validator(
                                        PROD_DB,
                                        "redis.bi-prod.svc.cluster.local",
                                        "https://bi.example.com",
                                        "prod")
                                .validate());
    }

    @Test
    void emptyValuesAreNotOffenders() throws Exception {
        // 留空 = 未配置,由各自的既有校验负责(CAS 基地址留空更是合法的:从实际连接推断)
        assertDoesNotThrow(() -> validator(PROD_DB, "redis.bi-prod", "", "prod").validate());
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "", "local,debug"})
    void nonProdNeverBlocks(String profiles) throws Exception {
        // 本地开发正是靠这些 localhost 默认值跑起来的
        assertDoesNotThrow(
                () ->
                        validator(LOCAL_DB, "localhost", "http://localhost:8090", profiles)
                                .validate());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "localhost",
                "127.0.0.1",
                "http://localhost:8090",
                "http://127.0.0.1:8090/path",
                "jdbc:mysql://localhost:3306/db",
                "redis://[::1]:6379/1",
                "postgresql://u:p@localhost:5432/db",
                "http://0.0.0.0:8080"
            })
    void loopbackForms(String value) throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> validator(PROD_DB, value, "", "prod").validate(),
                "应判为回环: " + value);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "redis.bi-prod.svc.cluster.local",
                "mysql.bi-prod.svc.cluster.local",
                // 以 localhost 开头的真实主机名 —— 裸子串匹配会在这里误判
                "localhost-proxy.internal",
                "localhostess.example.com",
                // 参数值里含 localhost 字样,主机位是集群地址
                "jdbc:mysql://mysql.bi-prod:3306/db?sslHost=localhost-ca",
                "https://bi.example.com:8443/auth"
            })
    void notLoopback(String value) throws Exception {
        assertDoesNotThrow(
                () -> validator(PROD_DB, value, "", "prod").validate(), "不该判为回环: " + value);
    }
}
