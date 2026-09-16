package com.auth.center.security;

/**
 * Token 黑名单服务接口 —— JWT 吊销机制的抽象契约。
 *
 * 配置了 {@code spring.data.redis.host} 时用 {@link RedisTokenBlacklistService}(多实例生产环境), 否则降级为内存实现
 * {@link TokenBlacklistService}(单实例开发环境)。
 */
public interface ITokenBlacklistService {

    /**
     * 将指定 JTI 的 Token 加入黑名单.
     *
     * @param jti JWT ID (Token 唯一标识)
     * @param expiryMs Token 的过期时间戳 (毫秒, epoch time)
     */
    void revoke(String jti, long expiryMs);

    /**
     * 检查指定 JTI 的 Token 是否已被吊销.
     *
     * @param jti JWT ID (Token 唯一标识)
     * @return {@code true} 表示已吊销, {@code false} 表示未吊销
     */
    boolean isRevoked(String jti);

    /**
     * 吊销整条会话 —— 该会话签发过的全部 access token 一律失效,不限于当前那一枚。
     *
     * 按 JTI 拉黑挡不住已续期会话的旧令牌(注册表只记当前 JTI),故另按 {@code sid} 立一道闸;网关每请求读 {@link
     * SessionKeys#revokedKey} 判定。原因码随标记一同存下,供前端把「被顶下线」与「登出 / 过期」分开说。
     *
     * @param sessionId 会话 ID(refresh 族 ID,即 access token 的 {@code sid} claim)
     * @param reason 吊销原因码(见 {@link SessionRevokeReasons})
     * @param expiryMs 会话过期时间戳(毫秒, epoch time),作为标记 TTL 上界
     */
    void revokeSession(String sessionId, String reason, long expiryMs);

    /**
     * 取会话的吊销原因码。
     *
     * @param sessionId 会话 ID
     * @return 原因码;未被吊销返回 {@code null}
     */
    String sessionRevokeReason(String sessionId);
}
