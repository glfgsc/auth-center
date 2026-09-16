package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.auth.center.service.IPlatformConfigService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link SessionPolicy} 单测 —— 并发会话上限的读取与回落.
 *
 * 全部回落路径都归到默认 1(而非「不限」):这是个安全开关,配置缺失 / 写错 / 库读不到时松开限制,恰恰是最不该发生的那种失效方向。
 */
class SessionPolicyTest {

    private IPlatformConfigService configService;
    private SessionPolicy policy;

    @BeforeEach
    void setUp() {
        configService = mock(IPlatformConfigService.class);
        policy = new SessionPolicy(configService);
    }

    /**
     * 让配置服务返回给定的上限原值。
     *
     * @param raw 配置值;{@code null} 表示该键不存在
     */
    private void given(String raw) {
        when(configService.getValues(anyString()))
                .thenReturn(raw == null ? Map.of() : Map.of(SessionPolicy.KEY_MAX_PER_USER, raw));
    }

    @Test
    void configuredValueWins() {
        given("3");
        assertEquals(3, policy.maxSessionsPerUser());
    }

    @Test
    void zeroMeansUnlimited() {
        given("0");
        assertEquals(0, policy.maxSessionsPerUser());
    }

    @Test
    void missingKeyFallsBackToSingleSession() {
        given(null);
        assertEquals(1, policy.maxSessionsPerUser());
    }

    @Test
    void blankValueFallsBackToSingleSession() {
        given("  ");
        assertEquals(1, policy.maxSessionsPerUser());
    }

    @Test
    void nonNumericValueFallsBackToSingleSession() {
        given("yes");
        assertEquals(1, policy.maxSessionsPerUser());
    }

    @Test
    void configReadFailureFallsBackToSingleSession() {
        when(configService.getValues(anyString())).thenThrow(new RuntimeException("db down"));
        assertEquals(1, policy.maxSessionsPerUser());
    }
}
