package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code ISessionRegistryService#listPage} 的契约单测(跑在内存实现上).
 *
 * 钉的是接口语义,不是某一种存储:按最近活跃倒序、用户名精确忽略大小写、产品判据为「在该产品有活跃记录」、总数是该过滤条件下的命中数而非全表数。Redis
 * 实现用有序索引与成员集达成同一份语义,其索引维护另需真 Redis 验证。
 */
class SessionRegistryPagingTest {

    private InMemorySessionActivityService activity;
    private InMemorySessionRegistryService registry;

    @BeforeEach
    void setUp() {
        activity = new InMemorySessionActivityService();
        registry = new InMemorySessionRegistryService(activity);
    }

    /**
     * 登记一条会话并给定其活跃产品。
     *
     * @param sessionId 会话 ID
     * @param username 用户名
     * @param lastActiveAt 最近活跃时间
     * @param systems 该会话活跃过的产品
     */
    private void add(String sessionId, String username, long lastActiveAt, String... systems) {
        SessionInfo s = new SessionInfo();
        s.setSessionId(sessionId);
        s.setUsername(username);
        s.setLastActiveAt(lastActiveAt);
        s.setExpiresAt(System.currentTimeMillis() + 86_400_000L);
        registry.record(s);
        for (String system : systems) {
            activity.touch(sessionId, system, lastActiveAt);
        }
    }

    /**
     * 取一页的会话 ID。
     *
     * @param page 页结果
     * @return 会话 ID 列表
     */
    private static List<String> ids(SessionPage page) {
        return page.records().stream().map(SessionInfo::getSessionId).toList();
    }

    /** 按最近活跃倒序,并按下标切页。 */
    @Test
    void ordersByLastActiveDescendingAndSlices() {
        add("s-old", "alice", 100L, "bi");
        add("s-mid", "alice", 200L, "bi");
        add("s-new", "alice", 300L, "bi");

        assertEquals(List.of("s-new", "s-mid"), ids(registry.listPage(null, null, 0, 2)));
        assertEquals(List.of("s-old"), ids(registry.listPage(null, null, 2, 2)));
    }

    /** 总数是命中总数,不随页大小变化。 */
    @Test
    void totalCountsMatchesNotPageSize() {
        add("s-1", "alice", 100L, "bi");
        add("s-2", "alice", 200L, "bi");
        add("s-3", "bob", 300L, "agent");

        assertEquals(3L, registry.listPage(null, null, 0, 1).total());
        assertEquals(2L, registry.listPage(null, "bi", 0, 1).total());
        assertEquals(2L, registry.listPage("alice", null, 0, 1).total());
    }

    /** 产品判据是「在该产品有活跃记录」;没有活跃记录的会话按该产品筛不出现。 */
    @Test
    void filtersByRecordedActivity() {
        add("s-bi", "alice", 200L, "bi");
        add("s-agent", "bob", 100L, "agent");
        add("s-idle", "carol", 300L);

        assertEquals(List.of("s-bi"), ids(registry.listPage(null, "bi", 0, 20)));
        assertTrue(ids(registry.listPage(null, "tracking", 0, 20)).isEmpty());
    }

    /** 用户名精确匹配且忽略大小写,可与产品条件叠加。 */
    @Test
    void filtersByUsernameIgnoringCase() {
        add("s-1", "Alice", 300L, "bi");
        add("s-2", "bob", 200L, "bi");

        assertEquals(List.of("s-1"), ids(registry.listPage("ALICE", null, 0, 20)));
        assertEquals(List.of("s-1"), ids(registry.listPage("alice", "bi", 0, 20)));
        assertTrue(ids(registry.listPage("alice", "agent", 0, 20)).isEmpty());
    }

    /** 偏移越界返回空页,但总数照给 —— 否则前端翻过头会以为一条都没有。 */
    @Test
    void offsetBeyondEndReturnsEmptyPageWithTotal() {
        add("s-1", "alice", 100L, "bi");

        SessionPage page = registry.listPage(null, null, 50, 20);

        assertTrue(page.records().isEmpty());
        assertEquals(1L, page.total());
    }

    /** 吊销后既不出现在列表里,也不再计入总数。 */
    @Test
    void removedSessionLeavesNoTrace() {
        add("s-1", "alice", 100L, "bi");
        add("s-2", "alice", 200L, "bi");

        registry.remove("s-1");
        activity.remove("s-1");

        SessionPage page = registry.listPage(null, "bi", 0, 20);
        assertEquals(List.of("s-2"), ids(page));
        assertEquals(1L, page.total());
    }
}
