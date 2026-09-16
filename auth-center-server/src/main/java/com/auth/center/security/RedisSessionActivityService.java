package com.auth.center.security;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的会话跨系统活跃度 —— 多实例共享,支撑会话列表展示跨系统活跃.
 *
 * Redis Key: {@code auth:session-activity:{sessionId}} = Hash(系统码 → 最近使用时间 ms)。逐字段 {@code HSET}
 * 更新,天然规避多系统并发上报的读改写覆盖。每次上报滚动刷新 TTL(默认 7 天,即 refresh 族上限), 会话吊销时 {@link #remove} 主动清理。仅当 {@code
 * spring.data.redis.host} 存在时装配并 {@code @Primary} 覆盖内存实现。
 *
 * 注意 Key 前缀刻意用 {@code auth:session-activity:} 而非 {@code auth:session:activity:} —— 后者会落进会话注册表
 * {@link RedisSessionRegistryService} 的扫描 glob {@code auth:session:*},其按字符串读取本 Hash 会抛 {@code
 * WRONGTYPE} 拖垮整个会话列表。故活跃度另起命名空间,与会话注册表隔离。
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedisSessionActivityService implements ISessionActivityService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionActivityService.class);

    /** 会话活跃度 Redis Key 前缀 —— 刻意不落 {@code auth:session:} 命名空间,避免与会话注册表扫描 glob 撞车。 */
    private static final String ACTIVITY_KEY_PREFIX = "auth:session-activity:";

    private final StringRedisTemplate redisTemplate;

    /** 活跃度 Hash 的滚动 TTL(ms),默认 7 天与 refresh 族上限一致。 */
    private final long activityTtlMs;

    /**
     * 构造注入。
     *
     * @param redisTemplate Redis 模板
     * @param activityTtlMs 活跃度 TTL(ms)
     */
    public RedisSessionActivityService(
            StringRedisTemplate redisTemplate,
            @Value("${auth.session.activity-ttl-ms:604800000}") long activityTtlMs) {
        this.redisTemplate = redisTemplate;
        this.activityTtlMs = activityTtlMs;
        log.info("已启用 Redis 会话跨系统活跃度");
    }

    /** {@inheritDoc} */
    @Override
    public void touch(String sessionId, String system, long lastSeenMs) {
        if (sessionId == null || sessionId.isBlank() || system == null || system.isBlank()) {
            return;
        }
        try {
            String key = ACTIVITY_KEY_PREFIX + sessionId;
            redisTemplate.opsForHash().put(key, system, String.valueOf(lastSeenMs));
            redisTemplate.expire(key, activityTtlMs, TimeUnit.MILLISECONDS);
            // 同步产品成员集:管理台按产品分页靠它与有序索引取交集,不写这一笔就查不到本会话。
            // bi-gateway 直写活跃度时也写同一个集合(见 SessionKeys)。
            redisTemplate.opsForSet().add(SessionKeys.productKey(system), sessionId);
        } catch (Exception e) {
            log.warn(
                    "[SessionActivity] 上报失败, sessionId={}, system={}: {}",
                    sessionId,
                    system,
                    e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Long> read(String sessionId) {
        Map<String, Long> result = new HashMap<>();
        if (sessionId == null || sessionId.isBlank()) {
            return result;
        }
        try {
            Map<Object, Object> raw =
                    redisTemplate.opsForHash().entries(ACTIVITY_KEY_PREFIX + sessionId);
            for (Map.Entry<Object, Object> e : raw.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    result.put(String.valueOf(e.getKey()), parseMs(String.valueOf(e.getValue())));
                }
            }
        } catch (Exception e) {
            log.warn("[SessionActivity] 读取失败, sessionId={}: {}", sessionId, e.getMessage());
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public void remove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        String key = ACTIVITY_KEY_PREFIX + sessionId;
        // 必须先读出这条会话活跃过哪些产品,再删哈希 —— 顺序反了就无从知道该从哪几个成员集里摘,
        // 集合里会永远留着一个已吊销的会话 ID。
        try {
            Set<Object> systems = redisTemplate.opsForHash().keys(key);
            if (systems != null) {
                for (Object system : systems) {
                    redisTemplate
                            .opsForSet()
                            .remove(SessionKeys.productKey(String.valueOf(system)), sessionId);
                }
            }
        } catch (Exception e) {
            log.warn("[SessionActivity] 清理产品成员集失败, sessionId={}: {}", sessionId, e.getMessage());
        }
        redisTemplate.delete(key);
    }

    /**
     * 解析 epoch ms 字符串,失败返回 0。
     *
     * @param s 数值字符串
     * @return epoch ms;失败 0
     */
    private long parseMs(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
