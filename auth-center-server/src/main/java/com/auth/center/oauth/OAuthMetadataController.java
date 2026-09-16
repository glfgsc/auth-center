package com.auth.center.oauth;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** RFC 8414 Authorization Server Metadata and the authorization server JWKS. */
@RestController
public class OAuthMetadataController {

    private final OAuthSettingsService settings;

    public OAuthMetadataController(OAuthSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping("/.well-known/oauth-authorization-server")
    public Map<String, Object> metadata() {
        String issuer = settings.current().issuer().replaceAll("/$", "");
        return Map.of(
                "issuer",
                issuer,
                "authorization_endpoint",
                issuer + "/oauth2/authorize",
                "token_endpoint",
                issuer + "/oauth2/token",
                "revocation_endpoint",
                issuer + "/oauth2/revoke",
                "jwks_uri",
                issuer + "/.well-known/jwks.json",
                "response_types_supported",
                List.of("code"),
                "grant_types_supported",
                List.of("authorization_code", "refresh_token"),
                "code_challenge_methods_supported",
                List.of("S256"),
                "token_endpoint_auth_methods_supported",
                List.of("client_secret_basic", "client_secret_post", "none"),
                "scopes_supported",
                settings.current().scopes());
    }
}
