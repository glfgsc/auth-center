package com.auth.center.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 会话吊销动作 —— 让一条会话即刻死透的四件事,收在一处.
 *
 * 四件缺一不可:①按 {@code sid} 吊销整条会话(网关每请求判,覆盖该会话签发过的全部令牌)②拉黑注册表记着的当前 access JTI(网关有一条内存快路径按 JTI
 * 判,不写则同副本的后续请求要多绕一次 Redis)③作废 refresh 族(断掉续期,否则被踢设备转头就能换一枚新令牌)④摘掉注册表与活跃度条目(管理台列表别再显示已死的会话)。
 *
 * 调用方三个:管理员单踢、管理员按用户踢、登录时按并发上限顶下线。各写各的,漏掉哪一件就是哪一件的缺口 —— 早先只做②③④,被踢设备手上若攥着续期前的旧令牌,在其自然寿命内照样通行。
 */
@Component
public class SessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

    private final ISessionRegistryService sessionRegistry;
    private final ISessionActivityService sessionActivityService;
    private final ITokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;

    /**
     * 构造注入。
     *
     * @param sessionRegistry 活跃会话注册表
     * @param sessionActivityService 会话跨系统活跃度服务
     * @param tokenBlacklistService 令牌黑名单服务
     * @param refreshTokenService refresh 族管理服务
     * @param jwtService JWT 服务(取 access token 有效期作吊销标记的兜底 TTL)
     */
    public SessionRevoker(
            ISessionRegistryService sessionRegistry,
            ISessionActivityService sessionActivityService,
            ITokenBlacklistService tokenBlacklistService,
            RefreshTokenService refreshTokenService,
            JwtService jwtService) {
        this.sessionRegistry = sessionRegistry;
        this.sessionActivityService = sessionActivityService;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
    }

    /**
     * 吊销一条会话(幂等)。
     *
     * @param sessionId 会话 ID(refresh 族 ID)
     * @param reason 吊销原因码(见 {@link SessionRevokeReasons})
     */
    public void revoke(String sessionId, String reason) {
        revoke(sessionRegistry.get(sessionId), sessionId, reason);
    }

    /**
     * 吊销一条已读出的会话(幂等)—— 调用方已有 {@link SessionInfo} 时走这个,省一次读。
     *
     * @param session 会话;{@code null} 表示本体已不在(仍执行作废族与摘索引)
     * @param sessionId 会话 ID
     * @param reason 吊销原因码
     */
    public void revoke(SessionInfo session, String sessionId, String reason) {
        // 本体已不在时不知道这条会话还剩多久,按 access token 有效期兜底即可:任何还能被出示的令牌都在这个窗口内自然过期。
        long expiresAt =
                session != null
                        ? session.getExpiresAt()
                        : System.currentTimeMillis() + jwtService.getAccessTokenExpiration();
        tokenBlacklistService.revokeSession(sessionId, reason, expiresAt);
        if (session != null && session.getAccessJti() != null) {
            tokenBlacklistService.revoke(session.getAccessJti(), session.getAccessExpiresAt());
        }
        refreshTokenService.invalidateFamily(sessionId);
        sessionRegistry.remove(sessionId);
        sessionActivityService.remove(sessionId);
    }

    /**
     * 按并发上限顶掉该用户的多余会话 —— 保留本次会话与最近活跃的若干条,其余吊销。
     *
     * 在新会话登记「之后」调用并显式排除它:登记之前算,并发两次登录会各自看不见对方,两条都活下来。
     *
     * @param userId 用户 ID
     * @param keepSessionId 本次登录的会话 ID(必留)
     * @param maxPerUser 每用户并发会话上限(须为正;非正表示不限,调用方不应进来)
     * @param reason 吊销原因码
     * @return 被顶掉的会话数
     */
    public int enforceLimit(Long userId, String keepSessionId, int maxPerUser, String reason) {
        if (userId == null || maxPerUser <= 0) {
            return 0;
        }
        List<SessionInfo> all = sessionRegistry.listByUser(userId);
        SessionInfo kept =
                all.stream()
                        .filter(s -> s.getSessionId().equals(keepSessionId))
                        .findFirst()
                        .orElse(null);
        if (kept == null) {
            // 本次会话不在注册表里(登记失败,或已被另一次更晚的登录顶掉)—— 没有定序基准,此时顶掉别人只会误伤:
            // 把「现在」当作本次登录时刻,会让一条已经出局的会话反过来把活着的新会话踢下去。
            return 0;
        }
        long keptLoginAt = kept.getLoginAt();
        List<SessionInfo> others =
                all.stream()
                        .filter(s -> !s.getSessionId().equals(keepSessionId))
                        // 只顶比本次登录更早的。两台设备同时登录时,双方都把对方看作「多余的那条」,互相顶掉的结果是谁都没登上;
                        // 按登录时刻定序后,晚的那次顶掉早的、早的那次不动晚的,最新登录必然活下来。
                        .filter(s -> s.getLoginAt() < keptLoginAt)
                        .toList();
        // listByUser 已按最近活跃倒序,故「留前 maxPerUser - 1 条」即留最近活跃的那几条。
        List<SessionInfo> doomed = others.stream().skip(Math.max(maxPerUser - 1, 0)).toList();
        for (SessionInfo s : doomed) {
            revoke(s, s.getSessionId(), reason);
        }
        if (!doomed.isEmpty()) {
            log.info(
                    "[Session] 并发上限顶下线: userId={}, 上限={}, 顶掉={} 条, reason={}",
                    userId,
                    maxPerUser,
                    doomed.size(),
                    reason);
        }
        return doomed.size();
    }
}
