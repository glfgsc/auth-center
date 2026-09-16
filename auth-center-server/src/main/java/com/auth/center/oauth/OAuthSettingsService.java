package com.auth.center.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OAuth 配置的数据库源；没有管理记录时才读取部署默认值。 */
@Service
public class OAuthSettingsService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private static final Pattern SCOPE_TOKEN = Pattern.compile("[\\x21\\x23-\\x5B\\x5D-\\x7E]+");
    private final OAuthSettingsMapper mapper;
    private final OAuthProperties defaults;
    private final ObjectMapper objectMapper;

    public OAuthSettingsService(
            OAuthSettingsMapper mapper, OAuthProperties defaults, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.defaults = defaults;
        this.objectMapper = objectMapper;
    }

    public Snapshot current() {
        OAuthSettings saved = mapper.selectById(1L);
        if (saved == null) {
            return new Snapshot(
                    defaults.getIssuer(),
                    defaults.getMcpResourceUrl(),
                    defaults.getScopes(),
                    defaults.getAccessTokenTtlSeconds(),
                    defaults.getRefreshTokenTtlSeconds(),
                    defaults.getAuthorizationCodeTtlSeconds(),
                    defaults.getResourceName(),
                    defaults.getResourceDocumentation());
        }
        return new Snapshot(
                saved.getIssuer(),
                saved.getMcpResourceUrl(),
                readScopes(saved.getScopes()),
                saved.getAccessTokenTtlSeconds(),
                saved.getRefreshTokenTtlSeconds(),
                saved.getAuthorizationCodeTtlSeconds(),
                saved.getResourceName(),
                saved.getResourceDocumentation());
    }

    @Transactional(rollbackFor = Exception.class)
    public Snapshot save(Snapshot value, Long updatedBy) {
        validate(value);
        OAuthSettings saved = mapper.selectById(1L);
        boolean insert = saved == null;
        if (insert) {
            saved = new OAuthSettings();
            saved.setId(1L);
        }
        saved.setIssuer(value.issuer());
        saved.setMcpResourceUrl(value.mcpResourceUrl());
        saved.setScopes(writeScopes(value.scopes()));
        saved.setAccessTokenTtlSeconds(value.accessTokenTtlSeconds());
        saved.setRefreshTokenTtlSeconds(value.refreshTokenTtlSeconds());
        saved.setAuthorizationCodeTtlSeconds(value.authorizationCodeTtlSeconds());
        saved.setResourceName(value.resourceName());
        saved.setResourceDocumentation(value.resourceDocumentation());
        saved.setUpdatedBy(updatedBy);
        saved.setUpdatedAt(LocalDateTime.now());
        if (insert) mapper.insert(saved);
        else mapper.updateById(saved);
        return current();
    }

    public void validate(Snapshot value) {
        if (value == null) throw new IllegalArgumentException("OAuth settings are required");
        validateUrl(value.issuer(), "issuer");
        validateUrl(value.mcpResourceUrl(), "mcpResourceUrl");
        if (!"/api/agent/mcp".equals(URI.create(value.mcpResourceUrl()).getPath()))
            throw new IllegalArgumentException("mcpResourceUrl path must be /api/agent/mcp");
        if (value.scopes() == null
                || value.scopes().isEmpty()
                || value.scopes().stream()
                        .anyMatch(
                                s ->
                                        s == null
                                                || s.isBlank()
                                                || !SCOPE_TOKEN.matcher(s).matches())) {
            throw new IllegalArgumentException("scopes contain an invalid OAuth scope token");
        }
        if (value.scopes().stream().map(String::trim).distinct().count() != value.scopes().size())
            throw new IllegalArgumentException("scopes must not contain duplicates");
        if (value.resourceName() == null || value.resourceName().isBlank())
            throw new IllegalArgumentException("resourceName is required");
        if (value.resourceName().length() > 200)
            throw new IllegalArgumentException("resourceName is too long");
        if (value.resourceDocumentation() != null && !value.resourceDocumentation().isBlank())
            validateUrl(value.resourceDocumentation(), "resourceDocumentation");
        if (value.accessTokenTtlSeconds() == null
                || value.accessTokenTtlSeconds() < 60
                || value.accessTokenTtlSeconds() > 3600)
            throw new IllegalArgumentException("accessTokenTtlSeconds must be between 60 and 3600");
        if (value.refreshTokenTtlSeconds() == null
                || value.refreshTokenTtlSeconds() < 300
                || value.refreshTokenTtlSeconds() > 2592000)
            throw new IllegalArgumentException(
                    "refreshTokenTtlSeconds is outside the allowed range");
        if (value.authorizationCodeTtlSeconds() == null
                || value.authorizationCodeTtlSeconds() < 10
                || value.authorizationCodeTtlSeconds() > 300)
            throw new IllegalArgumentException(
                    "authorizationCodeTtlSeconds is outside the allowed range");
    }

    private static void validateUrl(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " is required");
        URI uri = URI.create(value);
        boolean loopback = List.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
        if (!("https".equalsIgnoreCase(uri.getScheme())
                        || (loopback && "http".equalsIgnoreCase(uri.getScheme())))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    field + " must be an absolute HTTPS URL (localhost may use HTTP)");
        }
    }

    private List<String> readScopes(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("Stored OAuth scopes are invalid", e);
        }
    }

    private String writeScopes(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(
                    new ArrayList<>(scopes).stream().map(String::trim).distinct().toList());
        } catch (Exception e) {
            throw new IllegalStateException("OAuth scopes cannot be serialized", e);
        }
    }

    public record Snapshot(
            String issuer,
            String mcpResourceUrl,
            List<String> scopes,
            Integer accessTokenTtlSeconds,
            Integer refreshTokenTtlSeconds,
            Integer authorizationCodeTtlSeconds,
            String resourceName,
            String resourceDocumentation) {}
}
