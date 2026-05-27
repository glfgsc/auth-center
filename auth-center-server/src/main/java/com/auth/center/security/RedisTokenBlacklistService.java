package com.auth.center.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 Token 黑名单服务 -- 替代内存实现，支持多实例共享.
 *
 * <p>使用 Redis String 存储已吊销的 JWT ID (JTI)，
 * TTL 设为 Token 的剩余有效时间，过期后 Redis 自动清理。</p>
 *
 * <p>Redis Key 规则: {@code auth:blacklist:{jti}}, Value = "1"</p>
 *
 * <p>仅当 {@code spring.data.redis.host} 配置存在时才装配此实现，
 * 并通过 {@code @Primary} 覆盖 InMemory 实现。</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedisTokenBlacklistService implements ITokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(RedisTokenBlacklistService.class);

    /** 黑名单 Redis Key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "auth:blacklist:";

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
     * <p>TTL 设为 Token 的剩余过期时间。若 Token 已过期（剩余时间 <= 0），
     * 仍写入但立即过期（Redis 自动删除）。</p>
     *
     * @param jti      JWT ID (Token 唯一标识)
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
}
