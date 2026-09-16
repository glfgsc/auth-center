package com.auth.center.service.impl;

import com.auth.center.entity.ConnectedApp;
import com.auth.center.entity.ConnectedAppSecret;
import com.auth.center.mapper.ConnectedAppMapper;
import com.auth.center.mapper.ConnectedAppSecretMapper;
import com.auth.center.service.IConnectedAppService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link IConnectedAppService} 默认实现 -- 外部应用注册与密钥管理（从 BI 上移到认证中心）.
 *
 * 密钥安全策略：32 字节 SecureRandom → Base64URL；仅存 SHA-256 hash（原文只在创建响应返回一次）；每个应用最多 {@value
 * #MAX_SECRETS_PER_APP} 个并行密钥。
 */
@Service
public class ConnectedAppServiceImpl implements IConnectedAppService {

    private static final Logger log = LoggerFactory.getLogger(ConnectedAppServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 每个应用最多允许的并行密钥数（用于无停机轮换） */
    private static final int MAX_SECRETS_PER_APP = 2;

    /** 目标系统缺省值（当前唯一支持嵌入的系统为洞察） */
    private static final String DEFAULT_TARGET_SYSTEM = "bi";

    /** Direct-Trust 断言要求的受众（{@code aud}）—— 外部应用签发断言时必须写入 */
    private static final String EMBED_AUDIENCE = "bi-embed";

    /** 断言允许的最大有效期（毫秒，5 分钟）—— 缩小重放窗口 */
    private static final long MAX_ASSERTION_TTL_MILLIS = 5L * 60 * 1000;

    /** jti 去重缓存容量上限 */
    private static final int JTI_CACHE_MAX = 10000;

    private final ConnectedAppMapper appMapper;
    private final ConnectedAppSecretMapper secretMapper;

    /**
     * 已消费的断言 jti → 过期时刻（毫秒）—— 单实例内防重放（best-effort）。
     *
     * 多副本部署下各副本独立，重放窗口极窄（断言 ≤5 分钟）且重放不产生越权（同一 sub/资产），故不引入共享存储；断言的短时效 + audience 绑定为主防线。
     */
    private final Map<String, Long> seenJtis = new ConcurrentHashMap<>();

    /**
     * 构造注入.
     *
     * @param appMapper Connected App Mapper
     * @param secretMapper Connected App Secret Mapper
     */
    public ConnectedAppServiceImpl(
            ConnectedAppMapper appMapper, ConnectedAppSecretMapper secretMapper) {
        this.appMapper = appMapper;
        this.secretMapper = secretMapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<Map<String, Object>> listAll() {
        List<ConnectedApp> apps =
                appMapper.selectList(
                        new LambdaQueryWrapper<ConnectedApp>()
                                .orderByDesc(ConnectedApp::getCreatedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (ConnectedApp app : apps) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", app.getId());
            item.put("name", app.getName());
            item.put("clientId", app.getClientId());
            item.put("allowedDomains", app.getAllowedDomains());
            item.put("targetSystem", app.getTargetSystem());
            item.put("status", app.getStatus());
            // 公钥非机密：直接回传供详情页查看/编辑；hasPublicKey 供列表徽标。
            String pem = app.getAssertionPublicKeyPem();
            item.put("assertionPublicKeyPem", pem != null ? pem : "");
            item.put("hasPublicKey", pem != null && !pem.isBlank());
            item.put("createdBy", app.getCreatedBy());
            item.put("createdAt", app.getCreatedAt());
            item.put("updatedAt", app.getUpdatedAt());

            // 查询该应用的密钥列表（只返回 secretId + createdAt，不返回 hash）
            List<ConnectedAppSecret> secrets =
                    secretMapper.selectList(
                            new LambdaQueryWrapper<ConnectedAppSecret>()
                                    .eq(ConnectedAppSecret::getAppId, app.getId())
                                    .orderByAsc(ConnectedAppSecret::getCreatedAt));
            List<Map<String, Object>> secretList = new ArrayList<>();
            for (ConnectedAppSecret s : secrets) {
                Map<String, Object> si = new LinkedHashMap<>();
                si.put("secretId", s.getSecretId());
                si.put("createdAt", s.getCreatedAt());
                secretList.add(si);
            }
            item.put("secrets", secretList);
            item.put("secretCount", secrets.size());
            result.add(item);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(
            String name,
            String allowedDomains,
            String targetSystem,
            String assertionPublicKeyPem,
            Long createdBy) {
        ConnectedApp app = new ConnectedApp();
        app.setName(name);
        app.setClientId(UUID.randomUUID().toString());
        app.setAllowedDomains(allowedDomains);
        app.setTargetSystem(
                targetSystem != null && !targetSystem.isBlank()
                        ? targetSystem
                        : DEFAULT_TARGET_SYSTEM);
        app.setAssertionPublicKeyPem(normalizePem(assertionPublicKeyPem));
        app.setStatus("enabled");
        app.setCreatedBy(createdBy);
        app.setCreatedAt(LocalDateTime.now());
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.insert(app);

        // 自动生成第一个密钥
        String[] secretPair = createSecretRecord(app.getId());

        log.info(
                "[ConnectedApp] created app '{}' (clientId={}, secretId={})",
                name,
                app.getClientId(),
                secretPair[0]);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("app", app);
        result.put("clientId", app.getClientId());
        result.put("secretId", secretPair[0]);
        result.put("secretValue", secretPair[1]);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectedApp update(
            Long id,
            String name,
            String allowedDomains,
            String targetSystem,
            String status,
            String assertionPublicKeyPem) {
        ConnectedApp app = appMapper.selectById(id);
        if (app == null) {
            throw new IllegalArgumentException("Connected App not found: " + id);
        }
        if (name != null) app.setName(name);
        if (allowedDomains != null) app.setAllowedDomains(allowedDomains);
        if (targetSystem != null && !targetSystem.isBlank()) app.setTargetSystem(targetSystem);
        if (status != null) app.setStatus(status);
        // 公钥：null 表示不改；空串表示清除；非空须为合法 PEM public key。
        if (assertionPublicKeyPem != null) {
            app.setAssertionPublicKeyPem(normalizePem(assertionPublicKeyPem));
        }
        app.setUpdatedAt(LocalDateTime.now());
        appMapper.updateById(app);

        log.info("[ConnectedApp] updated app id={} name='{}'", id, app.getName());
        return app;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ConnectedApp app = appMapper.selectById(id);
        if (app == null) {
            throw new IllegalArgumentException("Connected App not found: " + id);
        }
        // 级联删除密钥（FK ON DELETE CASCADE 兜底，但显式删更清晰）
        secretMapper.delete(
                new LambdaQueryWrapper<ConnectedAppSecret>().eq(ConnectedAppSecret::getAppId, id));
        appMapper.deleteById(id);

        log.info(
                "[ConnectedApp] deleted app id={} name='{}' (secrets cascade-deleted)",
                id,
                app.getName());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> generateSecret(Long appId) {
        ConnectedApp app = appMapper.selectById(appId);
        if (app == null) {
            throw new IllegalArgumentException("Connected App not found: " + appId);
        }

        long count =
                secretMapper.selectCount(
                        new LambdaQueryWrapper<ConnectedAppSecret>()
                                .eq(ConnectedAppSecret::getAppId, appId));
        if (count >= MAX_SECRETS_PER_APP) {
            throw new IllegalStateException(
                    "Maximum "
                            + MAX_SECRETS_PER_APP
                            + " secrets per app; revoke an existing one first");
        }

        String[] pair = createSecretRecord(appId);

        // 乐观并发守卫：插入后重新计数，若超限则回滚刚插入的密钥
        long postInsertCount =
                secretMapper.selectCount(
                        new LambdaQueryWrapper<ConnectedAppSecret>()
                                .eq(ConnectedAppSecret::getAppId, appId));
        if (postInsertCount > MAX_SECRETS_PER_APP) {
            secretMapper.delete(
                    new LambdaQueryWrapper<ConnectedAppSecret>()
                            .eq(ConnectedAppSecret::getAppId, appId)
                            .eq(ConnectedAppSecret::getSecretId, pair[0]));
            throw new IllegalStateException(
                    "Maximum "
                            + MAX_SECRETS_PER_APP
                            + " secrets per app; revoke an existing one first");
        }

        log.info("[ConnectedApp] generated new secret for app id={} (secretId={})", appId, pair[0]);

        return Map.of("secretId", pair[0], "secretValue", pair[1]);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void revokeSecret(Long appId, String secretId) {
        int deleted =
                secretMapper.delete(
                        new LambdaQueryWrapper<ConnectedAppSecret>()
                                .eq(ConnectedAppSecret::getAppId, appId)
                                .eq(ConnectedAppSecret::getSecretId, secretId));
        if (deleted == 0) {
            throw new IllegalArgumentException("Secret not found: " + secretId);
        }
        log.info("[ConnectedApp] revoked secret {} for app id={}", secretId, appId);
    }

    /** {@inheritDoc} */
    @Override
    public ConnectedApp authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientSecret == null) return null;

        ConnectedApp app =
                appMapper.selectOne(
                        new LambdaQueryWrapper<ConnectedApp>()
                                .eq(ConnectedApp::getClientId, clientId));
        if (app == null) {
            log.debug("[ConnectedApp] auth failed — unknown clientId: {}", clientId);
            return null;
        }
        if (!"enabled".equals(app.getStatus())) {
            log.debug("[ConnectedApp] auth failed — app disabled: {}", clientId);
            return null;
        }

        String hash = sha256(clientSecret);
        ConnectedAppSecret match =
                secretMapper.selectOne(
                        new LambdaQueryWrapper<ConnectedAppSecret>()
                                .eq(ConnectedAppSecret::getAppId, app.getId())
                                .eq(ConnectedAppSecret::getSecretHash, hash));
        if (match == null) {
            log.debug("[ConnectedApp] auth failed — secret mismatch for clientId: {}", clientId);
            return null;
        }

        return app;
    }

    /** {@inheritDoc} */
    @Override
    public AssertionResult verifyAssertion(String clientId, String assertionJwt) {
        if (clientId == null || assertionJwt == null || assertionJwt.isBlank()) {
            return null;
        }

        ConnectedApp app =
                appMapper.selectOne(
                        new LambdaQueryWrapper<ConnectedApp>()
                                .eq(ConnectedApp::getClientId, clientId));
        if (app == null || !"enabled".equals(app.getStatus())) {
            log.debug(
                    "[ConnectedApp] assertion rejected — unknown/disabled clientId: {}", clientId);
            return null;
        }
        String pem = app.getAssertionPublicKeyPem();
        if (pem == null || pem.isBlank()) {
            log.warn(
                    "[ConnectedApp] assertion rejected — app '{}' has no assertion public key",
                    app.getName());
            return null;
        }

        PublicKey publicKey;
        try {
            publicKey = parsePublicKey(pem);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            log.warn(
                    "[ConnectedApp] assertion rejected — bad public key for app '{}': {}",
                    app.getName(),
                    e.getMessage());
            return null;
        }

        Claims claims;
        try {
            claims =
                    Jwts.parser()
                            .verifyWith(publicKey)
                            .build()
                            .parseSignedClaims(assertionJwt)
                            .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            log.warn(
                    "[ConnectedApp] assertion signature/format invalid for app '{}': {}",
                    app.getName(),
                    e.getMessage());
            return null;
        }

        // 受众：必须声明为 bi-embed，防止把它处签发的 JWT 挪用为嵌入断言
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(EMBED_AUDIENCE)) {
            log.warn("[ConnectedApp] assertion rejected — audience missing '{}'", EMBED_AUDIENCE);
            return null;
        }

        // 有效期上限：拒绝有效期过长的断言（exp 本身由 JJWT 校验未过期）
        Date exp = claims.getExpiration();
        if (exp == null || exp.getTime() - System.currentTimeMillis() > MAX_ASSERTION_TTL_MILLIS) {
            log.warn("[ConnectedApp] assertion rejected — missing or over-long expiration");
            return null;
        }

        // jti 防重放（单实例 best-effort）
        String jti = claims.getId();
        if (jti == null || jti.isBlank() || !recordJti(jti, exp.getTime())) {
            log.warn("[ConnectedApp] assertion rejected — missing or replayed jti");
            return null;
        }

        String subject = claims.getSubject();
        if (subject == null || subject.isBlank()) {
            log.warn("[ConnectedApp] assertion rejected — missing subject");
            return null;
        }

        return new AssertionResult(app, subject);
    }

    // ── 内部方法 ──────────────────────────────────────────────────────

    /**
     * 解析 PEM 公钥（SubjectPublicKeyInfo）为 RSA 公钥.
     *
     * @param pem PEM 格式公钥
     * @return RSA 公钥
     * @throws GeneralSecurityException 解析或密钥规格错误时抛出
     */
    private static PublicKey parsePublicKey(String pem) throws GeneralSecurityException {
        String base64 =
                pem.replaceAll("-----BEGIN (?:RSA )?PUBLIC KEY-----", "")
                        .replaceAll("-----END (?:RSA )?PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
        return KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    /**
     * 记录断言 jti，实现单实例防重放.
     *
     * @param jti 断言唯一标识
     * @param expiryMillis 断言过期时刻（用于容量清理）
     * @return 首次出现返回 {@code true}；已见过（重放）返回 {@code false}
     */
    private boolean recordJti(String jti, long expiryMillis) {
        long now = System.currentTimeMillis();
        if (seenJtis.size() > JTI_CACHE_MAX) {
            seenJtis.entrySet().removeIf(e -> e.getValue() < now);
        }
        return seenJtis.putIfAbsent(jti, expiryMillis) == null;
    }

    /**
     * 创建密钥记录，返回 [secretId, secretValue].
     *
     * @param appId 所属应用 ID
     * @return 密钥标识 + 原文（原文仅此一次可见）
     */
    private String[] createSecretRecord(Long appId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawSecret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        String secretId = "sec_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String hash = sha256(rawSecret);

        ConnectedAppSecret secret = new ConnectedAppSecret();
        secret.setAppId(appId);
        secret.setSecretId(secretId);
        secret.setSecretHash(hash);
        secret.setCreatedAt(LocalDateTime.now());
        secretMapper.insert(secret);

        return new String[] {secretId, rawSecret};
    }

    /**
     * 规范化并轻校验断言公钥 PEM —— 空白返回空串（可清除），非空须为 PEM public key.
     *
     * @param pem 传入的 PEM（可空）
     * @return 规范化后的 PEM，或空串
     * @throws IllegalArgumentException 非空但不是 PEM public key 时
     */
    private static String normalizePem(String pem) {
        if (pem == null || pem.isBlank()) {
            return "";
        }
        String trimmed = pem.trim();
        if (!trimmed.contains("BEGIN PUBLIC KEY")) {
            throw new IllegalArgumentException(
                    "assertion public key must be a PEM public key (-----BEGIN PUBLIC KEY-----)");
        }
        return trimmed;
    }

    /**
     * 计算 SHA-256 哈希.
     *
     * @param input 原始字符串
     * @return 十六进制 hash 字符串
     */
    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
