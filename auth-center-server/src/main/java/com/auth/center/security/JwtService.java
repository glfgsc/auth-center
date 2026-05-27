package com.auth.center.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

/**
 * JWT 服务 -- 使用 RSA 密钥对签发与验证 JSON Web Token.
 *
 * <p>签发的 Token 使用 RS256 (RSA + SHA-256) 算法, 包含以下 Claims:
 * <ul>
 *     <li>{@code sub} - 用户名</li>
 *     <li>{@code userId} - 用户 ID</li>
 *     <li>{@code permissionSet} - 权限集名称</li>
 *     <li>{@code capabilities} - 逗号分隔的能力列表</li>
 *     <li>{@code jti} - 唯一标识, 用于吊销追踪</li>
 * </ul>
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** Claims 中用户 ID 的键名 */
    private static final String CLAIM_USER_ID = "userId";

    /** Claims 中权限集的键名 */
    private static final String CLAIM_PERMISSION_SET = "permissionSet";

    /** Claims 中能力列表的键名 */
    private static final String CLAIM_CAPABILITIES = "capabilities";

    private final RsaKeyPairProvider rsaKeyPairProvider;

    /** Access Token 有效期 (毫秒), 默认 24 小时 */
    @Value("${auth.jwt.access-token-expiration:86400000}")
    private long accessTokenExpiration;

    /**
     * 构造函数, 注入 RSA 密钥对提供器.
     *
     * @param rsaKeyPairProvider RSA 密钥对提供器
     */
    public JwtService(RsaKeyPairProvider rsaKeyPairProvider) {
        this.rsaKeyPairProvider = rsaKeyPairProvider;
    }

    /**
     * 签发 Access Token.
     *
     * @param userId        用户 ID
     * @param username      用户名 (作为 subject)
     * @param permissionSet 权限集名称
     * @param capabilities  逗号分隔的能力列表
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(Long userId, String username,
                                      String permissionSet, String capabilities) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_PERMISSION_SET, permissionSet)
                .claim(CLAIM_CAPABILITIES, capabilities)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPairProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 解析并验证 Token, 返回 Claims.
     *
     * @param token JWT 字符串
     * @return 解析后的 Claims
     * @throws JwtException 签名无效、已过期或格式错误时抛出
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(rsaKeyPairProvider.getPublicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 验证 Token 是否有效 (签名正确且未过期).
     *
     * @param token JWT 字符串
     * @return {@code true} 表示有效, {@code false} 表示无效
     */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从 Token 中提取 JTI (JWT ID), 用于吊销追踪.
     *
     * @param token JWT 字符串
     * @return JTI 字符串
     * @throws JwtException Token 解析失败时抛出
     */
    public String getJti(String token) {
        return parseToken(token).getId();
    }
}
