package com.auth.center.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.common.Result;
import com.auth.center.service.IGroupService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code GroupReadController#getGroupIdsByUserId} 的内部服务令牌门控单测.
 *
 * 该端点路径落 {@code /internal/} 段，在 {@code SecurityConfig} 里按服务间端点 {@code permitAll} —— 用户级认证不生效，
 * 唯一的门就是端点内自校验 {@code X-Internal-Service-Token}。缺这道门，任意匿名调用方即可用任意 {@code userId}
 * 枚举全站用户的组归属（组归属是内容权限引擎 Layer 3 的授权输入）。
 *
 * 口径与 {@code PlatformConfigInternalController} 一致：常量时间比对、fail-closed、无效即 403。
 */
class GroupReadInternalTokenGateTest {

    private static final String TOKEN = "shared-internal-token";

    private IGroupService groupService;
    private GroupReadController controller;

    @BeforeEach
    void setUp() {
        groupService = mock(IGroupService.class);
        controller = new GroupReadController(groupService);
        ReflectionTestUtils.setField(controller, "internalServiceToken", TOKEN);
    }

    /** 令牌正确 → 正常返回组 ID。 */
    @Test
    void validTokenReturnsGroupIds() {
        when(groupService.getGroupIdsByUserId(anyLong())).thenReturn(List.of(7L, 9L));

        Result<List<Long>> r = controller.getGroupIdsByUserId(TOKEN, 42L);

        assertEquals(List.of(7L, 9L), r.getData());
    }

    /** 缺令牌 → 403，且绝不查库（不因「先查后拒」把结果算出来）。 */
    @Test
    void missingTokenIsForbiddenAndDoesNotQuery() {
        Result<List<Long>> r = controller.getGroupIdsByUserId(null, 42L);

        assertEquals(HttpStatus.FORBIDDEN.value(), r.getCode());
        assertNull(r.getData());
        verify(groupService, never()).getGroupIdsByUserId(anyLong());
    }

    /** 令牌错误 → 403。 */
    @Test
    void wrongTokenIsForbidden() {
        Result<List<Long>> r = controller.getGroupIdsByUserId("not-the-token", 42L);

        assertEquals(HttpStatus.FORBIDDEN.value(), r.getCode());
        verify(groupService, never()).getGroupIdsByUserId(anyLong());
    }

    /** 服务端未配置令牌 → 一律 403（fail-closed）。否则空令牌配置会让「不带头」与「带空头」双双通过，等于门形同虚设。 */
    @Test
    void unconfiguredServerTokenRejectsEverything() {
        ReflectionTestUtils.setField(controller, "internalServiceToken", "");

        assertEquals(
                HttpStatus.FORBIDDEN.value(), controller.getGroupIdsByUserId("", 42L).getCode());
        assertEquals(
                HttpStatus.FORBIDDEN.value(), controller.getGroupIdsByUserId(TOKEN, 42L).getCode());
        verify(groupService, never()).getGroupIdsByUserId(anyLong());
    }

    /** 前缀相同但更短的令牌不得通过（常量时间比对同时也须比长度）。 */
    @Test
    void prefixOfTokenIsRejected() {
        Result<List<Long>> r = controller.getGroupIdsByUserId(TOKEN.substring(0, 5), 42L);

        assertTrue(r.getCode() == HttpStatus.FORBIDDEN.value(), "前缀不得通过");
    }
}
