package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@link AuthPerm} 的能力码判定单测.
 *
 * 钉住的核心语义:{@code isAdmin()} 与 {@code hasCapability()} 是独立判据 —— 名为 admin 的权限集不会自动通过
 * 能力校验。这正是「权限集里勾掉某能力，该能力对应的管理端点就真的进不去」的前提;若哪天有人给 admin 加了旁路，本测试会红。
 */
class AuthPermCapabilityTest {

    private final AuthPerm authPerm = new AuthPerm();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 用给定 authorities 建立认证上下文。 */
    private static void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "tester",
                                null,
                                List.of(authorities).stream()
                                        .map(SimpleGrantedAuthority::new)
                                        .toList()));
    }

    @Test
    void holdsCapabilityWhenAuthorityPresent() {
        authenticateWith("ROLE_ADMIN", "admin:manage_user");

        assertTrue(authPerm.hasCapability("admin:manage_user"));
        assertTrue(authPerm.isAdmin());
    }

    /** admin 身份不蕴含能力 —— 勾掉的能力必须真的失效，否则开关形同虚设。 */
    @Test
    void adminRoleAloneDoesNotGrantCapability() {
        authenticateWith("ROLE_ADMIN");

        assertTrue(authPerm.isAdmin());
        assertFalse(authPerm.hasCapability("admin:manage_permission_set"));
    }

    /** 反过来:持有能力的非 admin 权限集也应放行，这是细粒度授权的另一半。 */
    @Test
    void nonAdminWithCapabilityPasses() {
        authenticateWith("ROLE_PLATFORM_ANALYST", "admin:manage_idp");

        assertFalse(authPerm.isAdmin());
        assertTrue(authPerm.hasCapability("admin:manage_idp"));
    }

    @Test
    void anyCapabilityMatchesWhenOneHeld() {
        authenticateWith("admin:manage_idp");

        assertTrue(authPerm.hasAnyCapability("admin:manage_user", "admin:manage_idp"));
        assertFalse(authPerm.hasAnyCapability("admin:manage_user", "admin:manage_config"));
    }

    /** 未认证时一律拒绝，不得因空上下文放行。 */
    @Test
    void deniesWhenUnauthenticated() {
        assertFalse(authPerm.isAdmin());
        assertFalse(authPerm.hasCapability("admin:manage_user"));
        assertFalse(authPerm.hasAnyCapability("admin:manage_user"));
        assertFalse(authPerm.hasCapability(null));
    }
}
