package com.auth.sdk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JWT 验证器 -- 从 Auth Center JWKS 端点获取 RSA 公钥,本地验证 JWT 签名.
 *
 * 公钥缓存策略:
 *
 *   - 正常情况下每 {@value #REFRESH_INTERVAL_MINUTES} 分钟自动刷新
 *   - 验证失败时立即刷新一次 (应对密钥轮换场景)
 *   - JWKS 拉取失败时降级使用缓存中的旧公钥,避免全局不可用
 *
 * 线程安全:使用 {@code volatile} + 同步块保护公钥缓存,可在多线程环境中共享使用.
 */
public class JwtValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtValidator.class);

    /** JWKS 端点路径 (相对于 Auth Center 根路径) */
    private static final String JWKS_PATH = "/.well-known/jwks.json";

    /** 公钥自动刷新间隔 (分钟) */
    private static final int REFRESH_INTERVAL_MINUTES = 30;

    /** 公钥自动刷新间隔 (毫秒) */
    private static final long REFRESH_INTERVAL_MS = (long) REFRESH_INTERVAL_MINUTES * 60 * 1000;

    /** HTTP 请求超时时间 (秒) */
    private static final int HTTP_TIMEOUT_SECONDS = 10;

    /** HTTP 200 状态码 */
    private static final int HTTP_OK = 200;

    /** JWKS JSON 字段:密钥数组 */
    private static final String JWKS_FIELD_KEYS = "keys";

    /** JWK 字段:密钥类型 */
    private static final String JWK_FIELD_KTY = "kty";

    /** JWK 字段: RSA 模数 */
    private static final String JWK_FIELD_N = "n";

    /** JWK 字段: RSA 指数 */
    private static final String JWK_FIELD_E = "e";

    /** RSA 密钥类型标识 */
    private static final String KEY_TYPE_RSA = "RSA";

    /** BigInteger 正数符号位 */
    private static final int POSITIVE_SIGNUM = 1;

    private final String jwksUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    /** 缓存的 RSA 公钥, volatile 保证多线程可见性 */
    private volatile RSAPublicKey cachedPublicKey;

    /** 上次成功拉取公钥的时间戳 (毫秒) */
    private volatile long lastFetchTimeMs;

    /** 公钥刷新锁,防止并发重复拉取 */
    private final Object fetchLock = new Object();

    /**
     * 构造 JWT 验证器.
     *
     * @param authCenterBaseUrl Auth Center 根 URL, 如 {@code http://auth-center:8090} 末尾不需要斜杠
     */
    public JwtValidator(String authCenterBaseUrl) {
        // 去除末尾斜杠
        String baseUrl =
                authCenterBaseUrl.endsWith("/")
                        ? authCenterBaseUrl.substring(0, authCenterBaseUrl.length() - 1)
                        : authCenterBaseUrl;
        this.jwksUrl = baseUrl + JWKS_PATH;
        this.objectMapper = new ObjectMapper();
        this.httpClient =
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                        .build();
    }

    /**
     * 验证 JWT 并返回 Claims.
     *
     * 用缓存的公钥验证签名和有效期;签名失败时立即刷新公钥后重试一次(应对密钥轮换),重试仍失败则抛出异常。
     *
     * @param token JWT 字符串 (不含 "Bearer " 前缀)
     * @return 解析后的 Claims
     * @throws JwtException 签名无效、已过期或格式错误时抛出
     * @throws IllegalStateException 无法获取 RSA 公钥时抛出
     */
    public Claims validateAndParse(String token) {
        RSAPublicKey publicKey = getPublicKey();
        try {
            return parseWithKey(token, publicKey);
        } catch (JwtException e) {
            // 签名失败:可能是密钥轮换,强制刷新公钥后重试一次
            log.info("JWT 验证失败, 尝试刷新公钥后重试: {}", e.getMessage());
            RSAPublicKey refreshedKey = forceRefreshPublicKey();
            if (refreshedKey != null && refreshedKey != publicKey) {
                return parseWithKey(token, refreshedKey);
            }
            // 刷新后仍然失败,抛出原始异常
            throw e;
        }
    }

    /**
     * 检查 JWT 是否有效 (签名正确且未过期).
     *
     * @param token JWT 字符串
     * @return {@code true} 表示有效, {@code false} 表示无效
     */
    public boolean isValid(String token) {
        try {
            validateAndParse(token);
            return true;
        } catch (Exception e) {
            log.debug("JWT 校验不通过: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 验证 JWT 并构建 {@link AuthUserDetails} 用户主体.
     *
     * @param token JWT 字符串
     * @return 从 JWT claims 构建的用户主体
     * @throws JwtException JWT 验证失败时抛出
     * @throws IllegalArgumentException claims 缺少必需字段时抛出
     */
    public AuthUserDetails toUserDetails(String token) {
        Claims claims = validateAndParse(token);
        return AuthUserDetails.fromJwtClaims(claims);
    }

    /**
     * 验证 JWT 并构建「指定系统」的 {@link AuthUserDetails} 用户主体.
     *
     * 主体只含该 {@code systemCode} 系统（+ global 通用）的权限集与能力，从而各平台只拿到与自己相关的授权，实现按系统隔离。
     *
     * @param token JWT 字符串
     * @param systemCode 当前系统编码（如 bi / tracking）
     * @return 从 JWT claims 构建的（按系统）用户主体
     * @throws JwtException JWT 验证失败时抛出
     * @throws IllegalArgumentException claims 缺少必需字段时抛出
     */
    public AuthUserDetails toUserDetails(String token, String systemCode) {
        Claims claims = validateAndParse(token);
        return AuthUserDetails.fromJwtClaims(claims, systemCode);
    }

    // ======================== 内部方法 ========================

    /**
     * 使用指定公钥解析 JWT.
     *
     * @param token JWT 字符串
     * @param publicKey RSA 公钥
     * @return 解析后的 Claims
     * @throws JwtException 验证失败时抛出
     */
    private Claims parseWithKey(String token, RSAPublicKey publicKey) {
        return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
    }

    /**
     * 获取 RSA 公钥:缓存有效则直接返回,否则从 JWKS 端点拉取.
     *
     * @return RSA 公钥
     * @throws IllegalStateException 无法获取公钥时抛出
     */
    private RSAPublicKey getPublicKey() {
        RSAPublicKey key = cachedPublicKey;
        long now = System.currentTimeMillis();

        // 缓存有效:公钥存在且未过期
        if (key != null && (now - lastFetchTimeMs) < REFRESH_INTERVAL_MS) {
            return key;
        }

        // 缓存过期或不存在:拉取新公钥
        return refreshPublicKey();
    }

    /**
     * 强制刷新公钥 (不检查缓存时效).
     *
     * @return 新的 RSA 公钥,或 null 表示拉取失败
     */
    private RSAPublicKey forceRefreshPublicKey() {
        synchronized (fetchLock) {
            try {
                RSAPublicKey newKey = fetchPublicKey();
                cachedPublicKey = newKey;
                lastFetchTimeMs = System.currentTimeMillis();
                return newKey;
            } catch (Exception e) {
                log.warn("强制刷新 JWKS 公钥失败: {}", e.getMessage());
                return cachedPublicKey;
            }
        }
    }

    /**
     * 刷新公钥:从 JWKS 端点拉取,失败时降级使用缓存.
     *
     * @return RSA 公钥
     * @throws IllegalStateException 无缓存且拉取失败时抛出
     */
    private RSAPublicKey refreshPublicKey() {
        synchronized (fetchLock) {
            // 双重检查:可能其他线程已完成刷新
            RSAPublicKey key = cachedPublicKey;
            long now = System.currentTimeMillis();
            if (key != null && (now - lastFetchTimeMs) < REFRESH_INTERVAL_MS) {
                return key;
            }

            try {
                RSAPublicKey newKey = fetchPublicKey();
                cachedPublicKey = newKey;
                lastFetchTimeMs = System.currentTimeMillis();
                return newKey;
            } catch (Exception e) {
                log.warn("从 JWKS 端点拉取公钥失败, URL={}: {}", jwksUrl, e.getMessage());
                // 降级:使用缓存中的旧公钥
                if (key != null) {
                    log.info("降级使用缓存的旧公钥");
                    return key;
                }
                throw new IllegalStateException("无法获取 RSA 公钥且无缓存可用, JWKS URL: " + jwksUrl, e);
            }
        }
    }

    /**
     * 从 JWKS 端点拉取 RSA 公钥 —— HTTP GET JWKS URL → 解析 JSON 取 {@code keys} 数组中第一个 RSA 密钥 → 从 {@code
     * n}(模数)和 {@code e}(指数)构造 {@link RSAPublicKey}.
     *
     * @return RSA 公钥
     * @throws Exception 网络请求失败、JSON 解析失败或密钥构造失败时抛出
     */
    private RSAPublicKey fetchPublicKey() throws Exception {
        log.debug("正在从 JWKS 端点拉取公钥: {}", jwksUrl);

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(jwksUrl))
                        .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HTTP_OK) {
            throw new IllegalStateException(
                    "JWKS 请求失败, HTTP " + response.statusCode() + ", URL: " + jwksUrl);
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode keys = root.get(JWKS_FIELD_KEYS);
        if (keys == null || !keys.isArray() || keys.isEmpty()) {
            throw new IllegalStateException("JWKS 响应中缺少有效的 keys 数组");
        }

        // 查找第一个 RSA 类型的 JWK
        for (JsonNode jwk : keys) {
            String kty = jwk.has(JWK_FIELD_KTY) ? jwk.get(JWK_FIELD_KTY).asText() : "";
            if (KEY_TYPE_RSA.equals(kty)) {
                return buildRsaPublicKey(jwk);
            }
        }

        throw new IllegalStateException("JWKS 中未找到 RSA 类型的密钥");
    }

    /**
     * 从 JWK JSON 节点构建 RSA 公钥.
     *
     * @param jwk JWK JSON 节点,需包含 {@code n} 和 {@code e} 字段
     * @return RSA 公钥
     * @throws NoSuchAlgorithmException JVM 不支持 RSA 算法时抛出
     * @throws InvalidKeySpecException 密钥参数无效时抛出
     */
    private RSAPublicKey buildRsaPublicKey(JsonNode jwk)
            throws NoSuchAlgorithmException, InvalidKeySpecException {
        String nBase64 = jwk.get(JWK_FIELD_N).asText();
        String eBase64 = jwk.get(JWK_FIELD_E).asText();

        // Base64url 解码 -> BigInteger (无符号正数)
        Base64.Decoder urlDecoder = Base64.getUrlDecoder();
        BigInteger modulus = new BigInteger(POSITIVE_SIGNUM, urlDecoder.decode(nBase64));
        BigInteger exponent = new BigInteger(POSITIVE_SIGNUM, urlDecoder.decode(eBase64));

        RSAPublicKeySpec keySpec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_TYPE_RSA);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
