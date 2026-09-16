package com.auth.center.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.auth.center.AuthCenterApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * {@link ClusterEndpointStartupValidator} 的上下文级验证 —— 这道闸在真实启动流程里确实拦得住。
 *
 * 单测只证明 {@code validate()} 的判断对。但这个 bean 拦不拦得住启动,取决于另外几件单测看不到的事: {@code @Component}
 * 有没有被扫到、{@code @Lazy(false)} 在 auth-center 的全局惰性初始化 ({@code
 * spring.main.lazy-initialization=true},见 k8s 清单)下有没有生效、{@code InitializingBean} 抛出的异常会不会被 Spring
 * 吞掉。这三样任一不成立,闸就形同虚设 —— 而它的全部价值就在于「真的起不来」。
 *
 * 故这里起真实上下文,并显式打开惰性初始化复刻线上配置。对照组用同一份配置只换掉回环地址,必须起得来 —— 只断言「拦住了」的话,一个恒抛异常的坏实现也会通过。
 */
class ClusterEndpointGateContextTest {

    /**
     * 生产 profile 会同时触发 {@link InternalTokenStartupValidator},给它一个不含开发标记的强令牌让它放行 ——
     * 否则两道闸混在一起,分不清是哪个拦下来的。
     */
    private static String[] args(String redisHost) {
        return new String[] {
            "--spring.datasource.url=jdbc:h2:mem:auth_gate;MODE=MYSQL;DB_CLOSE_DELAY=-1",
            "--spring.datasource.driver-class-name=org.h2.Driver",
            "--spring.flyway.enabled=false",
            "--spring.profiles.active=prod",
            // 复刻线上:auth-center 的清单开着全局惰性初始化,@Lazy(false) 正是为它准备的
            "--spring.main.lazy-initialization=true",
            "--auth.internal-service-token=ctx-test-token-strong-enough-for-startup-validation",
            "--spring.data.redis.host=" + redisHost,
        };
    }

    private static ConfigurableApplicationContext run(String redisHost) {
        SpringApplication app = new SpringApplication(AuthCenterApplication.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        return app.run(args(redisHost));
    }

    @Test
    void prodWithLoopbackRedisRefusesToStart() {
        Exception e = assertThrows(Exception.class, () -> run("localhost").close());
        String chain = chainMessages(e);
        assertTrue(chain.contains("拒绝启动"), chain);
        assertTrue(chain.contains("spring.data.redis.host"), chain);
    }

    @Test
    void prodWithClusterRedisStartsFine() {
        try (ConfigurableApplicationContext ctx = run("redis.bi-prod.svc.cluster.local")) {
            assertNotNull(ctx.getBean(ClusterEndpointStartupValidator.class), "闸本身应在容器里");
            assertTrue(ctx.isRunning());
        }
    }

    private static String chainMessages(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = t; c != null; c = c.getCause()) {
            sb.append(c.getMessage()).append('\n');
            if (c.getCause() == c) {
                break;
            }
        }
        return sb.toString();
    }
}
