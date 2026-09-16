package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionRevoker} 单测 —— 并发会话上限与吊销四件事.
 *
 * 注册表与黑名单用真实的内存实现而非替身:吊销的正确性恰恰在「四件事都做了」,替身只会记下调用、不会告诉你会话是否真的从注册表里消失、原因码是否真的读得回来。
 */
class SessionRevokerTest {

    private static final long HOUR_MS = 3_600_000L;

    private InMemorySessionRegistryService registry;
    private InMemorySessionActivityService activity;
    private TokenBlacklistService blacklist;
    private RefreshTokenService refreshTokenService;
    private SessionRevoker revoker;

    @BeforeEach
    void setUp() {
        activity = new InMemorySessionActivityService();
        registry = new InMemorySessionRegistryService(activity);
        blacklist = new TokenBlacklistService();
        refreshTokenService = mock(RefreshTokenService.class);
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.getAccessTokenExpiration()).thenReturn(900_000L);
        revoker =
                new SessionRevoker(registry, activity, blacklist, refreshTokenService, jwtService);
    }

    /**
     * 造一条并登记会话。
     *
     * @param sessionId 会话 ID
     * @param userId 用户 ID
     * @param loginAt 登录时刻,同时用作最近活跃时间(epoch ms)
     * @return 已登记的会话
     */
    private SessionInfo record(String sessionId, long userId, long loginAt) {
        SessionInfo s = new SessionInfo();
        s.setSessionId(sessionId);
        s.setUserId(userId);
        s.setUsername("u" + userId);
        s.setAccessJti("jti-" + sessionId);
        s.setAccessExpiresAt(System.currentTimeMillis() + HOUR_MS);
        s.setLoginAt(loginAt);
        s.setLastActiveAt(loginAt);
        s.setExpiresAt(System.currentTimeMillis() + HOUR_MS);
        registry.record(s);
        activity.touch(sessionId, "bi", loginAt);
        return s;
    }

    @Test
    void enforceLimitOfOneKeepsOnlyTheNewSession() {
        long now = System.currentTimeMillis();
        record("old-1", 1L, now - 60_000);
        record("old-2", 1L, now - 30_000);
        record("new", 1L, now);

        int kicked = revoker.enforceLimit(1L, "new", 1, SessionRevokeReasons.CONCURRENT_LOGIN);

        assertEquals(2, kicked);
        assertEquals(
                List.of("new"),
                registry.listByUser(1L).stream().map(SessionInfo::getSessionId).toList());
    }

    @Test
    void revokedSessionCarriesReasonAndBlacklistsItsToken() {
        record("doomed", 1L, System.currentTimeMillis() - 1000);
        record("new", 1L, System.currentTimeMillis());

        revoker.enforceLimit(1L, "new", 1, SessionRevokeReasons.CONCURRENT_LOGIN);

        // ①会话级吊销带原因码(网关按 sid 判,覆盖该会话全部令牌)
        assertEquals(
                SessionRevokeReasons.CONCURRENT_LOGIN, blacklist.sessionRevokeReason("doomed"));
        // ②当前 access 令牌进黑名单 ③refresh 族作废 ④注册表与活跃度清干净
        assertTrue(blacklist.isRevoked("jti-doomed"));
        verify(refreshTokenService).invalidateFamily("doomed");
        assertNull(registry.get("doomed"));
        assertTrue(activity.read("doomed").isEmpty());
        // 活下来的那条一件都没被动
        assertNull(blacklist.sessionRevokeReason("new"));
        assertNotNull(registry.get("new"));
        verify(refreshTokenService, never()).invalidateFamily("new");
    }

    @Test
    void enforceLimitKeepsMostRecentlyActiveWithinLimit() {
        long now = System.currentTimeMillis();
        record("stale", 1L, now - 120_000);
        record("recent", 1L, now - 10_000);
        record("new", 1L, now);

        int kicked = revoker.enforceLimit(1L, "new", 2, SessionRevokeReasons.CONCURRENT_LOGIN);

        assertEquals(1, kicked);
        assertEquals(
                List.of("new", "recent"),
                registry.listByUser(1L).stream().map(SessionInfo::getSessionId).toList());
    }

    @Test
    void enforceLimitLeavesOtherUsersAlone() {
        long now = System.currentTimeMillis();
        record("other-user", 2L, now - 60_000);
        record("new", 1L, now);

        assertEquals(0, revoker.enforceLimit(1L, "new", 1, SessionRevokeReasons.CONCURRENT_LOGIN));
        assertNotNull(registry.get("other-user"));
    }

    @Test
    void nonPositiveLimitMeansUnlimited() {
        long now = System.currentTimeMillis();
        record("old", 1L, now - 60_000);
        record("new", 1L, now);

        assertEquals(0, revoker.enforceLimit(1L, "new", 0, SessionRevokeReasons.CONCURRENT_LOGIN));
        assertEquals(2, registry.listByUser(1L).size());
    }

    @Test
    void concurrentLoginsDoNotKickEachOther() {
        // 两台设备同时登录:各自都会跑一次上限。若不按登录时刻定序,双方互相顶掉,结果是谁都没登上。
        long now = System.currentTimeMillis();
        record("earlier", 1L, now - 5);
        record("later", 1L, now);

        int kickedByLater =
                revoker.enforceLimit(1L, "later", 1, SessionRevokeReasons.CONCURRENT_LOGIN);
        int kickedByEarlier =
                revoker.enforceLimit(1L, "earlier", 1, SessionRevokeReasons.CONCURRENT_LOGIN);

        assertEquals(1, kickedByLater);
        assertEquals(0, kickedByEarlier, "早的那次登录不该反过来顶掉更晚的登录");
        assertEquals(
                List.of("later"),
                registry.listByUser(1L).stream().map(SessionInfo::getSessionId).toList());
    }

    @Test
    void revokingAVanishedSessionStillBreaksItsFamilyAndMarksReason() {
        // 本体已不在(登出后又被管理员吊销之类)—— 仍要断续期并留下原因码,否则手上还攥着令牌的设备照常通行。
        revoker.revoke("gone", SessionRevokeReasons.ADMIN_REVOKED);

        verify(refreshTokenService).invalidateFamily("gone");
        assertEquals(SessionRevokeReasons.ADMIN_REVOKED, blacklist.sessionRevokeReason("gone"));
    }
}
