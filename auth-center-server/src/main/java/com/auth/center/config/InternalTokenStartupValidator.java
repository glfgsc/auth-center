package com.auth.center.config;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 内部服务令牌的生产环境 fail-fast 校验(auth-center 侧,与 bi 各服务的 {@code InternalTokenStartupValidator} 对齐)。
 *
 * {@code auth.internal-service-token}(环境变量 {@code BI_INTERNAL_SERVICE_TOKEN})是服务间调用的预共享密钥。
 * auth-center 没有 {@code InternalServiceAuthFilter} 之类的过滤器,其 {@code /internal/**} 与 {@code
 * /api/auth/capabilities/register} 端点唯一的门就是各端点方法体里对本令牌的常量时间比对。而 {@code k8s/20-ingress.yaml} 把
 * {@code /api/auth} 前缀直连 auth-center、不经网关,故这些端点公网可达 —— 令牌强度就是它们唯一的实质防线。
 *
 * 配置内置开发默认值 {@code bi-internal-dev-token-NOT-FOR-PROD-...}(见 {@code application.yml}):生产若漏配
 * {@code BI_INTERNAL_SERVICE_TOKEN},服务会以这个众所周知的令牌启动,任何人凭它即可调用内部端点(如 {@code
 * EmbedInternalController} 为任意 subject 铸造带身份的 embed JWT = 用户冒充)。因此:
 *
 *   - 生产 profile({@code spring.profiles.active} 含 {@code prod} / {@code production})下,令牌仍含 {@code
 *       NOT-FOR-PROD} 开发标记 → fail-fast 拒绝启动。
 *   - 部署占位符({@code <CHANGE_ME>} 等)在任何环境都非法 → 直接拒绝启动。
 *   - 令牌为空属「内部认证关闭」(fail-safe:各端点自校验会拒绝所有内部调用,不放行)→ 仅记 WARN。
 *
 * {@code @Lazy(false)}:auth-center 若开启全局 {@code spring.main.lazy-initialization},无注入方的 {@code
 * InitializingBean} 会被跳过而永不校验;显式声明非惰性,保证启动期一定执行。
 */
@Component
@Lazy(false)
public class InternalTokenStartupValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(InternalTokenStartupValidator.class);

    /** 开发默认密钥标记 —— 含此子串即判定为未更换的开发令牌。 */
    private static final String DEV_SECRET_MARKER = "NOT-FOR-PROD";

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Override
    public void afterPropertiesSet() {
        validate();
    }

    /** 校验内部服务令牌强度:占位符任意环境拒;开发默认值生产环境拒;为空仅告警。 */
    void validate() {
        if (!StringUtils.hasText(internalServiceToken)) {
            log.warn(
                    "[SECURITY] auth.internal-service-token (env BI_INTERNAL_SERVICE_TOKEN) 未配置 —— "
                            + "auth-center 的内部端点(embed 铸票 / 审计汇入 / 平台配置 / 能力注册)将拒绝所有调用。"
                            + "生产环境必须设置与各 bi 服务一致的强随机令牌。");
            return;
        }
        if (isPlaceholder(internalServiceToken)) {
            throw new IllegalStateException(
                    "[SECURITY] auth.internal-service-token 仍为部署占位符(k8s Secret bi-app-secrets 的 "
                            + "BI_INTERNAL_SERVICE_TOKEN <CHANGE_ME> 未替换),拒绝启动 —— 请注入强随机令牌。");
        }
        if (internalServiceToken.contains(DEV_SECRET_MARKER)) {
            if (isProductionProfile()) {
                throw new IllegalStateException(
                        "[SECURITY] 生产 profile 下 auth.internal-service-token 仍为开发默认值(含 "
                                + DEV_SECRET_MARKER
                                + "),拒绝启动 —— 该令牌众所周知,而 auth-center 内部端点经 ingress 公网可达,凭它即可"
                                + "冒充可信内部服务铸造带身份的 embed JWT。请通过 BI_INTERNAL_SERVICE_TOKEN 设置强随机令牌。");
            }
            log.error(
                    "[SECURITY] auth.internal-service-token 仍为开发默认值(含 {})!"
                            + "生产必须通过 BI_INTERNAL_SERVICE_TOKEN 设置强随机令牌。",
                    DEV_SECRET_MARKER);
        }
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

    /** 令牌是否为未替换的部署占位符(如 {@code <CHANGE_ME>})—— 任何环境都非法。 */
    private static boolean isPlaceholder(String v) {
        String t = v.trim();
        return t.startsWith("<") || t.contains("CHANGE_ME");
    }
}
