package com.auth.center.controller;

import com.auth.center.entity.SsoConfig;
import com.auth.center.service.ISsoConfigService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录 / 登出跳转地址的安全判定与拼装。
 *
 * 开放重定向是 CAS 最典型的一处洞:service 参数由调用方给,不校验就能把用户带着票据送到任意站点。这里只放行「配置过的 SSO 服务端」与本站自身来源,其余一律拒。
 * 判定集中在一处,是为了让登录、登出、外部回调三条路用同一把尺。
 */
@Component
public class CasRedirectGuard {

    private static final Logger log = LoggerFactory.getLogger(CasRedirectGuard.class);

    /** configJson 中 CAS 协议版本的匹配正则 —— 管理台「协议版本」下拉写入,取值 "2.0" / "3.0"。 */
    private static final Pattern PROTOCOL_VERSION_PATTERN =
            Pattern.compile("\"protocolVersion\"\\s*:\\s*\"([^\"]+)\"");

    /** configJson 中单点登出(SLO)开关的匹配正则 */
    private static final Pattern SLO_ENABLED_PATTERN =
            Pattern.compile("\"singleLogout\"\\s*:\\s*true");

    /** CAS 2.0 验票端点。 */
    private static final String VALIDATE_PATH_V2 = "/serviceValidate";

    /** CAS 3.0 验票端点 —— 与 2.0 的差别是会回属性(cas:attributes)。 */
    private static final String VALIDATE_PATH_V3 = "/p3/serviceValidate";

    /**
     * 本系统对外访问基地址（协议 + 主机，如 {@code https://skp-internal-dev.bbtv.cn}）.
     *
     * 用于构造回传给上游 CAS 的回调 service，保证登录与验票两阶段的 service 逐字符一致。留空则回退到根据请求转发头自动推断，见 {@link
     * #resolvePublicBaseUrl}。
     */
    @Value("${auth.cas.public-base-url:}")
    private String publicBaseUrl;

    private final ISsoConfigService ssoConfigService;

    public CasRedirectGuard(ISsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    /**
     * 判断 SSO 扩展配置 JSON 是否开启了单点登出（SLO）.
     *
     * @param configJson SSO 配置扩展 JSON 字符串，可能为 {@code null}
     * @return {@code true} 表示开启了 SLO
     */
    public boolean isSingleLogoutEnabled(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return false;
        }
        return SLO_ENABLED_PATTERN.matcher(configJson).find();
    }

