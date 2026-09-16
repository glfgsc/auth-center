package com.auth.center.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SpEL 权限判断 bean，供 {@code @PreAuthorize("@authPerm.hasCapability('...')")} 等表达式使用。两种判定粒度，按端点
 * 有没有专属能力码来选：
 *
 *   - {@link #hasCapability(String)} —— 细粒度。端点有对应能力码时用它，管理员在权限集里勾掉该能力即真的失去访问。
 *   - {@link #isAdmin()} —— 粗粒度，只问「是不是 admin 权限集」。留给尚无专属能力码的管理端点。
 *
 * {@code isAdmin()} 不蕴含任何能力：两者是独立判据，没有「admin 自动通过能力校验」的旁路。admin 权限集之所以
 * 处处能过，是因为它本身持有全部能力码，而不是因为它叫 admin —— 这正是让权限集里的开关真正生效的前提。能力码来自 JWT 快照，见 {@link
 * JwtAuthenticationFilter}。
 */
@Component("authPerm")
public class AuthPerm {

    /** admin 权限集对应的角色 authority。 */
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    /**
     * 当前用户是否持有 ADMIN 权限集。
     *
     * @return 持有返回 true
     */
    public boolean isAdmin() {
        return hasAuthority(ROLE_ADMIN);
    }

    /**
     * 当前用户是否持有指定能力码。
     *
     * @param capability 能力码，如 {@code admin:manage_user}
     * @return 持有返回 true
     */
    public boolean hasCapability(String capability) {
        return capability != null && hasAuthority(capability);
    }

    /**
     * 当前用户是否持有其中任意一个能力码。
     *
     * @param capabilities 能力码列表
     * @return 命中任意一个返回 true
     */
    public boolean hasAnyCapability(String... capabilities) {
        if (capabilities == null) {
            return false;
        }
        for (String capability : capabilities) {
            if (hasCapability(capability)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前认证主体是否带指定 authority。
     *
     * @param authority authority 字符串
     * @return 带则返回 true
     */
    private static boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
