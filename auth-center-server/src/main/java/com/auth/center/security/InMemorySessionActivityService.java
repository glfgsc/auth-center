package com.auth.center.security;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 基于内存的会话跨系统活跃度 —— 单实例开发环境默认实现.
 *
 * {@link RedisSessionActivityService} 在配置了 Redis 时以 {@code @Primary} 覆盖本实现。内存实现不跨实例共享,仅供本地开发。
 */
@Component
public class InMemorySessionActivityService implements ISessionActivityService {

    /** sessionId → (系统码 → 最近使用时间 ms)。 */
    private final Map<String, Map<String, Long>> activity = new ConcurrentHashMap<>();

    /** {@inheritDoc} */
    @Override
    public void touch(String sessionId, String system, long lastSeenMs) {
        if (sessionId == null || sessionId.isBlank() || system == null || system.isBlank()) {
            return;
        }
        activity.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(system, lastSeenMs);
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, Long> read(String sessionId) {
        Map<String, Long> systems = activity.get(sessionId);
        return systems == null ? new HashMap<>() : new HashMap<>(systems);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(String sessionId) {
        activity.remove(sessionId);
    }
}