    /**
     * 按 configJson 里的协议版本选验票端点.
     *
     * 管理台「协议版本」下拉写的是 {@code protocolVersion}，取值 {@code "2.0"} / {@code "3.0"}。 3.0 走 {@code
     * /p3/serviceValidate}（回属性），2.0 走 {@code /serviceValidate}。
     *
     * 读不到就按 2.0：{@code /serviceValidate} 是 CAS 协议的通用端点，所有 CAS 服务端都实现；而 {@code /p3/} 段只有 3.0
     * 以上才有，猜 3.0 会让老服务端直接 404。默认取窄的那个。
     *
     * @param configJson SSO 配置扩展 JSON 字符串，可能为 {@code null}
     * @return 验票端点路径
     */
    public String resolveValidatePath(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return VALIDATE_PATH_V2;
        }
        Matcher m = PROTOCOL_VERSION_PATTERN.matcher(configJson);
        if (!m.find()) {
            return VALIDATE_PATH_V2;
        }
        return m.group(1).startsWith("3") ? VALIDATE_PATH_V3 : VALIDATE_PATH_V2;
    }

    /**
     * 校验登出后跳转的 {@code service} 是否可信 -- 防开放重定向.
     *
     * 仅允许跳转到本系统对外基地址，或已配置的上游 CAS 服务器地址下的 URL。
     *
     * @param service 待跳转的目标 URL
     * @param request 当前请求（用于推断对外基地址）
     * @return {@code true} 表示可信，允许 302 跳转
     */
    public boolean isSafeLogoutRedirect(String service, HttpServletRequest request) {
        String base = resolvePublicBaseUrl(request);
        if (base != null && !base.isBlank() && urlMatchesOrigin(service, base)) {
            return true;
        }
        return matchesAnyConfiguredSsoServer(service);
    }

    /**
     * 校验 service URL 是否在允许列表中 -- 防开放重定向.
     *
     * 仅允许指向本系统对外基地址或已配置的上游 CAS 服务器地址下的 URL。与 {@link #isSafeLogoutRedirect(String,
     * HttpServletRequest)} 逻辑一致，适用于 /cas/login 和 /cas/external-callback 路径的
     * service/originalService 校验。
     *
     * @param serviceUrl 待校验的 service URL
     * @param request 当前请求（用于推断对外基地址）
     * @return {@code true} 表示 service URL 可信
     */
    public boolean isSafeServiceUrl(String serviceUrl, HttpServletRequest request) {
        if (serviceUrl == null || serviceUrl.isBlank()) {
            return false;
        }
        String base = resolvePublicBaseUrl(request);
        if (base != null && !base.isBlank() && urlMatchesOrigin(serviceUrl, base)) {
            return true;
        }
        return matchesAnyConfiguredSsoServer(serviceUrl);
    }

    /**
     * 判断 URL 是否落在任一已配置的上游 CAS 地址下 —— 重定向白名单的第二条判据。
     *
     * 按产品拆分配置后上游地址不止一个，这里取并集而非只看某一档:白名单回答的是「这个地址我们信不信」，
     * 每一条都是管理员在登录设置里逐字填进去的可信上游，不该因为当前请求解析到的是另一档就被判为不可信 —— 那会让配了自己 SSO 的产品登出/回调时被拒。
     *
     * @param url 待校验 URL
     * @return {@code true} 表示命中某一档已配置的上游地址
     */
    public boolean matchesAnyConfiguredSsoServer(String url) {
        for (SsoConfig config : ssoConfigService.listConfigs()) {
            String serverUrl = config.getServerUrl();
            if (serverUrl != null
                    && !serverUrl.isBlank()
                    && urlMatchesOrigin(url, serverUrl.replaceAll("/+$", ""))) {
                return true;
            }
        }
        return false;
    }

    public static boolean urlMatchesOrigin(String url, String origin) {
        if (!url.startsWith(origin)) {
            return false;
        }
        if (url.length() == origin.length()) {
            return true;
        }
        char next = url.charAt(origin.length());
        return next == '/' || next == '?' || next == '#';
    }

    /**
     * 重建本次外部回调请求的对外绝对 URL（去除上游 CAS 追加的 ticket 参数）.
     *
     * CAS 协议要求验票时的 {@code service} 与签发 ST 时的 {@code service} 完全相等。登录阶段由浏览器用 {@code
     * <对外基地址>/cas/external-callback?originalService=...} 构造并提交给上游 CAS；因此这里用同一个对外基地址 + 本次请求 path +
     * 浏览器回传的原始 query（仅剔除 ticket）重建出逐字符一致的回调地址。
     *
     * @param request 当前外部回调 HTTP 请求
     * @return 对外绝对回调 URL，例如 {@code
     *     https://skp-internal-dev.bbtv.cn/cas/external-callback?originalService=...}
     */
    public String rebuildSelfCallbackUrl(HttpServletRequest request) {
        String base = resolvePublicBaseUrl(request) + request.getRequestURI();
        String serviceQuery = stripTicketParam(request.getQueryString());
        return serviceQuery.isEmpty() ? base : base + "?" + serviceQuery;
    }

    /**
     * 解析本系统对外访问基地址（协议 + 主机，无尾部斜杠）.
     *
     * 优先使用显式配置 {@code auth.cas.public-base-url}；未配置时回退到从实际连接推断（{@code request.getScheme()} +
     * {@code request.getServerName()}）。
     *
     * 安全说明:绝不信任 {@code X-Forwarded-Host}/{@code X-Forwarded-Proto} 等转发头 ——
     * 攻击者可注入这些头绕过 {@link #isSafeServiceUrl} 校验，造成开放重定向并泄露 CAS Service Ticket。生产部署应显式配置 {@code
     * auth.cas.public-base-url}。
     *
     * @param request 当前 HTTP 请求
     * @return 对外基地址，例如 {@code https://skp-internal-dev.bbtv.cn}
     */
    public String resolvePublicBaseUrl(HttpServletRequest request) {
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            return publicBaseUrl.replaceAll("/+$", "");
        }
        // Fallback: derive from actual connection (safe, not from forwarded headers)
        return requestOrigin(request);
    }

    /**
     * 当前请求实际连接的 origin（协议 + 主机 [+ 非默认端口]）—— 不读转发头，不可伪造.
     *
     * @param request HTTP 请求
     * @return 形如 {@code https://host[:port]} 的 origin
     */
    public static String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            return scheme + "://" + host;
        }
        return scheme + "://" + host + ":" + port;
    }

    /**
     * 构造「委派上游 CAS 登录」的 302 目标 —— 与 {@code cas-login.html} 跳板页的 {@code redirectToSso()} 逐字段同构
     * （登录端点拼接、回调包裹、双层 URL 编码、两道回环防御）,保证服务端 302 与页面 JS 跳转产生完全一致的上游请求（进而 external-callback 验票阶段重建的
     * service 与登录阶段逐字符一致）.
     *
     * @param ssoServerUrl 上游 CAS 服务器基地址（如 {@code https://cas.example.com/cas}）
     * @param service 业务系统回调 URL（可空 → 回退对外基地址）
     * @param publicOrigin 本中心对外基地址（{@code resolvePublicBaseUrl} 结果）
     * @param requestOrigin 本次请求实际连接 origin（回环防御的第二比较基准）
     * @param system 发起登录的产品编码（可空）—— 原样挂在回调 URL 上，使 external-callback 阶段能取回同一档 SSO 配置去验票。{@code
     *     rebuildSelfCallbackUrl} 按原始 query 重建，故它必须进 service 串。
     * @return 上游 CAS 登录 URL；命中回环防御时返回 {@code null}（调用方回退渲染登录页报错）
     */
    public static String buildUpstreamSsoRedirect(
            String ssoServerUrl,
            String service,
            String publicOrigin,
            String requestOrigin,
            String system) {
        String loginEndpoint = ssoServerUrl.replaceAll("/+$", "") + "/login";
        // 回环防御 1：上游 CAS 登录端点解析到本认证中心自身的登录页 —— 委派会原地打转
        // （service 每跳多包一层 URL 编码，最终撑爆请求行返回 400）。
        if (loginEndpoint.equals(publicOrigin + "/cas/login")
                || loginEndpoint.equals(requestOrigin + "/cas/login")) {
            return null;
        }
        // 回环防御 2：传入的 service 已是本中心的 external-callback 回调，说明正处于
        // 重定向回环中，拒绝继续包裹。
        if (service != null && service.contains("/cas/external-callback")) {
            return null;
        }
        String target = service != null && !service.isBlank() ? service : publicOrigin;
        String callbackUrl =
                publicOrigin
                        + "/cas/external-callback?originalService="
                        + URLEncoder.encode(target, StandardCharsets.UTF_8);
        if (system != null && !system.isBlank()) {
            callbackUrl += "&system=" + URLEncoder.encode(system, StandardCharsets.UTF_8);
        }
        return loginEndpoint + "?service=" + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);
    }

    /**
     * 从原始 query 串中剔除 {@code ticket} 参数，保留其余参数的原始编码.
     *
     * @param queryString 原始 query 串（未解码），可能为 {@code null}
     * @return 剔除 ticket 后的 query 串，可能为空字符串
     */
    public String stripTicketParam(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return Arrays.stream(queryString.split("&"))
                .filter(param -> !param.startsWith("ticket="))
                .collect(Collectors.joining("&"));
    }

    /**
     * 构建重定向 URL -- 将 ticket 参数附加到 service URL.
     *
     * @param serviceUrl 原始服务 URL
     * @param ticketId Service Ticket ID
     * @return 带 ticket 参数的完整 URL
     */
    public String buildRedirectUrl(String serviceUrl, String ticketId) {
        String separator = serviceUrl.contains("?") ? "&" : "?";
        return serviceUrl + separator + "ticket=" + ticketId;
    }

    @PostConstruct
    public void warnIfPublicBaseUrlNotConfigured() {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            log.warn(
                    "auth.cas.public-base-url is not configured. "
                            + "CAS redirect validation will use request.getServerName() which is safe "
                            + "but may not match the external-facing URL behind a reverse proxy. "
                            + "Set AUTH_PUBLIC_BASE_URL for production deployments.");
        }
    }
}
