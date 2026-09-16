package com.auth.center.controller;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 向上游 CAS 服务端校验票据 —— 出网代理装配、请求发起与回包截断,以及取调用方真实 IP。
 *
 * 回包截断只为日志:上游把 HTML 错误页当校验结果返回时,整页打进日志会淹掉排查线索。
 */
@Component
public class CasUpstreamValidator {

    /** 上游错误响应体记日志时的截断长度。 */
    private static final int ERROR_BODY_PREVIEW_CHARS = 500;

    /** HTTP 4xx 起始码 —— 之上走 errorStream 取响应体。 */
    private static final int HTTP_STATUS_BAD_REQUEST = 400;

    /** 外部 CAS 验票 HTTP 超时 (秒) */
    private static final int EXTERNAL_CAS_TIMEOUT_SECONDS = 10;

    /** 秒到毫秒换算系数 */
    private static final int MILLIS_PER_SECOND = 1000;

    /** 出网验票请求的 User-Agent —— 见 {@link #fetchCasValidation} 里为什么必须显式设置。 */
    private static final String VALIDATE_USER_AGENT = "LoomInsight-AuthCenter/1.0";

    /** 验票 SOCKS5 代理密码（可空）. */
    @Value("${auth.cas.validate-socks-password:}")
    private String validateSocksPassword;

    /**
     * 验票出站 SOCKS5 代理地址（{@code host:port}）.
     *
     * auth-center 部署在受限网络（如本地容器需经跳板进企业内网）时，验票为服务器对服务器调用，需经此 SOCKS5 代理。留空 = 直连（默认）。见 {@link
     * #buildValidateProxy}。
     */
    @Value("${auth.cas.validate-socks-proxy:}")
    private String validateSocksProxy;

    /** 验票 SOCKS5 代理用户名（匿名代理时留空）. */
    @Value("${auth.cas.validate-socks-username:}")
    private String validateSocksUsername;

    private static final Logger log = LoggerFactory.getLogger(CasUpstreamValidator.class);

    /**
     * 注册 SOCKS5 代理认证器 —— 仅当配置了验票代理 + 用户名时.
     *
     * Java 的 SOCKS5 用户名/密码认证（RFC 1929）通过默认 {@link Authenticator} 提供， {@code
     * java.net.socks.username/password} 系统属性方式并不可靠。此认证器仅对 SOCKS 协议的认证请求返回凭据，不影响 auth-center
     * 其他出站的认证行为。
     */
    @PostConstruct
    public void registerSocksAuthenticator() {
        if (validateSocksProxy == null
                || validateSocksProxy.isBlank()
                || validateSocksUsername == null
                || validateSocksUsername.isBlank()) {
            return;
        }
        final String proxyUser = validateSocksUsername;
        final char[] proxyPass =
                (validateSocksPassword != null ? validateSocksPassword : "").toCharArray();
        Authenticator.setDefault(
                new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        String protocol = getRequestingProtocol();
                        if (protocol != null && protocol.toUpperCase().contains("SOCKS")) {
                            return new PasswordAuthentication(proxyUser, proxyPass);
                        }
                        return null;
                    }
                });
        log.info("已注册 SOCKS5 验票代理认证器: proxy={}, user={}", validateSocksProxy, proxyUser);
    }

    /**
     * 拉取上游 CAS {@code serviceValidate} 响应 XML，支持经 SOCKS5 代理出站.
     *
     * auth-center 部署在受限网络（如本地容器经跳板进企业内网）时，验票为服务器对服务器调用，需经 {@code auth.cas.validate-socks-proxy}
     * 指定的 SOCKS5 代理：域名按本地 DNS 解析为目标 IP 后由代理建立隧道，TLS 在隧道内端到端完成（SNI 与证书校验仍针对上游 CAS 域名）。未配置代理时直连。
     *
     * @param validateUrl 完整的 serviceValidate URL
     * @return 上游 CAS 返回的 XML 文本
     * @throws IOException 连接或读取失败
     */
    public String fetchCasValidation(String validateUrl) throws IOException {
        URL url = URI.create(validateUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(buildValidateProxy());
        conn.setConnectTimeout(EXTERNAL_CAS_TIMEOUT_SECONDS * MILLIS_PER_SECOND);
        conn.setReadTimeout(EXTERNAL_CAS_TIMEOUT_SECONDS * MILLIS_PER_SECOND);
        conn.setRequestMethod("GET");
        // 报上名号：不设则 JDK 填 "Java/21.x"，不少 WAF 把这个 UA 直接当扫描器 403 掉
        // （实测现象：TCP/TLS 都通、CAS 侧加了服务白名单也没用，因为拦它的根本不是 CAS）。
        // 顺带让对方的访问日志、限流、告警里这股流量有主，出问题时两边对得上。
        conn.setRequestProperty("User-Agent", VALIDATE_USER_AGENT);
        conn.setRequestProperty("Accept", "application/xml, text/xml, */*");
        int status = conn.getResponseCode();
        try (InputStream is =
                status >= HTTP_STATUS_BAD_REQUEST ? conn.getErrorStream() : conn.getInputStream()) {
            String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (status >= HTTP_STATUS_BAD_REQUEST) {
                // 把对方的响应体带进异常。此前直接 getInputStream()，4xx/5xx 当场抛 IOException，
                // 只剩一句「Server returned HTTP response code: 403」——而拦截原因、请求 ID
                // 恰恰写在被丢掉的那段 body 里，排查时只能靠猜。
                throw new IOException(
                        "上游 CAS 返回 "
                                + status
                                + "；响应体: "
                                + abbreviate(body, ERROR_BODY_PREVIEW_CHARS));
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    /** 截断过长文本，避免把上游的整页错误 HTML 灌进日志。 */
    public String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() <= max ? oneLine : oneLine.substring(0, max) + "…(截断)";
    }

    /**
     * 构建验票出站代理 —— 配置了 {@code auth.cas.validate-socks-proxy} 则返回带可选认证的 SOCKS5 代理，否则直连.
     *
     * SOCKS5 认证凭据通过 {@code java.net.socks.username/password} 系统属性传递，仅被 SOCKS 连接消费，不影响
     * auth-center 对 DB / Redis / Nacos 的直连出站。
     *
     * @return SOCKS 代理实例；未配置或配置非法时返回 {@link Proxy#NO_PROXY}
     */
    public Proxy buildValidateProxy() {
        if (validateSocksProxy == null || validateSocksProxy.isBlank()) {
            return Proxy.NO_PROXY;
        }
        int sep = validateSocksProxy.lastIndexOf(':');
        if (sep <= 0 || sep == validateSocksProxy.length() - 1) {
            log.warn(
                    "auth.cas.validate-socks-proxy 格式应为 host:port，实际 [{}]，本次验票改为直连",
                    validateSocksProxy);
            return Proxy.NO_PROXY;
        }
        String host = validateSocksProxy.substring(0, sep).trim();
        int port;
        try {
            port = Integer.parseInt(validateSocksProxy.substring(sep + 1).trim());
        } catch (NumberFormatException e) {
            log.warn("auth.cas.validate-socks-proxy 端口非法 [{}]，本次验票改为直连", validateSocksProxy);
            return Proxy.NO_PROXY;
        }
        return new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
    }

    /**
     * 提取客户端真实 IP —— 优先 {@code X-Forwarded-For} 首段(网关/代理链最外层客户端),回退 {@code remoteAddr}.
     *
     * 与 {@code AuthController.clientIp} 同口径:CAS 登记活跃会话时 IP 须是终端用户真实地址而非网关地址。
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    public String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
