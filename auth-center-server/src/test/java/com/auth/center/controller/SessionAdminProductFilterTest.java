package com.auth.center.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.security.ISessionActivityService;
import com.auth.center.security.ISessionRegistryService;
import com.auth.center.security.SessionInfo;
import com.auth.center.security.SessionPage;
import com.auth.center.security.SessionRevoker;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * {@code SessionAdminController#list} 的分页与活跃度合并单测.
 *
 * 过滤与排序已下沉到注册表(按索引取数,见 {@code ISessionRegistryService#listPage}),控制器只负责:把页码换算成偏移、
 * 夹住每页条数上限、只为本页合并跨系统活跃度。
 *
 * 最后一条是要点:按产品过滤若在控制器做,必须先把所有会话的活跃度读一遍才能判断谁该留下;产品维度由注册表侧的成员
 * 集完成后,控制器拿到手的已经只是本页,活跃度的读取量随之从「全表」降为「一页」。
 */
class SessionAdminProductFilterTest {

    private ISessionRegistryService sessionRegistry;
    private ISessionActivityService sessionActivityService;
    private SessionAdminController controller;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(ISessionRegistryService.class);
        sessionActivityService = mock(ISessionActivityService.class);
        controller =
                new SessionAdminController(
                        sessionRegistry, sessionActivityService, mock(SessionRevoker.class));
    }

    /**
     * 造一条会话。
     *
     * @param sessionId 会话 ID
     * @return 会话
     */
    private static SessionInfo session(String sessionId) {
        SessionInfo s = new SessionInfo();
        s.setSessionId(sessionId);
        s.setUsername("alice");
        return s;
    }

    /** 页码换算成偏移,过滤条件原样下传。 */
    @Test
    void translatesPageToOffset() {
        when(sessionRegistry.listPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(SessionPage.EMPTY);

        controller.list("alice", "bi", 3, 20);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        verify(sessionRegistry).listPage(eq("alice"), eq("bi"), offset.capture(), eq(20));
        assertEquals(40, offset.getValue());
    }

    /** 每页条数被夹在上限内 —— 否则 size 给个大数就把分页整个绕过去了。 */
    @Test
    void clampsPageSize() {
        when(sessionRegistry.listPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(SessionPage.EMPTY);

        controller.list(null, null, 1, 99999);

        verify(sessionRegistry).listPage(any(), any(), eq(0), eq(200));
    }

    /** 非法页码与条数被纠正为第 1 页、至少 1 条,而不是把负偏移传下去。 */
    @Test
    void normalizesNonPositivePaging() {
        when(sessionRegistry.listPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(SessionPage.EMPTY);

        controller.list(null, null, 0, 0);

        verify(sessionRegistry).listPage(any(), any(), eq(0), eq(1));
    }

    /** 活跃度只为本页的会话读取,不碰总数里的其余会话。 */
    @Test
    void mergesActivityForCurrentPageOnly() {
        when(sessionRegistry.listPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(new SessionPage(List.of(session("s-1"), session("s-2")), 500L));
        when(sessionActivityService.read("s-1")).thenReturn(Map.of("bi", 7L));
        when(sessionActivityService.read("s-2")).thenReturn(Map.of("agent", 9L));

        Map<String, Object> data = controller.list(null, "bi", 1, 20).getData();

        @SuppressWarnings("unchecked")
        List<SessionInfo> records = (List<SessionInfo>) data.get("records");
        assertEquals(Map.of("bi", 7L), records.get(0).getSystemActivity());
        assertEquals(Map.of("agent", 9L), records.get(1).getSystemActivity());
        verify(sessionActivityService).read("s-1");
        verify(sessionActivityService).read("s-2");
        verify(sessionActivityService, never()).read("s-3");
    }

    /** 响应结构与登录历史一致:records / total / page / size。 */
    @Test
    void returnsPagingEnvelope() {
        when(sessionRegistry.listPage(any(), any(), anyInt(), anyInt()))
                .thenReturn(new SessionPage(List.of(session("s-1")), 42L));

        Map<String, Object> data = controller.list(null, null, 2, 20).getData();

        assertEquals(42L, data.get("total"));
        assertEquals(2, data.get("page"));
        assertEquals(20, data.get("size"));
        assertTrue(data.containsKey("records"));
    }
}
