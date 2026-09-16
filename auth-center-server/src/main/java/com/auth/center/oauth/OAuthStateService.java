package com.auth.center.oauth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** OAuth 授权码、授权事务和 refresh token 的共享存储。所有一次性对象均在 Redis 中原子消费。 */
@Service
public class OAuthStateService {

    private static final String AUTHORIZATION_KEY = "auth:oauth:code:";
    private static final String TRANSACTION_KEY = "auth:oauth:tx:";
    private static final String REFRESH_KEY = "auth:oauth:refresh:";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OAuthStateService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String createTransaction(Map<String, Object> request, long ttlSeconds) {
        return put(TRANSACTION_KEY, request, ttlSeconds);
    }

    public Map<String, Object> consumeTransaction(String transactionId) {
        return getAndDelete(TRANSACTION_KEY + transactionId, MAP_TYPE);
    }

    public String createAuthorizationCode(Map<String, Object> code, long ttlSeconds) {
        return put(AUTHORIZATION_KEY, code, ttlSeconds);
    }

    public Map<String, Object> consumeAuthorizationCode(String code) {
        return getAndDelete(AUTHORIZATION_KEY + code, MAP_TYPE);
    }

    public String createRefreshToken(Map<String, Object> token, long ttlSeconds) {
        String raw =
                ENCODER.encodeToString(
                        (UUID.randomUUID().toString() + UUID.randomUUID()).getBytes());
        try {
            redis.opsForValue()
                    .set(
                            REFRESH_KEY + hash(raw),
                            objectMapper.writeValueAsString(token),
                            ttlSeconds,
                            TimeUnit.SECONDS);
            return raw;
        } catch (Exception e) {
            throw new IllegalStateException("OAuth token storage failed", e);
        }
    }

    public Map<String, Object> consumeRefreshToken(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return getAndDelete(REFRESH_KEY + hash(raw), MAP_TYPE);
    }

    private String put(String prefix, Object value, long ttlSeconds) {
        String id =
                ENCODER.encodeToString(
                        (UUID.randomUUID().toString() + UUID.randomUUID()).getBytes());
        try {
            redis.opsForValue()
                    .set(
                            prefix + id,
                            objectMapper.writeValueAsString(value),
                            ttlSeconds,
                            TimeUnit.SECONDS);
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("OAuth state storage failed", e);
        }
    }

    private <T> T getAndDelete(String key, TypeReference<T> type) {
        String json = redis.opsForValue().getAndDelete(key);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("OAuth state is corrupt", e);
        }
    }

    private static String hash(String value) {
        try {
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
