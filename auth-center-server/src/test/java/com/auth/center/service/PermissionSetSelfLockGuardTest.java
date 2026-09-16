package com.auth.center.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.PermissionSetMapper;
import com.auth.center.service.impl.PermissionSetServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 「权限集管理」自锁守卫单测.
 *
 * {@code admin:manage_permission_set} 把守的正是权限集编辑器自身。最后一个持有它的权限集把它勾掉后，没有任何人还能打开编辑器、也就无法把它加回来 ——
 * 只能改库或跑迁移。守卫必须在写库前挡住这一步。
 */
class PermissionSetSelfLockGuardTest {

    private static final String CAP = "admin:manage_permission_set";

    private PermissionSetMapper mapper;
    private PermissionSetServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(PermissionSetMapper.class);
        service = new PermissionSetServiceImpl(mapper);
    }

    private static PermissionSet ps(Long id, String code, String capsJson) {
        PermissionSet p = new PermissionSet();
        p.setId(id);
        p.setCode(code);
        p.setSystemCode("global");
        p.setCapabilities(capsJson);
        return p;
    }

    /** 唯一持有者把该能力勾掉 —— 必须拒绝，且一行不写库。 */
    @Test
    void rejectsRemovingLastHolder() {
        when(mapper.selectList(any()))
                .thenReturn(List.of(ps(1L, "admin", "[\"" + CAP + "\"]"), ps(2L, "viewer", "[]")));

        PermissionSet stripped = ps(1L, "admin", "[\"admin:manage_user\"]");

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> service.save(stripped));
        assertTrue(e.getMessage().contains(CAP));
        verify(mapper, never()).updateById(any(PermissionSet.class));
    }

    /** 还有别的权限集持有 —— 属于可恢复操作，放行。 */
    @Test
    void allowsRemovalWhenAnotherSetStillHolds() {
        when(mapper.selectList(any()))
                .thenReturn(
                        List.of(
                                ps(1L, "admin", "[\"" + CAP + "\"]"),
                                ps(2L, "ops", "[\"" + CAP + "\"]")));

        assertDoesNotThrow(() -> service.save(ps(1L, "admin", "[\"admin:manage_user\"]")));
        verify(mapper).updateById(any(PermissionSet.class));
    }

    /** 本集仍持有该能力 —— 改别的能力不受守卫干扰。 */
    @Test
    void allowsEditWhenCapabilityKept() {
        assertDoesNotThrow(
                () -> service.save(ps(1L, "admin", "[\"" + CAP + "\",\"admin:manage_idp\"]")));
        verify(mapper).updateById(any(PermissionSet.class));
        verify(mapper, never()).selectList(any());
    }

    /** capabilities 为 null = 本次不改能力（MyBatis-Plus NOT_NULL 跳过该列），不该误拦。 */
    @Test
    void ignoresUpdateThatDoesNotTouchCapabilities() {
        assertDoesNotThrow(() -> service.save(ps(1L, "admin", null)));
        verify(mapper).updateById(any(PermissionSet.class));
        verify(mapper, never()).selectList(any());
    }

    /** 新建权限集不会减少持有者，不查全表。 */
    @Test
    void skipsGuardOnInsert() {
        assertDoesNotThrow(() -> service.save(ps(null, "newbie", "[]")));
        verify(mapper).insert(any(PermissionSet.class));
        verify(mapper, never()).selectList(any());
    }
}
