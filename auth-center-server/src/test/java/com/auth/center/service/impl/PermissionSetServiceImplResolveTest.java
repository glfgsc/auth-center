package com.auth.center.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.PermissionSetMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link PermissionSetServiceImpl#resolveForUser} 的契约 —— 用户的平台级主角色是 {@code global} 系统那一条.
 *
 * 权限集按系统绑定:同一用户可同时持 {@code global=admin} 与 {@code agent=agent_admin}。曾用无序 {@code LIMIT 1}
 * 随便取一条,把管理员误解析成 {@code agent_admin} → 数据面管理员旁路对真管理员失效、连自己的数据源都看不到。这里钉死:{@code resolveForUser} 只认
 * {@code global} 系统的集({@link PermissionSetMapper#selectGlobalByUserId}), 缺则回落默认 {@code viewer}。
 */
class PermissionSetServiceImplResolveTest {

    private PermissionSet ps(String code) {
        PermissionSet p = new PermissionSet();
        p.setCode(code);
        return p;
    }

    @Test
    void resolvesTheGlobalPermissionSet_notAnArbitrarySystemsOne() {
        PermissionSetMapper mapper = Mockito.mock(PermissionSetMapper.class);
        // 管理员的 global 集是 admin(agent 系统那条 agent_admin 不该被取到)
        when(mapper.selectGlobalByUserId(1L)).thenReturn(ps("admin"));
        PermissionSetServiceImpl svc = new PermissionSetServiceImpl(mapper);

        assertEquals("admin", svc.resolveForUser(1L).getCode());
        verify(mapper).selectGlobalByUserId(1L); // 必须走 global 专解析,不是「取任意一条」
    }

    @Test
    void fallsBackToViewerWhenUserHasNoGlobalPermissionSet() {
        PermissionSetMapper mapper = Mockito.mock(PermissionSetMapper.class);
        when(mapper.selectGlobalByUserId(9L)).thenReturn(null); // 只在 agent 等非 global 系统有角色
        when(mapper.selectByCode("viewer")).thenReturn(ps("viewer"));
        PermissionSetServiceImpl svc = new PermissionSetServiceImpl(mapper);

        assertEquals("viewer", svc.resolveForUser(9L).getCode());
    }

    @Test
    void returnsEmptyViewerWhenEvenTheDefaultSetIsMissing() {
        PermissionSetMapper mapper = Mockito.mock(PermissionSetMapper.class);
        when(mapper.selectGlobalByUserId(9L)).thenReturn(null);
        when(mapper.selectByCode("viewer")).thenReturn(null);
        PermissionSetServiceImpl svc = new PermissionSetServiceImpl(mapper);

        // 兜底:不抛 NPE,给一个空 viewer(登录/裁决据此按最小权限处理)
        assertEquals("viewer", svc.resolveForUser(9L).getCode());
    }
}
