package com.auth.center.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.jupiter.api.Test;

/**
 * {@link LoginCryptoService} 加解密往返测试 —— 用服务下发的公钥以 RSA-OAEP(SHA-256, MGF1-SHA256) 加密,经服务私钥解密还原,
 * 验证填充参数自洽(与前端 Web Crypto {@code RSA-OAEP}/SHA-256 对齐:两侧 OAEP + MGF1 均用 SHA-256)。
 */
class LoginCryptoServiceTest {

    private final LoginCryptoService service;

    LoginCryptoServiceTest() throws Exception {
        service = new LoginCryptoService();
    }

    /** 用服务公钥以 RSA-OAEP(SHA-256, MGF1-SHA256)加密,模拟前端 Web Crypto 的加密。 */
    private String encryptWithServicePublicKey(String plaintext) throws GeneralSecurityException {
        byte[] spki = Base64.getDecoder().decode(service.getPublicKeyBase64());
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        OAEPParameterSpec oaep =
                new OAEPParameterSpec(
                        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.ENCRYPT_MODE, pub, oaep);
        return Base64.getEncoder()
                .encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void roundTripDecryptsWhatPublicKeyEncrypted() throws Exception {
        String payload = System.currentTimeMillis() + ":P@ssw0rd!中文";
        String cipher = encryptWithServicePublicKey(payload);
        assertEquals(payload, service.decrypt(cipher));
    }

    @Test
    void publicKeyIsParsableSpki() throws Exception {
        assertNotNull(service.getPublicKeyBase64());
        byte[] spki = Base64.getDecoder().decode(service.getPublicKeyBase64());
        KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
    }

    @Test
    void decryptRejectsNonCiphertext() {
        // 明文 / 非法 Base64 → 抛异常(控制器据此回退明文兼容处理,不误判为密文)
        assertThrows(Exception.class, () -> service.decrypt("not-a-valid-ciphertext"));
    }
}
