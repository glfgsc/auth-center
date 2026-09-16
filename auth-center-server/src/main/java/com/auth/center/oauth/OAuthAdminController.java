package com.auth.center.oauth;

import com.auth.center.common.Result;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth Center 管理 OAuth 服务配置与客户端注册，CAS 配置仍由 SSO Controller 独立管理。 */
@RestController
@RequestMapping("/api/auth/admin/oauth")
@PreAuthorize("@authPerm.isAdmin()")
public class OAuthAdminController {
    private final OAuthSettingsService settings;
    private final OAuthClientService clients;

    public OAuthAdminController(OAuthSettingsService settings, OAuthClientService clients) {
        this.settings = settings;
        this.clients = clients;
    }

    @GetMapping("/settings")
    public Result<Map<String, Object>> settings() {
        return Result.ok(publicSettings(settings.current()));
    }

    public record SettingsRequest(
            String issuer,
            String mcpResourceUrl,
            List<String> scopes,
            Integer accessTokenTtlSeconds,
            Integer refreshTokenTtlSeconds,
            Integer authorizationCodeTtlSeconds,
            String resourceName,
            String resourceDocumentation) {}

    @PutMapping("/settings")
    public Result<Map<String, Object>> saveSettings(@Valid @RequestBody SettingsRequest request) {
        try {
            OAuthSettingsService.Snapshot value =
                    new OAuthSettingsService.Snapshot(
                            request.issuer(),
                            request.mcpResourceUrl(),
                            request.scopes(),
                            request.accessTokenTtlSeconds(),
                            request.refreshTokenTtlSeconds(),
                            request.authorizationCodeTtlSeconds(),
                            request.resourceName(),
                            request.resourceDocumentation());
            return Result.ok(publicSettings(settings.save(value, getCurrentUserId())));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @GetMapping("/clients")
    public Result<List<Map<String, Object>>> clients() {
        return Result.ok(clients.list());
    }

    public record ClientRequest(
            String clientName,
            String clientType,
            List<String> redirectUris,
            List<String> scopes,
            String resourceAudience,
            Integer tokenTtlSeconds,
            String status) {}

    @PostMapping("/clients")
    public Result<Map<String, Object>> createClient(@Valid @RequestBody ClientRequest request) {
        try {
            return Result.ok(
                    clients.create(
                            request.clientName(),
                            request.clientType(),
                            request.redirectUris(),
                            request.scopes(),
                            request.resourceAudience(),
                            request.tokenTtlSeconds(),
                            getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @PutMapping("/clients/{id}")
    public Result<Map<String, Object>> updateClient(
            @PathVariable Long id, @Valid @RequestBody ClientRequest request) {
        try {
            return Result.ok(
                    clients.publicView(
                            clients.update(
                                    id,
                                    request.clientName(),
                                    request.redirectUris(),
                                    request.scopes(),
                                    request.resourceAudience(),
                                    request.tokenTtlSeconds(),
                                    request.status())));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    @DeleteMapping("/clients/{id}")
    public Result<Void> deleteClient(@PathVariable Long id) {
        try {
            clients.delete(id);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    private static Map<String, Object> publicSettings(OAuthSettingsService.Snapshot value) {
        return Map.of(
                "issuer", value.issuer(),
                "mcpResourceUrl", value.mcpResourceUrl(),
                "scopes", value.scopes(),
                "accessTokenTtlSeconds", value.accessTokenTtlSeconds(),
                "refreshTokenTtlSeconds", value.refreshTokenTtlSeconds(),
                "authorizationCodeTtlSeconds", value.authorizationCodeTtlSeconds(),
                "resourceName", value.resourceName(),
                "resourceDocumentation",
                        value.resourceDocumentation() == null ? "" : value.resourceDocumentation());
    }

    @SuppressWarnings("unchecked")
    private static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        Object details = authentication.getDetails();
        if (details instanceof Map<?, ?> detailsMap) {
            Object userId = ((Map<String, Object>) detailsMap).get("userId");
            if (userId instanceof Number number) return number.longValue();
        }
        return null;
    }
}
