package com.auth.center.security;

import com.auth.center.service.IPlatformConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 并发会话策略 —— 每个用户同时能有几条活跃会话.
 *
 * 取自平台配置 {@code auth_center / security.session.maxPerUser},管理台「平台配置」页可改,改完下一次登录即生效。不加缓存:登录不是高频动作,一次按唯一索引的主键查换来「改了立刻算数」,
 * 比省这一次查询划算 —— 安全开关最怕的就是改完还要猜多久生效。
 *
 * 生产是单域名部署(洞察在 {@code /}、知数工坊在 {@code /agent}),两个产品同源共享 localStorage,一次登录两边都用同一枚令牌,故上限为 1
 * 不会让用户开两个产品就互相顶掉。
 */
@Component
public class SessionPolicy {

    private static final Logger log = LoggerFactory.getLogger(SessionPolicy.class);

    /** 承载本策略的平台配置所属系统。 */
    public static final String SYSTEM_CODE = "auth_center";

    /** 每用户并发会话上限的配置键。 */
    public static final String KEY_MAX_PER_USER = "security.session.maxPerUser";

    /** 配置缺失或不可解析时的上限 —— 与迁移种子同值,任一处漏了都不至于静默放开限制。 */
    private static final int DEFAULT_MAX_PER_USER = 1;

    private final IPlatformConfigService configService;

    /**
     * 构造注入。
     *
     * @param configService 平台配置服务
     */
    public SessionPolicy(IPlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 取每用户并发会话上限。
     *
     * @return 上限;{@code <= 0} 表示不限制并发会话
     */
    public int maxSessionsPerUser() {
        String raw;
        try {
            raw = configService.getValues(SYSTEM_CODE).get(KEY_MAX_PER_USER);
        } catch (Exception e) {
            log.warn("[Session] 读并发会话上限失败,按默认 {}: {}", DEFAULT_MAX_PER_USER, e.getMessage());
            return DEFAULT_MAX_PER_USER;
        }
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_PER_USER;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("[Session] 并发会话上限配置非整数 '{}',按默认 {}", raw, DEFAULT_MAX_PER_USER);
            return DEFAULT_MAX_PER_USER;
        }
    }
}
