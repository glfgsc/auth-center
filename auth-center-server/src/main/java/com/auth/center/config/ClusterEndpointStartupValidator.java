package com.auth.center.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 生产 profile 下拒绝以 localhost 回环地址启动 —— 配置没落位的静默降级闸。
 *
 * 运行期配置在 Nacos({@code auth-center/nacos-config/auth-center.yml}),清单里不再带对应 env。镜像若构建自没有 Nacos
 * 配置通道的旧代码,{@code spring.config.import} 的 {@code optional:nacos:} 会静默跳过,于是既没有 env、也没有 Nacos,所有
 * {@code ${VAR:默认值}} 一律落到镜像内的开发默认值 —— 全是 {@code localhost}。此时服务
 * 照常启动、健康检查照常通过,但每一次跨服务调用都打向自己的容器,现场只剩一句「连不上 localhost:xxxx」。
 *
 * 回环地址在生产集群里永远是错的(各服务分属不同 Pod,只能经 k8s Service DNS 互访),所以它是「配置没落位」的可靠信号。生产 profile
 * 下发现即拒绝启动;非生产 profile 不校验 —— 本地开发正是靠这些 localhost 默认值跑起来的。
 *
 * 与 Python 运行时的 {@code NacosConfigUnavailable} 是同一件事的两侧:那边判定「Nacos 启用却拿不到配置」,这边
 * 判定「配置没落位的结果」。两侧不同是因为 Java 侧的 {@code optional:nacos:} 缺配置时还有镜像内 {@code application.yml}
 * 兜着,拿不到与拿到默认值无法在加载期区分,只能按结果判。
 *
 * {@code @Lazy(false)}:auth-center 开启了全局 {@code spring.main.lazy-initialization},无注入方的 {@code
 * InitializingBean} 会被跳过而永不校验;显式声明非惰性,保证启动期一定执行。
 */
@Component
@Lazy(false)
public class ClusterEndpointStartupValidator implements InitializingBean {

    private static final Logger log =
            LoggerFactory.getLogger(ClusterEndpointStartupValidator.class);

    @Value("${auth.cas.public-base-url:}")
    private String casPublicBaseUrl;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.data.redis.host:}")
    private String redisHost;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /** 生产 profile 下逐项检查跨服务地址,命中回环即拒绝启动。 */
    void validate() {
        if (!isProductionProfile()) {
            return;
        }
        Map<String, String> offenders = new LinkedHashMap<>();
        check(offenders, "spring.datasource.url", datasourceUrl);
        check(offenders, "spring.data.redis.host", redisHost);
        // CAS 回调基地址留空是合法的(留空 = 从实际连接推断),只在显式配成回环时才算错
        check(offenders, "auth.cas.public-base-url", casPublicBaseUrl);
        if (offenders.isEmpty()) {
            log.info("[CONFIG] 跨服务地址校验通过 —— 无回环地址");
            return;
        }
        throw new IllegalStateException(
                "[CONFIG] 生产 profile 下这些配置仍是回环地址,拒绝启动:"
                        + offenders
                        + "。集群内各服务分属不同 Pod,localhost 指向本容器自己,永远连不上目标 —— 这通常意味着"
                        + "配置没落位:本镜像可能构建自尚未接入 Nacos 的代码(spring.config.import 的 optional:nacos: "
                        + "静默跳过),而清单里对应的 env 已被删除,于是回落到了镜像内的开发默认值。排查:确认 Nacos 上"
                        + "已发布 auth-center.yml,且本镜像的代码含 spring.config.import 与 spring.cloud.nacos.config 配置;"
                        + "临时绕过可在清单里显式注入对应 env(env 优先级高于 Nacos)。");
    }

    /** 值非空且指向回环时记入 offenders;留空视为未配置,由各自的既有校验负责。 */
    private static void check(Map<String, String> offenders, String key, String value) {
        if (StringUtils.hasText(value) && isLoopback(value)) {
            offenders.put(key, value);
        }
    }

    /**
     * 回环主机名,出现在 URL 的主机位({@code //} 或 {@code @} 之后)或整个值就是它时命中。
     *
     * 要求前后都是分隔符,而不是裸子串包含:库名、参数值里也可能出现这几个字 ({@code
     * jdbc:...?host=localhost-proxy}),误判的表现同样是服务起不来,比漏判更难解释。
     */
    private static final Pattern LOOPBACK =
            Pattern.compile("(^|//|@)(localhost|127\\.0\\.0\\.1|\\[::1\\]|0\\.0\\.0\\.0)($|[:/?])");

    /** 是否指向回环地址 —— 裸主机名与连接串两种形态都要认。 */
    private static boolean isLoopback(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        return LOOPBACK.matcher(v).find();
    }

    /** {@code spring.profiles.active} 是否含 {@code prod} / {@code production}(大小写不敏感)。 */
    private boolean isProductionProfile() {
        if (!StringUtils.hasText(activeProfiles)) {
            return false;
        }
        for (String p : activeProfiles.split(",")) {
            String t = p.trim().toLowerCase(Locale.ROOT);
            if ("prod".equals(t) || "production".equals(t)) {
                return true;
            }
        }
        return false;
    }
}
