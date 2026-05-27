package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.SsoConfig;
import com.auth.center.service.ISsoConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SSO 管理控制器 -- 对齐前端 IdentityProvider 数据模型.
 *
 * <p>前端 PlatformSettingsPage 的 SSO tab 调用以下端点:
 * <ul>
 *     <li>{@code GET  /api/auth/sso/admin/cas}       — 读取 CAS 配置</li>
 *     <li>{@code POST /api/auth/sso/admin/cas}       — 创建/更新 CAS 配置</li>
 *     <li>{@code DELETE /api/auth/sso/admin/cas}      — 删除 CAS 配置</li>
 *     <li>{@code POST /api/auth/sso/admin/cas/test}   — 测试 CAS 服务器连通性</li>
 *     <li>{@code GET  /api/auth/sso/admin/callback-url} — 获取 CAS 回调地址</li>
 * </ul>
 *
 * <p>所有端点需认证（SecurityConfig 中 /api/auth/sso/admin/** → authenticated）。</p>
 */
@RestController
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
     * 读取当前 CAS 配置 — 转为前端 IdentityProvider 格式.
     *
     * @return CAS 配置，不存在时 data=null
     */
    @GetMapping("/cas")
    public Result<Map<String, Object>> getCas() {
        SsoConfig config = ssoConfigService.getConfig();
        if (config == null) {
            return Result.ok(null);
        }
        return Result.ok(toIdentityProvider(config));
    }

    /**
     * 创建或更新 CAS 配置 — 接收前端 IdentityProvider 格式.
     *
     * @param body 前端发送的 IdentityProvider 字段
     * @return 保存后的配置
     */
    @PostMapping("/cas")
    public Result<Map<String, Object>> saveCas(@RequestBody Map<String, Object> body) {
        SsoConfig config = ssoConfigService.getConfig();
        if (config == null) {
            config = new SsoConfig();
            config.setType(DEFAULT_TYPE);
        }

        // 映射前端字段到实体
        if (body.containsKey("name")) {
            config.setDisplayName(String.valueOf(body.get("name")));
        }
        if (body.containsKey("icon")) {
            config.setIcon(body.get("icon") != null ? String.valueOf(body.get("icon")) : "");
        }
        if (body.containsKey("enabled")) {
            config.setEnabled(Boolean.TRUE.equals(body.get("enabled")));
        }
        if (body.containsKey("loginMode")) {
            String loginMode = String.valueOf(body.get("loginMode"));
            config.setMode(loginMode);
        }
        if (body.containsKey("configJson")) {
            String configJson = String.valueOf(body.get("configJson"));
            config.setConfigJson(configJson);
            // 从 configJson 中提取 serverUrl 同步到顶层字段（向后兼容 public-config）
            extractServerUrl(config, configJson);
        }

        SsoConfig saved = ssoConfigService.updateConfig(config);
        log.info("SSO CAS 配置已保存: mode={}, enabled={}", saved.getMode(), saved.getEnabled());
        return Result.ok(toIdentityProvider(saved));
    }

    /**
     * 删除 CAS 配置 — 回到纯本地登录模式.
     *
     * @return 删除确认消息
     */
    @DeleteMapping("/cas")
    public Result<String> deleteCas() {
        ssoConfigService.deleteConfig();
        log.info("SSO CAS 配置已删除");
        return Result.ok("deleted");
    }

    /**
     * 测试 CAS 服务器连通性 — 探测 /login 端点可达性.
     *
     * @param body 包含 configJson 的请求体
     * @return 测试结果 {ok, message, endpoints}
     */
    @PostMapping("/cas/test")
    public Result<Map<String, Object>> testCas(@RequestBody Map<String, Object> body) {
        String configJson = body.get("configJson") != null
                ? String.valueOf(body.get("configJson")) : "{}";
        String serverUrl = extractServerUrlFromJson(configJson);

        if (serverUrl == null || serverUrl.isBlank()) {
            return Result.ok(Map.of(
                    "ok", false,
                    "message", "CAS 服务器地址为空"
            ));
        }

        // 去除尾部斜杠
        serverUrl = serverUrl.replaceAll("/+$", "");
        String loginEndpoint = serverUrl + "/login";

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> endpoints = new LinkedHashMap<>();

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(loginEndpoint))
                    .timeout(Duration.ofSeconds(TEST_TIMEOUT_SECONDS))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

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
    @GetMapping("/callback-url")
    public Result<Map<String, String>> callbackUrl() {
        // 回调 URL 指向网关地址 — 由网关转发到 auth-center
        // 实际部署时通过环境变量覆盖
        return Result.ok(Map.of(
                "base", "/cas/external-callback",
                "pattern", "/cas/external-callback?ticket={ticket}&originalService={service}"
        ));
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
        map.put("name", config.getDisplayName() != null ? config.getDisplayName() : "CAS");
        map.put("providerType", DEFAULT_TYPE);
        map.put("slug", DEFAULT_SLUG);
        map.put("configJson", config.getConfigJson() != null ? config.getConfigJson() : "{}");
        map.put("enabled", Boolean.TRUE.equals(config.getEnabled()));
        map.put("loginMode", config.getMode() != null ? config.getMode() : "disabled");
        map.put("icon", config.getIcon() != null ? config.getIcon() : "");
        map.put("workspaceId", null);
        map.put("createTime", config.getCreatedAt() != null ? config.getCreatedAt().toString() : null);
        map.put("updateTime", config.getUpdatedAt() != null ? config.getUpdatedAt().toString() : null);
        return map;
    }

    /**
     * 从 configJson 字符串中提取 serverUrl 并同步到实体顶层字段.
     *
     * @param config     SSO 配置实体
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
}
