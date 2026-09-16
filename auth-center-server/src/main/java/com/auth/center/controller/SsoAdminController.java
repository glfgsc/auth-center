package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.controller.request.SsoConfigSaveRequest;
import com.auth.center.controller.request.SsoConfigTestRequest;
import com.auth.center.entity.SsoConfig;
import com.auth.center.service.ISsoConfigService;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 管理控制器 -- 对齐前端 IdentityProvider 数据模型. 前端 PlatformSettingsPage 的 SSO tab 调用以下端点:
 *
 *   - {@code GET /api/auth/sso/admin/cas} — 读取 CAS 配置
 *   - {@code POST /api/auth/sso/admin/cas} — 创建/更新 CAS 配置
 *   - {@code DELETE /api/auth/sso/admin/cas} — 删除 CAS 配置
 *   - {@code POST /api/auth/sso/admin/cas/test} — 测试 CAS 服务器连通性
 *   - {@code GET /api/auth/sso/admin/callback-url} — 获取 CAS 回调地址
 *
 * 所有端点需认证（SecurityConfig 中 /api/auth/sso/admin/** → authenticated）。
 */
@RestController
@Validated
@RequestMapping("/api/auth/sso/admin")
public class SsoAdminController {

    private static final Logger log = LoggerFactory.getLogger(SsoAdminController.class);

    /** CAS 连接测试超时（秒） */
    private static final int TEST_TIMEOUT_SECONDS = 10;

    /** 默认 SSO 类型 */
    private static final String DEFAULT_TYPE = "CAS";

    /** 默认提供商 Slug */
    private static final String DEFAULT_SLUG = "cas";

    private final ISsoConfigService ssoConfigService;

    /** 当前服务基地址，用于构建回调 URL */
    @Value("${server.port:8090}")
    private int serverPort;

    /**
     * 构造函数.
     *
     * @param ssoConfigService SSO 配置服务
     */
    public SsoAdminController(ISsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    /**
     * 读取某产品自己的 CAS 配置 — 转为前端 IdentityProvider 格式.
     *
     * 不回落 {@code global} 兜底档:管理台要能区分「本产品单独配置过」与「沿用兜底档」，
     * 回落会让管理员把兜底档误认成本产品的配置，一保存就悄悄分叉出一份副本。
     *
     * @param system 产品编码，默认兜底档 {@code global}
     * @return CAS 配置，该产品未单独配置时 data=null
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @GetMapping("/cas")
    public Result<Map<String, Object>> getCas(
            @RequestParam(required = false, defaultValue = ISsoConfigService.GLOBAL_SYSTEM_CODE)
                    String system) {
        SsoConfig config = ssoConfigService.getConfig(system);
        if (config == null) {
            return Result.ok(null);
        }
        return Result.ok(toIdentityProvider(config));
    }

    /**
     * 列出已单独配置过 SSO 的产品编码 — 管理台据此标注哪些产品没在用兜底档.
     *
     * @return 产品编码列表
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @GetMapping("/cas/configured-systems")
    public Result<List<String>> configuredSystems() {
        return Result.ok(
                ssoConfigService.listConfigs().stream().map(SsoConfig::getSystemCode).toList());
    }

    /**
     * 创建或更新某产品的 CAS 配置 — 接收前端 IdentityProvider 格式.
     *
     * @param system 产品编码，默认兜底档 {@code global}
     * @param request SSO 配置保存请求
     * @return 保存后的配置
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @PostMapping("/cas")
    public Result<Map<String, Object>> saveCas(
            @RequestParam(required = false, defaultValue = ISsoConfigService.GLOBAL_SYSTEM_CODE)
                    String system,
            @Valid @RequestBody SsoConfigSaveRequest request) {
        SsoConfig config = ssoConfigService.getConfig(system);
        if (config == null) {
            config = new SsoConfig();
            config.setType(DEFAULT_TYPE);
            config.setSystemCode(system);
        }

        // 映射请求字段到实体
        if (request.getName() != null) {
            config.setDisplayName(request.getName());
        }
        if (request.getIcon() != null) {
            config.setIcon(request.getIcon());
        }
        if (request.getEnabled() != null) {
            config.setEnabled(request.getEnabled());
        }
        if (request.getLoginMode() != null) {
            config.setMode(request.getLoginMode());
        }
        if (request.getConfigJson() != null) {
            config.setConfigJson(request.getConfigJson());
            // 从 configJson 中提取 serverUrl 同步到顶层字段（向后兼容 public-config）
            extractServerUrl(config, request.getConfigJson());
        }

        SsoConfig saved = ssoConfigService.updateConfig(config);
        log.info(
                "SSO CAS 配置已保存: systemCode={}, mode={}, enabled={}",
                saved.getSystemCode(),
                saved.getMode(),
                saved.getEnabled());
        return Result.ok(toIdentityProvider(saved));
    }

    /**
     * 删除某产品的 CAS 配置 — 该产品回落到 {@code global} 兜底档；删的就是兜底档时回到纯本地登录.
     *
     * @param system 产品编码，默认兜底档 {@code global}
     * @return 删除确认消息
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @DeleteMapping("/cas")
    public Result<String> deleteCas(
            @RequestParam(required = false, defaultValue = ISsoConfigService.GLOBAL_SYSTEM_CODE)
                    String system) {
        ssoConfigService.deleteConfig(system);
        log.info("SSO CAS 配置已删除: systemCode={}", system);
        return Result.ok("deleted");
    }

    /**
     * 测试 CAS 服务器连通性 — 探测 /login 端点可达性.
     *
     * @param request SSO 配置测试请求
     * @return 测试结果 {ok, message, endpoints}
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @PostMapping("/cas/test")
    public Result<Map<String, Object>> testCas(@Valid @RequestBody SsoConfigTestRequest request) {
        String configJson = request.getConfigJson() != null ? request.getConfigJson() : "{}";
        String serverUrl = extractServerUrlFromJson(configJson);

        if (serverUrl == null || serverUrl.isBlank()) {
            return Result.ok(Map.of("ok", false, "message", "CAS 服务器地址为空"));
        }

        // 去除尾部斜杠
        serverUrl = serverUrl.replaceAll("/+$", "");

        // SSRF 防护：校验 CAS 服务器 URL 必须是公网可达的 HTTP(S) 地址
        String ssrfError = validatePublicUrl(serverUrl);
        if (ssrfError != null) {
            return Result.ok(Map.of("ok", false, "message", ssrfError));
        }

        String loginEndpoint = serverUrl + "/login";

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> endpoints = new LinkedHashMap<>();

        try {
            HttpClient client =
                    HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
            HttpRequest httpRequest =
                    HttpRequest.newBuilder()
                            .uri(URI.create(loginEndpoint))
                            .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                            .GET()
                            .build();
            HttpResponse<String> response =
                    client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            endpoints.put("login", loginEndpoint + " → " + status);

            if (status >= 200 && status < 400) {
                result.put("ok", true);
                result.put("message", "CAS 服务器连接成功 (HTTP " + status + ")");
            } else {
                result.put("ok", false);
                result.put("message", "CAS 服务器返回 HTTP " + status);
            }
        } catch (IOException e) {
            endpoints.put("login", loginEndpoint + " → 连接失败");
            result.put("ok", false);
            result.put("message", "连接失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            result.put("ok", false);
            result.put("message", "连接超时");
        }

        result.put("endpoints", endpoints);
        return Result.ok(result);
    }

    /**
     * 获取 CAS 回调地址 — 管理员配置外部 CAS 服务注册表时使用.
     *
     * @return 包含 base 和 pattern 的回调地址信息
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
    @GetMapping("/callback-url")
    public Result<Map<String, String>> callbackUrl() {
        // 回调 URL 指向网关地址 — 由网关转发到 auth-center
        // 实际部署时通过环境变量覆盖
        return Result.ok(
                Map.of(
                        "base", "/cas/external-callback",
                        "pattern",
                                "/cas/external-callback?ticket={ticket}&originalService={service}"));
    }

    /* ---------- 私有辅助方法 ---------- */

    /**
     * 将 SsoConfig 实体转为前端 IdentityProvider 格式.
     *
     * @param config SSO 配置实体
     * @return IdentityProvider 格式的 Map
     */
    private Map<String, Object> toIdentityProvider(SsoConfig config) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", config.getId());
        map.put("systemCode", config.getSystemCode());
        map.put("name", config.getDisplayName() != null ? config.getDisplayName() : "CAS");
        map.put("providerType", DEFAULT_TYPE);
        map.put("slug", DEFAULT_SLUG);
        map.put("configJson", config.getConfigJson() != null ? config.getConfigJson() : "{}");
        map.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        map.put("loginMode", config.getMode() != null ? config.getMode() : "disabled");
        map.put("icon", config.getIcon() != null ? config.getIcon() : "");
        map.put("workspaceId", null);
        map.put(
                "createTime",
                config.getCreatedAt() != null ? config.getCreatedAt().toString() : null);
        map.put(
                "updateTime",
                config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : null);
        return map;
    }

