package com.auth.center.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token 黑名单服务 -- 基于内存的 JWT 吊销机制.
 *
 * <p>通过 JTI (JWT ID) 跟踪已注销的 Token. 每条记录保留到 Token 原始过期时间,
 * 之后由定时任务自动清除 (过期的 Token 无需继续黑名单, 因为它们已自然失效).
 *
 * <p>当 Redis 实现可用时（{@code RedisTokenBlacklistService}），此 Bean 不会被创建。</p>
 */
@Component
@ConditionalOnMissingBean(RedisTokenBlacklistService.class)
public class TokenBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(TokenBlacklistService.class);

    /** 定时清理间隔 (毫秒): 10 分钟 */
    private static final long CLEANUP_INTERVAL_MS = 10L * 60 * 1000;

    /**
     * 黑名单存储: jti -> Token 过期时间戳 (毫秒).
     * <p>使用过期时间戳判断何时可以安全移除条目.
     */
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    /**
     * 将指定 JTI 的 Token 加入黑名单.
     *
     * @param jti      JWT ID (Token 唯一标识)
     * @param expiryMs Token 的过期时间戳 (毫秒, epoch time)
     */
    public void revoke(String jti, long expiryMs) {
        blacklist.put(jti, expiryMs);
        log.info("Token 已加入黑名单: jti={}", jti);
    }

    /**
     * 检查指定 JTI 的 Token 是否已被吊销.
     *
     * @param jti JWT ID (Token 唯一标识)
     * @return {@code true} 表示已吊销, {@code false} 表示未吊销
     */
    public boolean isRevoked(String jti) {
        return blacklist.containsKey(jti);
    }

    /**
     * 定时清理已过期的黑名单条目.
     *
     * <p>Token 过期后自然失效, 无需继续保留在黑名单中, 避免内存无限增长.
     * 每 {@value #CLEANUP_INTERVAL_MS} 毫秒执行一次.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = blacklist.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (entry.getValue() < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Token 黑名单清理了 {} 条过期条目, 当前剩余 {} 条", removed, blacklist.size());
        }
    }
}
