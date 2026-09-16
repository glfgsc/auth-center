package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@link JwtAuthenticationFilter} 把 JWT 里的能力快照落成 authorities 的单测.
 *
 * 该过滤器若只写 {@code ROLE_<权限集>}、能力码从不进上下文,auth-center 侧就只能做「是不是 admin」这种粗判，权限集里的 {@code admin:*}
 * 开关对它毫无作用。本测试钉住能力码确实落地，且是 {@code capabilities} 标量与 {@code systemPermissions}
 * 按系统结构的并集（同一能力可能只经某个子系统绑定授予）。
 */
class JwtAuthenticationFilterCapabilityTest {

    private JwtService jwtService;
    private ITokenBlacklistService blacklist;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        RsaKeyPairProvider keys = new RsaKeyPairProvider();
        ReflectionTestUtils.setField(
                keys, "keyDir", System.getProperty("java.io.tmpdir") + "/auth-center-test-keys");
        keys.init();

        jwtService = new JwtService(keys);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", 900_000L);

        blacklist = mock(ITokenBlacklistService.class);
        when(blacklist.isRevoked(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        filter = new JwtAuthenticationFilter(jwtService, blacklist);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** 跑一遍过滤器，返回落进上下文的 authority 集合。 */
    private Set<String> authoritiesFor(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(auth, "过滤器应已建立认证上下文");
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    void capabilitiesClaimBecomesAuthorities() throws Exception {
        String token =
                jwtService.generateAccessToken(
                        7L, "alice", "admin", "[\"admin:manage_user\",\"admin:manage_idp\"]", null);

        Set<String> authorities = authoritiesFor(token);

        assertTrue(authorities.contains("ROLE_ADMIN"));
        assertTrue(authorities.contains("admin:manage_user"));
        assertTrue(authorities.contains("admin:manage_idp"));
        assertFalse(authorities.contains("admin:manage_permission_set"));
    }

    /** 只经某个子系统绑定授予的能力也要落地 —— 标量 claim 只取 global/首个绑定，会漏掉它。 */
    @Test
    void systemPermissionsCapsAreUnionedIn() throws Exception {
        Map<String, Object> systemPermissions =
                Map.of(
                        "global", Map.of("ps", "admin", "caps", List.of("admin:manage_user")),
                        "bi", Map.of("ps", "platform_analyst", "caps", List.of("security:rls")));

        String token =
                jwtService.generateAccessToken(
                        7L, "alice", "admin", "[\"admin:manage_user\"]", systemPermissions);

        Set<String> authorities = authoritiesFor(token);

        assertTrue(authorities.contains("admin:manage_user"));
        assertTrue(authorities.contains("security:rls"), "子系统绑定的能力也应落进 authorities");
    }

    /** 能力为空时只剩角色 authority，不得因空值抛异常中断认证链。 */
    @Test
    void emptyCapabilitiesLeavesOnlyRole() throws Exception {
        String token = jwtService.generateAccessToken(7L, "bob", "viewer", "[]", null);

        Set<String> authorities = authoritiesFor(token);

        assertTrue(authorities.contains("ROLE_VIEWER"));
        assertFalse(authorities.stream().anyMatch(a -> a.contains(":")));
    }

    /**
     * 会话被吊销后,本过滤器也必须拦住 —— 不只是网关的事。
     *
     * {@code /api/auth/**} 在 ingress 里直连 auth-center、不经网关,只按 jti 判则被顶下线的设备凭续期前的旧令牌照样读得到用户信息。
     */
    @Test
    void revokedSessionIsNotAuthenticated() throws Exception {
        String token = jwtService.generateAccessToken(7L, "alice", "admin", "[]", null, "fam-1");
        when(blacklist.sessionRevokeReason("fam-1"))
                .thenReturn(SessionRevokeReasons.CONCURRENT_LOGIN);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), mock(FilterChain.class));

        assertNull(SecurityContextHolder.getContext().getAuthentication(), "被顶下线的会话不该建立认证上下文");
    }
}