    /**
     * 从 configJson 字符串中提取 serverUrl 并同步到实体顶层字段.
     *
     * @param config SSO 配置实体
     * @param configJson JSON 字符串
     */
    private void extractServerUrl(SsoConfig config, String configJson) {
        String url = extractServerUrlFromJson(configJson);
        if (url != null) {
            config.setServerUrl(url);
        }
    }

    /**
     * 从 configJson 字符串中提取 serverUrl 值.
     *
     * @param configJson JSON 字符串
     * @return serverUrl 值，提取失败返回 null
     */
    private String extractServerUrlFromJson(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        // 简单提取 — 避免引入额外 JSON 依赖
        // configJson 格式: {"serverUrl":"https://...","protocolVersion":"3.0",...}
        int idx = configJson.indexOf("\"serverUrl\"");
        if (idx < 0) {
            return null;
        }
        int colonIdx = configJson.indexOf(':', idx);
        if (colonIdx < 0) {
            return null;
        }
        int startQuote = configJson.indexOf('"', colonIdx);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = configJson.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return configJson.substring(startQuote + 1, endQuote);
    }

    /**
     * SSRF 防护 — 校验 URL 为公网可达的 HTTP(S) 地址。
     *
     * 拒绝私有网段（10/8, 172.16/12, 192.168/16）、环回、链路本地和元数据端点，防止管理员通过 CAS 测试端点探测内网。
     *
     * @param url 待校验 URL
     * @return 错误消息（通过校验返回 null）
     */
    private static String validatePublicUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "URL 格式无效";
        }

        String scheme = uri.getScheme();
        if (scheme == null
                || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            return "仅允许 http/https 协议";
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return "URL 缺少主机名";
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return "无法解析主机名: " + host;
        }

        for (InetAddress addr : addresses) {
            if (addr.isLoopbackAddress()
                    || addr.isSiteLocalAddress()
                    || addr.isLinkLocalAddress()
                    || addr.isAnyLocalAddress()
                    || isMetadataAddress(addr)) {
                return "不允许连接到内网/本地地址: " + addr.getHostAddress();
            }
        }
        return null;
    }

    /** 云厂商元数据端点 169.254.169.254 */
    private static boolean isMetadataAddress(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 4
                && (bytes[0] & 0xFF) == 169
                && (bytes[1] & 0xFF) == 254
                && (bytes[2] & 0xFF) == 169
                && (bytes[3] & 0xFF) == 254;
    }
}
