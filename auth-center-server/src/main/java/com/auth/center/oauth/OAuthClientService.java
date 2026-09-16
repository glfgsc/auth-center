package com.auth.center.oauth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** OAuth 客户端注册与协议校验的唯一入口。 */
@Service
public class OAuthClientService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final OAuthClientMapper mapper;
    private final ObjectMapper objectMapper;
    private final OAuthSettingsService settings;

    public OAuthClientService(
            OAuthClientMapper mapper, ObjectMapper objectMapper, OAuthSettingsService settings) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.settings = settings;
    }

    public OAuthClient findEnabled(String clientId) {
        if (clientId == null || clientId.isBlank()) return null;
        return mapper.selectOne(
                new LambdaQueryWrapper<OAuthClient>()
                        .eq(OAuthClient::getClientId, clientId)
                        .eq(OAuthClient::getStatus, "enabled"));
    }

    public boolean authenticateSecret(OAuthClient client, String secret) {
        if (client == null || secret == null || client.getClientSecretHash() == null) return false;
        return MessageDigest.isEqual(
                client.getClientSecretHash().getBytes(StandardCharsets.US_ASCII),
                sha256(secret).getBytes(StandardCharsets.US_ASCII));
    }

    public List<String> redirectUris(OAuthClient client) {
        return parseList(client.getRedirectUris(), "redirect_uris");
    }

    public List<String> scopes(OAuthClient client) {
        return parseList(client.getScopes(), "scopes");
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OAuthClient client :
                mapper.selectList(
                        new LambdaQueryWrapper<OAuthClient>()
                                .orderByDesc(OAuthClient::getCreatedAt))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", client.getId());
            item.put("clientId", client.getClientId());
            item.put("clientName", client.getClientName());
            item.put("clientType", client.getClientType());
            item.put("redirectUris", redirectUris(client));
            item.put("scopes", scopes(client));
            item.put("resourceAudience", client.getResourceAudience());
            item.put("tokenTtlSeconds", client.getTokenTtlSeconds());
            item.put("status", client.getStatus());
            item.put("createdAt", client.getCreatedAt());
            item.put("updatedAt", client.getUpdatedAt());
            result.add(item);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(
            String clientName,
            String clientType,
            List<String> redirectUris,
            List<String> scopes,
            String resourceAudience,
            Integer tokenTtlSeconds,
            Long createdBy) {
        validateRegistration(
                clientName, clientType, redirectUris, scopes, resourceAudience, tokenTtlSeconds);
        OAuthClient client = new OAuthClient();
        client.setClientId("mcp_" + ENCODER.encodeToString(randomBytes(18)));
        client.setClientName(clientName.trim());
        client.setClientType(clientType);
        String secret = "confidential".equals(clientType) ? randomSecret() : null;
        if (secret != null) client.setClientSecretHash(sha256(secret));
        client.setRedirectUris(writeList(redirectUris));
        client.setScopes(writeList(scopes));
        client.setResourceAudience(resourceAudience.trim());
        client.setTokenTtlSeconds(tokenTtlSeconds);
        client.setStatus("enabled");
        client.setCreatedBy(createdBy);
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        mapper.insert(client);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientId", client.getClientId());
        if (secret != null) result.put("clientSecret", secret);
        result.put("client", publicView(client));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public OAuthClient update(
            Long id,
            String clientName,
            List<String> redirectUris,
            List<String> scopes,
            String resourceAudience,
            Integer tokenTtlSeconds,
            String status) {
        OAuthClient client = mapper.selectById(id);
        if (client == null) throw new IllegalArgumentException("OAuth client not found");
        validateRegistration(
                clientName != null ? clientName : client.getClientName(),
                client.getClientType(),
                redirectUris != null ? redirectUris : redirectUris(client),
                scopes != null ? scopes : scopes(client),
                resourceAudience != null ? resourceAudience : client.getResourceAudience(),
                tokenTtlSeconds != null ? tokenTtlSeconds : client.getTokenTtlSeconds());
        if (clientName != null) client.setClientName(clientName.trim());
        if (redirectUris != null) client.setRedirectUris(writeList(redirectUris));
        if (scopes != null) client.setScopes(writeList(scopes));
        if (resourceAudience != null) client.setResourceAudience(resourceAudience.trim());
        if (tokenTtlSeconds != null) client.setTokenTtlSeconds(tokenTtlSeconds);
        if (status != null) {
            if (!List.of("enabled", "disabled").contains(status))
                throw new IllegalArgumentException("invalid client status");
            client.setStatus(status);
        }
        client.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(client);
        return client;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (mapper.deleteById(id) == 0)
            throw new IllegalArgumentException("OAuth client not found");
    }

    public Map<String, Object> publicView(OAuthClient client) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", client.getId());
        item.put("clientId", client.getClientId());
        item.put("clientName", client.getClientName());
        item.put("clientType", client.getClientType());
        item.put("redirectUris", redirectUris(client));
        item.put("scopes", scopes(client));
        item.put("resourceAudience", client.getResourceAudience());
        item.put("tokenTtlSeconds", client.getTokenTtlSeconds());
        item.put("status", client.getStatus());
        return item;
    }

    private void validateRegistration(
            String clientName,
            String clientType,
            List<String> redirectUris,
            List<String> scopes,
            String resourceAudience,
            Integer tokenTtlSeconds) {
        if (clientName == null || clientName.isBlank() || clientName.length() > 128)
            throw new IllegalArgumentException("clientName is required");
        if (!List.of("public", "confidential").contains(clientType))
            throw new IllegalArgumentException("clientType must be public or confidential");
        if (redirectUris == null || redirectUris.isEmpty())
            throw new IllegalArgumentException("redirectUris is required");
        redirectUris.forEach(OAuthClientService::validateRedirectUri);
        OAuthSettingsService.Snapshot current = settings.current();
        if (scopes == null || scopes.isEmpty() || !current.scopes().containsAll(scopes))
            throw new IllegalArgumentException("scopes exceed the configured OAuth scopes");
        if (resourceAudience == null || !resourceAudience.equals(current.mcpResourceUrl()))
            throw new IllegalArgumentException(
                    "resourceAudience must equal the configured MCP resource");
        if (tokenTtlSeconds == null
                || tokenTtlSeconds < 60
                || tokenTtlSeconds > current.accessTokenTtlSeconds())
            throw new IllegalArgumentException("tokenTtlSeconds is outside the allowed range");
    }

    private static void validateRedirectUri(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("redirectUris contains a blank URL");
        URI uri = URI.create(value);
        boolean loopback = List.of("localhost", "127.0.0.1", "::1").contains(uri.getHost());
        if (!("https".equalsIgnoreCase(uri.getScheme())
                        || ("http".equalsIgnoreCase(uri.getScheme()) && loopback))
                || uri.getHost() == null
                || uri.getFragment() != null
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "redirectUri must use HTTPS (HTTP is allowed only for loopback) and must not contain a fragment or user info");
        }
    }

    private List<String> parseList(String json, String field) {
        try {
            List<String> values = objectMapper.readValue(json, STRING_LIST);
            if (values == null || values.stream().anyMatch(v -> v == null || v.isBlank()))
                throw new IllegalArgumentException(field + " is invalid");
            return values;
        } catch (Exception e) {
            throw new IllegalArgumentException(field + " is invalid", e);
        }
    }

    private String writeList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(
                    values.stream().map(String::trim).distinct().toList());
        } catch (Exception e) {
            throw new IllegalArgumentException("OAuth registration cannot be serialized", e);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String randomSecret() {
        return ENCODER.encodeToString(randomBytes(32));
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
