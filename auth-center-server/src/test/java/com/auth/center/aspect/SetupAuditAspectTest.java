package com.auth.center.aspect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.common.Result;
import com.auth.center.entity.AuditLog;
import com.auth.center.service.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link SetupAuditAspect} 单测 —— 验证审计捕获、脱敏、分类与失败记录(写入统一 {@link AuditLog})。
 *
 * 纯 Mockito + Mock 请求/安全上下文,不起 Spring / DB。覆盖:用户创建取模块 / 操作 / 操作人并脱敏密码、 GET
 * 读取不记、非管理端点不记、连接应用删除的分类与 targetId、抛异常时记 500,来源恒为 auth_center。
 */
class SetupAuditAspectTest {

    private final IAuditLogService service = mock(IAuditLogService.class);
    private final SetupAuditAspect aspect = new SetupAuditAspect(service, new ObjectMapper());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private void mockRequest(String method, String uri) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr("10.0.0.5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    private void mockAdmin(String username, long userId) {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        auth.setDetails(Map.of("userId", userId));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private ProceedingJoinPoint mockJp(
            String methodName, String[] paramNames, Object[] args, Object result) throws Throwable {
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getName()).thenReturn(methodName);
        when(sig.getParameterNames()).thenReturn(paramNames);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getArgs()).thenReturn(args);
        when(jp.proceed()).thenReturn(result);
        return jp;
    }

    @Test
    void auditsUserCreateAndRedactsPassword() throws Throwable {
        mockRequest("POST", "/api/auth/admin/users");
        mockAdmin("root", 1L);
        Map<String, Object> body = Map.of("username", "alice", "password", "s3cr3t-pw");
        ProceedingJoinPoint jp =
                mockJp("create", new String[] {"req"}, new Object[] {body}, Result.ok(42L));

        aspect.around(jp);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).record(cap.capture());
        AuditLog a = cap.getValue();
        assertEquals("auth_center", a.getSourceSystem());
        assertEquals("user", a.getModule());
        assertEquals("create", a.getOperationType());
        assertEquals("root", a.getActorUsername());
        assertEquals(1L, a.getActorUserId());
        assertEquals(200, a.getStatus());
        assertNotNull(a.getParams());
        assertFalse(a.getParams().contains("s3cr3t-pw"), "明文密码不得进入审计 params");
        assertTrue(a.getParams().contains("***"), "密码应被脱敏为 ***");
    }

    @Test
    void skipsGetReads() throws Throwable {
        mockRequest("GET", "/api/auth/admin/users");
        mockAdmin("root", 1L);
        ProceedingJoinPoint jp =
                mockJp("list", new String[] {}, new Object[] {}, Result.ok(List.of()));

        aspect.around(jp);

        verify(service, never()).record(any());
    }

    @Test
    void skipsNonAdminPaths() throws Throwable {
        mockRequest("POST", "/api/auth/login");
        ProceedingJoinPoint jp =
                mockJp("login", new String[] {}, new Object[] {}, Result.ok("token"));

        aspect.around(jp);

        verify(service, never()).record(any());
    }

    @Test
    void skipsCasConnectivityTest() throws Throwable {
        mockRequest("POST", "/api/auth/sso/admin/cas/test");
        mockAdmin("root", 1L);
        ProceedingJoinPoint jp =
                mockJp("testCas", new String[] {"req"}, new Object[] {Map.of()}, Result.ok());

        aspect.around(jp);

        verify(service, never()).record(any());
    }

    @Test
    void classifiesConnectedAppDeleteWithTargetId() throws Throwable {
        mockRequest("DELETE", "/api/auth/admin/connected-apps/7");
        mockAdmin("root", 1L);
        ProceedingJoinPoint jp =
                mockJp("delete", new String[] {"id"}, new Object[] {7L}, Result.ok());

        aspect.around(jp);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).record(cap.capture());
        assertEquals("connected_app", cap.getValue().getModule());
        assertEquals("delete", cap.getValue().getOperationType());
        assertEquals("7", cap.getValue().getTargetId());
    }

    @Test
    void classifiesSecretRotation() throws Throwable {
        mockRequest("POST", "/api/auth/admin/connected-apps/7/secrets");
        mockAdmin("root", 1L);
        ProceedingJoinPoint jp =
                mockJp("generateSecret", new String[] {"id"}, new Object[] {7L}, Result.ok());

        aspect.around(jp);

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).record(cap.capture());
        assertEquals("connected_app", cap.getValue().getModule());
        assertEquals("generate_secret", cap.getValue().getOperationType());
    }

    @Test
    void recordsFailureStatusOnException() throws Throwable {
        mockRequest("PUT", "/api/auth/admin/permission-sets/3");
        mockAdmin("root", 1L);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getName()).thenReturn("update");
        when(sig.getParameterNames()).thenReturn(new String[] {"id", "req"});
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getArgs()).thenReturn(new Object[] {3L, Map.of("name", "x")});
        when(jp.proceed()).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> aspect.around(jp));

        ArgumentCaptor<AuditLog> cap = ArgumentCaptor.forClass(AuditLog.class);
        verify(service).record(cap.capture());
        assertEquals(500, cap.getValue().getStatus());
        assertEquals("boom", cap.getValue().getErrorMsg());
        assertEquals("permission_set", cap.getValue().getModule());
        assertEquals("update", cap.getValue().getOperationType());
    }
}
