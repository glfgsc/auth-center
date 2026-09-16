package com.auth.center.oauth;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** OAuth Authorization Server 的部署级默认配置。管理台保存的值优先于这些部署默认值。 */
@ConfigurationProperties(prefix = "auth.oauth")
public class OAuthProperties {

    private static final Pattern SCOPE_TOKEN = Pattern.compile("[\\x21\\x23-\\x5B\\x5D-\\x7E]+");

    private String issuer;
    private String mcpResourceUrl;
    private List<String> scopes = new ArrayList<>(List.of("bi:mcp"));
    private int accessTokenTtlSeconds = 900;
    private int refreshTokenTtlSeconds = 604800;
    private int authorizationCodeTtlSeconds = 60;
    private String resourceName = "知数 BI MCP";
    private String resourceDocumentation;

    @PostConstruct
    public void validateOnStartup() {
        validate();
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getMcpResourceUrl() {
        return mcpResourceUrl;
    }

    public void setMcpResourceUrl(String mcpResourceUrl) {
        this.mcpResourceUrl = mcpResourceUrl;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    public int getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(int accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public int getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(int refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public int getAuthorizationCodeTtlSeconds() {
        return authorizationCodeTtlSeconds;
    }

    public void setAuthorizationCodeTtlSeconds(int authorizationCodeTtlSeconds) {
        this.authorizationCodeTtlSeconds = authorizationCodeTtlSeconds;
    }

    public String getResourceName() {
        return resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceDocumentation() {
        return resourceDocumentation;
    }

    public void setResourceDocumentation(String resourceDocumentation) {
        this.resourceDocumentation = resourceDocumentation;
    }

    public void validate() {
        requireUrl("auth.oauth.issuer", issuer);
        requireUrl("auth.oauth.mcp-resource-url", mcpResourceUrl);
        if (!"/api/agent/mcp".equals(URI.create(mcpResourceUrl).getPath())) {
            throw new IllegalStateException(
                    "auth.oauth.mcp-resource-url path must be /api/agent/mcp");
        }
        if (scopes == null
                || scopes.isEmpty()
                || scopes.stream()
                        .anyMatch(
                                s ->
                                        s == null
                                                || s.isBlank()
                                                || !SCOPE_TOKEN.matcher(s).matches())) {
            throw new IllegalStateException(
                    "auth.oauth.scopes contain an invalid OAuth scope token");
        }
        if (scopes.stream().map(String::trim).distinct().count() != scopes.size()) {
            throw new IllegalStateException("auth.oauth.scopes must not contain duplicates");
        }
        if (accessTokenTtlSeconds < 60 || accessTokenTtlSeconds > 3600) {
            throw new IllegalStateException(
                    "auth.oauth.access-token-ttl-seconds must be between 60 and 3600");
        }
        if (refreshTokenTtlSeconds < 300 || refreshTokenTtlSeconds > 2592000) {
            throw new IllegalStateException(
                    "auth.oauth.refresh-token-ttl-seconds is outside the allowed range");
        }
        if (authorizationCodeTtlSeconds < 10 || authorizationCodeTtlSeconds > 300) {
            throw new IllegalStateException(
                    "auth.oauth.authorization-code-ttl-seconds is outside the allowed range");
        }
    }

    private static void requireUrl(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        URI uri = URI.create(value);
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    name + " must be an absolute HTTP(S) URL without query or fragment");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                && !List.of("localhost", "127.0.0.1", "::1").contains(uri.getHost())) {
            throw new IllegalStateException(name + " must use HTTPS outside localhost");
        }
    }
}
