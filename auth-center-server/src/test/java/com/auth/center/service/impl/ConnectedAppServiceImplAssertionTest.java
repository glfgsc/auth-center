package com.auth.center.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.auth.center.entity.ConnectedApp;
import com.auth.center.mapper.ConnectedAppMapper;
import com.auth.center.mapper.ConnectedAppSecretMapper;
import com.auth.center.service.IConnectedAppService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * {@link ConnectedAppServiceImpl#verifyAssertion} 的安全单测 —— SSO 直信任身份密码学门。
 *
 * 认证中心是连接应用验签的唯一信任源(BI 侧已删);此测覆盖:合法断言取 sub、伪造签名拒、缺 audience 拒、过期拒、有效期过长拒、jti
 * 重放拒、应用未配公钥拒、应用停用拒。全为纯 Mockito + 真实 RSA 签名(无 Spring / DB)。
 */
class ConnectedAppServiceImplAssertionTest {

    private static final String CLIENT_ID = "app-xyz";
    private static final String AUDIENCE = "bi-embed";
    private static final String SUBJECT = "alice@corp.com";

    private static KeyPair registeredKeyPair;
    private static KeyPair attackerKeyPair;

    private ConnectedAppMapper appMapper;
    private ConnectedAppServiceImpl svc;

    /** LambdaQueryWrapper 依赖 TableInfo 元数据;纯单测无 Spring 需手动初始化。 */
    @BeforeAll
    static void init() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ConnectedApp.class);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        registeredKeyPair = kpg.generateKeyPair();
        attackerKeyPair = kpg.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        appMapper = Mockito.mock(ConnectedAppMapper.class);
        ConnectedAppSecretMapper secretMapper = Mockito.mock(ConnectedAppSecretMapper.class);
        svc = new ConnectedAppServiceImpl(appMapper, secretMapper);
    }

    /* ── helpers ─────────────────────────────────────────────────────── */

    private void stubApp(String status, PublicKey pemKey) {
        ConnectedApp app = new ConnectedApp();
        app.setId(1L);
        app.setName("CRM");
        app.setClientId(CLIENT_ID);
        app.setStatus(status);
        app.setAssertionPublicKeyPem(pemKey != null ? toPem(pemKey) : null);
        when(appMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(app);
    }

    private static String toPem(PublicKey key) {
        String base64 = Base64.getEncoder().encodeToString(key.getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    private static String signAssertion(
            PrivateKey key, String audience, String subject, String jti, long ttlMillis) {
        var builder = Jwts.builder().subject(subject).id(jti).issuedAt(new Date());
        if (audience != null) {
            builder.audience().add(audience).and();
        }
        return builder.expiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    private String validAssertion() {
        return signAssertion(
                registeredKeyPair.getPrivate(),
                AUDIENCE,
                SUBJECT,
                UUID.randomUUID().toString(),
                60_000);
    }

    /* ── tests ───────────────────────────────────────────────────────── */

    @Test
    void validAssertion_returnsSubject() {
        stubApp("enabled", registeredKeyPair.getPublic());
        IConnectedAppService.AssertionResult result =
                svc.verifyAssertion(CLIENT_ID, validAssertion());
        assertNotNull(result);
        assertEquals(SUBJECT, result.subject());
        assertEquals(CLIENT_ID, result.app().getClientId());
    }

    @Test
    void tamperedSignature_returnsNull() {
        // 用攻击者私钥签,但应用注册的是另一把公钥 → 验签失败。
        stubApp("enabled", registeredKeyPair.getPublic());
        String forged =
                signAssertion(
                        attackerKeyPair.getPrivate(),
                        AUDIENCE,
                        SUBJECT,
                        UUID.randomUUID().toString(),
                        60_000);
        assertNull(svc.verifyAssertion(CLIENT_ID, forged));
    }

    @Test
    void missingAudience_returnsNull() {
        stubApp("enabled", registeredKeyPair.getPublic());
        String noAud =
                signAssertion(
                        registeredKeyPair.getPrivate(),
                        null,
                        SUBJECT,
                        UUID.randomUUID().toString(),
                        60_000);
        assertNull(svc.verifyAssertion(CLIENT_ID, noAud));
    }

    @Test
    void expired_returnsNull() {
        stubApp("enabled", registeredKeyPair.getPublic());
        String expired =
                signAssertion(
                        registeredKeyPair.getPrivate(),
                        AUDIENCE,
                        SUBJECT,
                        UUID.randomUUID().toString(),
                        -1_000);
        assertNull(svc.verifyAssertion(CLIENT_ID, expired));
    }

    @Test
    void overLongExpiry_returnsNull() {
        // 有效期 10 分钟 > 上限 5 分钟 → 拒绝(缩小重放窗口)。
        stubApp("enabled", registeredKeyPair.getPublic());
        String longLived =
                signAssertion(
                        registeredKeyPair.getPrivate(),
                        AUDIENCE,
                        SUBJECT,
                        UUID.randomUUID().toString(),
                        10 * 60_000);
        assertNull(svc.verifyAssertion(CLIENT_ID, longLived));
    }

    @Test
    void replayedJti_returnsNull() {
        stubApp("enabled", registeredKeyPair.getPublic());
        String assertion = validAssertion();
        assertNotNull(svc.verifyAssertion(CLIENT_ID, assertion)); // 首次通过
        assertNull(svc.verifyAssertion(CLIENT_ID, assertion)); // 重放拒
    }

    @Test
    void appWithoutPublicKey_returnsNull() {
        stubApp("enabled", null);
        assertNull(svc.verifyAssertion(CLIENT_ID, validAssertion()));
    }

    @Test
    void disabledApp_returnsNull() {
        stubApp("disabled", registeredKeyPair.getPublic());
        assertNull(svc.verifyAssertion(CLIENT_ID, validAssertion()));
    }
}
