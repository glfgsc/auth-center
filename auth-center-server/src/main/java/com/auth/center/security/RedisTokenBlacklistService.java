package com.auth.center.security;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的 Token 黑名单服务 -- 替代内存实现，支持多实例共享.
 *
 * 使用 Redis String 存储已吊销的 JWT ID (JTI)， TTL 设为 Token 的剩余有效时间，过期后 Redis 自动清理。
 *
 * Redis Key 规则: {@code auth:blacklist:{jti}}, Value = "1"
 *
 * 仅当 {@code spring.data.redis.host} 配置存在时才装配此实现，并通过 {@code @Primary} 覆盖 InMemory 实现。
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedisTokenBlacklistService implements ITokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);

    /** 黑名单 Redis Key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

    /** 用户级令牌撤销 Redis Key 前缀 */
    private static final String USER_REVOKED_KEY_PREFIX = "token:user_revoked:";

    /** 黑名单标记值 */
    private static final String BLACKLIST_VALUE = "1";

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造函数，注入 Redis 模板.
     *
     * @param redisTemplate StringRedisTemplate 实例
     */
    public RedisTokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.info("已启用 Redis Token 黑名单服务");
    }

    /**
     * 将指定 JTI 的 Token 加入黑名单.
     *
     * TTL 设为 Token 的剩余过期时间。若 Token 已过期（剩余时间 <= 0），仍写入但立即过期（Redis 自动删除）。
     *
     * @param jti JWT ID (Token 唯一标识)
     * @param expiryMs Token 的过期时间戳 (毫秒, epoch time)
     */
    public void revoke(String jti, long expiryMs) {
        long remainingMs = expiryMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            log.info("Token 已自然过期，无需加入黑名单: jti={}", jti);
            return;
        }
        String key = BLACKLIST_KEY_PREFIX + jti;
        redisTemplate.opsForValue().set(key, BLACKLIST_VALUE, remainingMs, TimeUnit.MILLISECONDS);
        log.info("Token 已加入 Redis 黑名单: jti={}, remainingMs={}", jti, remainingMs);
    }

    /**
     * 检查指定 JTI 的 Token 是否已被吊销.
     *
     * @param jti JWT ID (Token 唯一标识)
     * @return {@code true} 表示已吊销, {@code false} 表示未吊销
     */
    public boolean isRevoked(String jti) {
        String key = BLACKLIST_KEY_PREFIX + jti;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** {@inheritDoc} */
    public void revokeSession(String sessionId, String reason, long expiryMs) {
        long remainingMs = expiryMs - System.currentTimeMillis();
        if (remainingMs <= 0) {
            return;
        }
        redisTemplate
                .opsForValue()
                .set(SessionKeys.revokedKey(sessionId), reason, remainingMs, TimeUnit.MILLISECONDS);
        log.info("会话已吊销: sessionId={}, reason={}, remainingMs={}", sessionId, reason, remainingMs);
    }

    /** {@inheritDoc} */
    public String sessionRevokeReason(String sessionId) {
        return redisTemplate.opsForValue().get(SessionKeys.revokedKey(sessionId));
    }

    /**
     * 标记用户级别令牌撤销 — 该用户在此时刻之前签发的所有令牌均视为无效.
     *
     * 在 Redis 中写入 {@code token:user_revoked:{userId}} = 当前时间戳（epoch seconds）， TTL 设为略大于 token
     * 最大有效期，过期后 Redis 自动清理。
     *
     * @param userId 被撤销用户 ID
     * @param ttl 撤销标记存活时间（应 >= token 最大有效期）
     */
    public void revokeAllTokensForUser(Long userId, Duration ttl) {
        String key = USER_REVOKED_KEY_PREFIX + userId;
        String revokedAtEpochSeconds = String.valueOf(Instant.now().getEpochSecond());
        redisTemplate
                .opsForValue()
                .set(key, revokedAtEpochSeconds, ttl.toMillis(), TimeUnit.MILLISECONDS);
        log.info(
                "用户级令牌撤销: userId={}, revokedAt={}, ttl={}h",
                userId,
                revokedAtEpochSeconds,
                ttl.toHours());
    }

    /**
     * 检查用户是否被全局撤销 — token 签发时间早于撤销时间则视为无效.
     *
     * @param userId 用户 ID
     * @param issuedAt token 签发时间（epoch seconds）
     * @return {@code true} 表示该 token 已被撤销
     */
    public boolean isUserRevoked(Long userId, long issuedAt) {
        String key = USER_REVOKED_KEY_PREFIX + userId;
        String val = redisTemplate.opsForValue().get(key);
        if (val == null) {
            return false;
        }
        long revokedAt = Long.parseLong(val);
        return issuedAt <= revokedAt;
    }
}
