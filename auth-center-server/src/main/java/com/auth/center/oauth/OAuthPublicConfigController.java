package com.auth.center.oauth;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Runtime 读取 OAuth 资源边界的无敏感信息配置端点。 */
@RestController
public class OAuthPublicConfigController {
    private final OAuthSettingsService settings;

    public OAuthPublicConfigController(OAuthSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/api/auth/oauth/public-config")
    public Map<String, Object> publicConfig() {
        OAuthSettingsService.Snapshot current = settings.current();
        String issuer = current.issuer().replaceAll("/$", "");
        return Map.of(
                "authorizationServer",
                issuer,
                "issuer",
                issuer,
                "jwksUri",
                issuer + "/.well-known/jwks.json",
                "resourceUrl",
                current.mcpResourceUrl(),
                "scopes",
                current.scopes(),
                "resourceName",
                current.resourceName(),
                "resourceDocumentation",
                current.resourceDocumentation() == null ? "" : current.resourceDocumentation());
    }
}
