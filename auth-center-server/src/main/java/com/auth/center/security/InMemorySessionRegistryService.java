package com.auth.center.security;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 基于内存的活跃会话注册表 —— 单实例开发环境默认实现.
 *
 * {@link RedisSessionRegistryService} 在配置了 Redis 时以 {@code @Primary} 覆盖本实现。
 * 内存实现不跨实例共享,仅供本地开发;读取时惰性剔除已过期条目。
 *
 * 本实现不需要 Redis 那套索引:会话本就整份在堆里,过滤排序切片直接做即可 —— 索引解决的是「不把全表从 Redis 拉回本地」,这里没有那次拉取。产品维度取自
 * {@link InMemorySessionActivityService}, 与 Redis 实现读成员集是同一份语义。
 */
@Component
public class InMemorySessionRegistryService implements ISessionRegistryService {

    /** sessionId → 会话。 */
    private final Map<String, SessionInfo> sessions = new ConcurrentHashMap<>();

    private final InMemorySessionActivityService activityService;

    /**
     * 构造注入。
     *
     * 直接依赖内存活跃度实现而非接口:两者是成对的开发态兜底,按接口注入会拿到 {@code @Primary} 的 Redis 实现,
     * 那时本类根本不会被使用,却凭空多出一条跨实现的连线。
     *
     * @param activityService 内存活跃度服务
     */
    public InMemorySessionRegistryService(InMemorySessionActivityService activityService) {
        this.activityService = activityService;
    }

    /** {@inheritDoc} */
    @Override
    public void record(SessionInfo session) {
        if (session.getExpiresAt() > System.currentTimeMillis()) {
            sessions.put(session.getSessionId(), session);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void touch(String sessionId, String accessJti, long accessExpiresAt, long lastActiveAt) {
        SessionInfo session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        session.setAccessJti(accessJti);
        session.setAccessExpiresAt(accessExpiresAt);
        session.setLastActiveAt(lastActiveAt);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    /** {@inheritDoc} */
    @Override
    public List<SessionInfo> listAll() {
        long now = System.currentTimeMillis();
        List<SessionInfo> live = new ArrayList<>();
        sessions.values().removeIf(s -> s.getExpiresAt() <= now);
        live.addAll(sessions.values());
        return live;
    }

    /** {@inheritDoc} */
    @Override
    public List<SessionInfo> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return listAll().stream()
                .filter(s -> userId.equals(s.getUserId()))
                .sorted(Comparator.comparingLong(SessionInfo::getLastActiveAt).reversed())
                .toList();
    }

    /** {@inheritDoc} */
    @Override
    public SessionPage listPage(String username, String systemCode, int offset, int limit) {
        if (limit <= 0) {
            return SessionPage.EMPTY;
        }
        List<SessionInfo> matched =
                listAll().stream()
                        .filter(
                                s ->
                                        blank(username)
                                                || username.trim()
                                                        .equalsIgnoreCase(s.getUsername()))
                        .filter(
                                s ->
                                        blank(systemCode)
                                                || activityService
                                                        .read(s.getSessionId())
                                                        .containsKey(systemCode))
                        .sorted(Comparator.comparingLong(SessionInfo::getLastActiveAt).reversed())
                        .toList();
        if (offset >= matched.size()) {
            return new SessionPage(List.of(), matched.size());
        }
        return new SessionPage(
                List.copyOf(matched.subList(offset, Math.min(offset + limit, matched.size()))),
                matched.size());
    }

    /**
     * 过滤条件是否为空。
     *
     * @param value 条件值
     * @return 空或全空白为 {@code true}
     */
    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    /** {@inheritDoc} */
    @Override
    public SessionInfo get(String sessionId) {
        SessionInfo session = sessions.get(sessionId);
        if (session != null && session.getExpiresAt() <= System.currentTimeMillis()) {
            sessions.remove(sessionId);
            return null;
        }
        return session;
    }
}
