package com.auth.center.security;

/**
 * Token 黑名单服务接口 -- JWT 吊销机制的抽象契约.
 *
 * <p>提供两种实现:
 * <ul>
 *     <li>{@link TokenBlacklistService} — 基于内存的实现 (单实例开发环境)</li>
 *     <li>{@link RedisTokenBlacklistService} — 基于 Redis 的实现 (多实例生产环境)</li>
 * </ul>
 *
 * <p>选择逻辑: 当 {@code spring.data.redis.host} 配置存在时使用 Redis 实现，
 * 否则降级为内存实现。</p>
 */
public interface ITokenBlacklistService {

    /**
     * 将指定 JTI 的 Token 加入黑名单.
     *
     * @param jti      JWT ID (Token 唯一标识)
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
}
