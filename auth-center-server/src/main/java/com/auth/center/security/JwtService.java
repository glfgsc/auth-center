package com.auth.center.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 服务 —— 用 RSA 密钥对签发与验证 Token,算法 RS256。
 *
 * Claims:{@code sub} 用户名、{@code userId}、{@code permissionSet} 权限集名称、 {@code capabilities}
 * 逗号分隔能力列表、{@code jti} 唯一标识(吊销追踪用)。
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

    /** Claims 中「按系统权限」的键名（system -> {ps, caps[]}） */
    private static final String CLAIM_SYSTEM_PERMISSIONS = "systemPermissions";

    /** Claims 中「令牌作用域」的键名（嵌入会话标记为 {@code embed}, 供下游收窄授权面） */
    private static final String CLAIM_SCOPE = "scope";

    /** Claims 中「会话 ID」的键名（= refresh 族 ID, 跨续期稳定,供会话治理精确定位/移除会话）。 */
    private static final String CLAIM_SESSION_ID = "sid";

    private final RsaKeyPairProvider rsaKeyPairProvider;

    /** Access Token 有效期 (毫秒), 默认 15 分钟 */
    @Value("${auth.jwt.access-token-expiration:900000}")
    private long accessTokenExpiration;

    /** Refresh Token 有效期 (毫秒), 默认 7 天 */
    @Value("${auth.jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    /** 嵌入会话 Token 默认有效期 (毫秒), 默认 30 分钟 —— 短时,过期由 iframe 静默重取 */
    @Value("${auth.jwt.embed-token-expiration:1800000}")
    private long embedTokenExpiration;

    /**
     * 构造函数,注入 RSA 密钥对提供器.
     *
     * @param rsaKeyPairProvider RSA 密钥对提供器
     */
    public JwtService(RsaKeyPairProvider rsaKeyPairProvider) {
        this.rsaKeyPairProvider = rsaKeyPairProvider;
    }

    /**
     * 签发 Access Token.
     *
     * @param userId 用户 ID
     * @param username 用户名 (作为 subject)
     * @param permissionSet 向后兼容的权限集标量编码
     * @param capabilities 向后兼容的能力列表 (JSON 数组或逗号分隔)
     * @param systemPermissions 按系统权限结构 (system -&gt; {ps, caps[]}), 可为 null
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(
            Long userId,
            String username,
            String permissionSet,
            String capabilities,
            Map<String, Object> systemPermissions) {
        return generateAccessToken(
                userId, username, permissionSet, capabilities, systemPermissions, null);
    }

    /**
     * 签发 Access Token(带会话 ID)—— 用于需登记活跃会话的登录/续期路径.
     *
     * 比 {@link #generateAccessToken(Long, String, String, String, Map)} 多注入 {@code sid} claim (=
     * refresh 族 ID, 跨续期稳定), 供会话治理据此精确定位并移除会话;其余不变。{@code sessionId} 为空则不注入。
     *
     * @param userId 用户 ID
     * @param username 用户名 (作为 subject)
     * @param permissionSet 向后兼容的权限集标量编码
     * @param capabilities 向后兼容的能力列表 (JSON 数组或逗号分隔)
     * @param systemPermissions 按系统权限结构 (system -&gt; {ps, caps[]}), 可为 null
     * @param sessionId 会话 ID(refresh 族 ID), 可为 null(不注入 sid claim)
     * @return 签名后的 JWT 字符串
     */
    public String generateAccessToken(
            Long userId,
            String username,
            String permissionSet,
            String capabilities,
            Map<String, Object> systemPermissions,
            String sessionId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiration);

        var builder =
                Jwts.builder()
                        .subject(username)
                        .claim(CLAIM_USER_ID, userId)
                        .claim(CLAIM_PERMISSION_SET, permissionSet)
                        .claim(CLAIM_CAPABILITIES, capabilities);
        if (systemPermissions != null && !systemPermissions.isEmpty()) {
            builder.claim(CLAIM_SYSTEM_PERMISSIONS, systemPermissions);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            builder.claim(CLAIM_SESSION_ID, sessionId);
        }
        return builder.id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPairProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发 OAuth 2.1 Resource Server access token。
     *
     * 与旧登录 JWT 分开签发：OAuth token 必须带 issuer、resource audience、client_id 和 scope，
     * 这样 MCP Resource Server 不会把旧的管理台登录 token 当成 MCP bearer token 接受。
     */
    public String generateOAuthAccessToken(
            Long userId,
            String username,
            String permissionSet,
            String capabilities,
            Map<String, Object> systemPermissions,
            String issuer,
            String audience,
            String clientId,
            String scope,
            long ttlMillis) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMillis);
        var builder =
                Jwts.builder()
                        .subject(username)
                        .issuer(issuer)
                        .audience()
                        .add(audience)
                        .and()
                        .claim(CLAIM_USER_ID, userId)
                        .claim(CLAIM_PERMISSION_SET, permissionSet)
                        .claim(CLAIM_CAPABILITIES, capabilities)
                        .claim(CLAIM_SCOPE, scope)
                        .claim("client_id", clientId)
                        .claim("token_type", "access_token");
        if (systemPermissions != null && !systemPermissions.isEmpty()) {
            builder.claim(CLAIM_SYSTEM_PERMISSIONS, systemPermissions);
        }
        return builder.id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPairProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发嵌入会话 Token — 用于外部应用 SSO 直信任(Direct-Trust)嵌入场景.
     *
     * 与 {@link #generateAccessToken} 的关键差异,均为数据安全收窄:
     *
     *   - 作用域:强制注入 {@code scope=embed} + 资产/工作区绑定 claim, 下游据此把该会话只读能力收窄到所嵌资产(见
     *       {@code @EmbedGuard}).
     *   - 禁管理员旁路: {@code permissionSet} 恒传 {@code null} —— 下游 {@code X-Permission-Set} 不含
     *       {@code PS_admin}, RLS/CLS/工作区隔离对该会话始终强制生效,即便终端用户本人是管理员. {@code capabilities}
     *       仍为真实解析值,使行级/列级权限按其真实角色/分组评估.
     *   - 短时:有效期取 {@code ttlMillis}(&gt;0) 或默认 {@link #embedTokenExpiration}.
     *
     * @param userId 终端用户 ID(RLS/CLS 身份来源)
     * @param username 用户名 (subject)
     * @param capabilities 真实解析的能力列表(承载角色/分组,供 RLS/CLS 使用)
     * @param scopeClaims 作用域绑定 claim (如 {@code assetType/assetId/workspaceId}), 可为 null
     * @param ttlMillis 自定义有效期(毫秒); &lt;=0 时用默认 {@link #embedTokenExpiration}
     * @return 签名后的嵌入会话 JWT 字符串
     */
    public String generateEmbedToken(
            Long userId,
            String username,
            String capabilities,
            Map<String, Object> scopeClaims,
            long ttlMillis) {
        Date now = new Date();
        long ttl = ttlMillis > 0 ? ttlMillis : embedTokenExpiration;
        Date expiry = new Date(now.getTime() + ttl);

        var builder =
                Jwts.builder()
                        .subject(username)
                        .claim(CLAIM_USER_ID, userId)
                        // permissionSet 恒 null: 杜绝 PS_admin 旁路, embed 会话始终受 RLS/CLS/工作区约束
                        .claim(CLAIM_CAPABILITIES, capabilities)
                        .claim(CLAIM_SCOPE, "embed");
        if (scopeClaims != null && !scopeClaims.isEmpty()) {
            scopeClaims.forEach(builder::claim);
        }
        return builder.id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPairProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发 Refresh Token — 仅含 userId 和 token 类型标记,不含权限信息.
     *
     * Refresh Token 用于在 Access Token 过期后无感知续期,有效期较长(默认 7 天), 每次使用时 rotation(签发新 refresh + 新
     * access)。
     *
     * @param userId 用户 ID
     * @param username 用户名 (subject)
     * @return 签名后的 Refresh Token JWT 字符串
     */
    public String generateRefreshToken(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshTokenExpiration);

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(rsaKeyPairProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 获取 Refresh Token 有效期毫秒数.
     *
     * @return refresh token TTL (毫秒)
     */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    /**
     * 获取 Access Token 有效期毫秒数.
     *
     * @return access token TTL (毫秒)
     */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
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

    /**
     * 从 Claims 中提取会话 ID(sid)—— 无 sid claim 时返回 null(如 CAS / 嵌入令牌等未登记会话的路径).
     *
     * @param claims 已解析的 Claims
     * @return 会话 ID(refresh 族 ID); 无则 {@code null}
     */
    public String sessionIdOf(Claims claims) {
        return claims.get(CLAIM_SESSION_ID, String.class);
    }
}
