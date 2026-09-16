package com.auth.center.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.auth.center.common.Result;
import com.auth.center.controller.request.CapabilityRegisterRequest;
import com.auth.center.service.ISystemCapabilityService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * {@code CapabilityController#register} 的内部服务令牌门控单测.
 *
 * 该端点在 {@code SecurityConfig} 里 {@code permitAll}（子系统启动期调用，此刻无用户上下文），且做的是 {@code
 * replaceForSystem} —— 先删后插的全量替换。缺内部令牌校验时，任意匿名调用方即可清空或改写某系统的能力码目录（能力码是权限模型的最小粒度单元）。
 *
 * {@code ALLOWED_SYSTEM_CODES} 白名单挡不住这件事：{@code systemCode} 是调用方自己填进请求体的值。
 */
class CapabilityRegisterTokenGateTest {

    private static final String TOKEN = "shared-internal-token";

    private ISystemCapabilityService service;
    private CapabilityController controller;

    @BeforeEach
    void setUp() {
        service = mock(ISystemCapabilityService.class);
        controller = new CapabilityController(service);
        ReflectionTestUtils.setField(controller, "internalServiceToken", TOKEN);
    }

    private static CapabilityRegisterRequest req() {
        CapabilityRegisterRequest r = new CapabilityRegisterRequest();
        r.setSystemCode("bi");
        CapabilityRegisterRequest.CapabilityItem item =
                new CapabilityRegisterRequest.CapabilityItem();
        item.setCode("dashboard:view");
        item.setCategory("dashboard");
        item.setLabel("查看仪表板");
        r.setCapabilities(List.of(item));
        return r;
    }

    /** 令牌正确 → 正常注册。 */
    @Test
    void validTokenRegisters() {
        when(service.replaceForSystem(anyString(), any())).thenReturn(1);

        Result<?> r = controller.register(TOKEN, req());

        assertEquals(HttpStatus.OK.value(), r.getCode());
        verify(service).replaceForSystem(anyString(), any());
    }

    /** 缺令牌 → 403，且绝不触碰目录（全量替换必须在门后）。 */
    @Test
    void missingTokenIsForbiddenAndDoesNotReplace() {
        Result<?> r = controller.register(null, req());

        assertEquals(HttpStatus.FORBIDDEN.value(), r.getCode());
        verify(service, never()).replaceForSystem(anyString(), any());
    }

    /** 令牌错误 → 403，目录不动。 */
    @Test
    void wrongTokenIsForbiddenAndDoesNotReplace() {
        Result<?> r = controller.register("not-the-token", req());

        assertEquals(HttpStatus.FORBIDDEN.value(), r.getCode());
        verify(service, never()).replaceForSystem(anyString(), any());
    }

    /** 服务端未配令牌 → 一律 403（fail-closed），不允许空配置退化成无门。 */
    @Test
    void unconfiguredServerTokenRejectsEverything() {
        ReflectionTestUtils.setField(controller, "internalServiceToken", "");

        assertEquals(HttpStatus.FORBIDDEN.value(), controller.register("", req()).getCode());
        assertEquals(HttpStatus.FORBIDDEN.value(), controller.register(TOKEN, req()).getCode());
        verify(service, never()).replaceForSystem(anyString(), any());
    }

    /** 令牌门在 systemCode 白名单之前：未持令牌者连「systemCode 不在允许列表」这条信息都拿不到。 */
    @Test
    void tokenGatePrecedesSystemCodeWhitelist() {
        CapabilityRegisterRequest bad = req();
        bad.setSystemCode("definitely-not-allowed");

        Result<?> r = controller.register(null, bad);

        assertEquals(HttpStatus.FORBIDDEN.value(), r.getCode());
        verify(service, never()).replaceForSystem(anyString(), any());
    }
}
