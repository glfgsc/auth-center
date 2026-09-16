package com.auth.center.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * {@link CasServerController#buildUpstreamSsoRedirect} 单测 —— 服务端 302 委派上游 CAS 的跳转构造必须与 {@code
 * cas-login.html} 跳板页的 {@code redirectToSso()} 逐字段同构.
 *
 * 覆盖:正常构造(登录端点拼接/回调包裹/双层编码)、service 空回退对外基地址、上游地址尾斜杠归一、两道回环防御(上游指向自身登录页按对外基地址与请求 origin
 * 双基准判定、service 自引用 external-callback)、防御命中返回 null(调用方回退渲染登录页)、产品编码原样挂进回调(验票阶段据此取回同一档 SSO 配置).
 */
class CasServerControllerUpstreamRedirectTest {

    private static final String UPSTREAM = "https://cas.example.com/cas";
    private static final String PUBLIC_ORIGIN = "https://bi.example.com";
    private static final String REQUEST_ORIGIN = "http://auth-center:8090";
    private static final String SERVICE = "https://bi.example.com/embed-sso/dashboard/5";

    /**
     * 正常构造:
     * loginEndpoint?service=encode(publicOrigin/cas/external-callback?originalService=encode(service)).
     */
    @Test
    void buildsUpstreamRedirectWithDoubleEncodedCallback() {
        String out =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, null);

        String expectedCallback =
                PUBLIC_ORIGIN
                        + "/cas/external-callback?originalService="
                        + URLEncoder.encode(SERVICE, StandardCharsets.UTF_8);
        String expected =
                UPSTREAM
                        + "/login?service="
                        + URLEncoder.encode(expectedCallback, StandardCharsets.UTF_8);
        assertEquals(expected, out);
    }

    /** 上游地址尾斜杠归一 —— 与跳板页 replace(/\/+$/,'') 同构. */
    @Test
    void normalizesTrailingSlashesOnUpstreamUrl() {
        String out =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM + "///", SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, null);
        String expectedPrefix = UPSTREAM + "/login?service=";
        assertEquals(expectedPrefix, out.substring(0, expectedPrefix.length()));
    }

    /** service 为空 → originalService 回退对外基地址(与跳板页 serviceVal || origin 同构). */
    @Test
    void fallsBackToPublicOriginWhenServiceMissing() {
        String out =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, "  ", PUBLIC_ORIGIN, REQUEST_ORIGIN, null);
        String expectedCallback =
                PUBLIC_ORIGIN
                        + "/cas/external-callback?originalService="
                        + URLEncoder.encode(PUBLIC_ORIGIN, StandardCharsets.UTF_8);
        assertEquals(
                UPSTREAM
                        + "/login?service="
                        + URLEncoder.encode(expectedCallback, StandardCharsets.UTF_8),
                out);
    }

    /**
     * 带产品编码时挂在 originalService 之后 —— external-callback 阶段 {@code rebuildSelfCallbackUrl} 按原始 query
     * 重建 service，参数顺序与编码必须逐字符一致，否则上游 CAS 验票会因 service 不匹配而失败。
     */
    @Test
    void appendsSystemAfterOriginalServiceSoCallbackRoundTrips() {
        String out =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, "bi");

        String expectedCallback =
                PUBLIC_ORIGIN
                        + "/cas/external-callback?originalService="
                        + URLEncoder.encode(SERVICE, StandardCharsets.UTF_8)
                        + "&system=bi";
        assertEquals(
                UPSTREAM
                        + "/login?service="
                        + URLEncoder.encode(expectedCallback, StandardCharsets.UTF_8),
                out);
    }

    /** 产品编码为空白时不挂 —— 与不传产品完全等价，保证未接入产品维度的调用方行为不变. */
    @Test
    void omitsSystemParamWhenBlank() {
        String withBlank =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, "  ");
        String withNull =
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, null);
        assertEquals(withNull, withBlank);
    }

    /** 回环防御 1a: 上游登录端点 == 对外基地址自身登录页 → null(拒绝委派,回退渲染). */
    @Test
    void loopGuardRejectsUpstreamPointingAtSelfByPublicOrigin() {
        assertNull(
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        PUBLIC_ORIGIN + "/cas", SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, null));
    }

    /** 回环防御 1b: 上游登录端点 == 请求实际 origin 自身登录页 → null. */
    @Test
    void loopGuardRejectsUpstreamPointingAtSelfByRequestOrigin() {
        assertNull(
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        REQUEST_ORIGIN + "/cas", SERVICE, PUBLIC_ORIGIN, REQUEST_ORIGIN, null));
    }

    /** 回环防御 2: service 已是本中心 external-callback → null(拒绝继续包裹). */
    @Test
    void loopGuardRejectsSelfReferencingService() {
        String looping = PUBLIC_ORIGIN + "/cas/external-callback?originalService=https%3A%2F%2Fx";
        assertNull(
                CasRedirectGuard.buildUpstreamSsoRedirect(
                        UPSTREAM, looping, PUBLIC_ORIGIN, REQUEST_ORIGIN, null));
    }
}
