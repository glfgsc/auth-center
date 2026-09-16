package com.auth.center.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.spec.MGF1ParameterSpec;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 登录密码传输加密 -- 提供 RSA-2048 公钥供前端加密登录密码,后端以私钥解密.
 *
 * 目的:登录密码不再以明文进入请求体 -- 即便 TLS 在中间节点(反向代理 / LB / WAF)被终止或请求体被记入日志,密码也是密文. 前端登录时先取公钥,用
 * RSA-OAEP(SHA-256) 加密 {@code <serverTime>:<password>} 后提交;后端解密并校验时间戳新鲜度以抗重放.
 *
 * 密钥对进程内生成、重启轮换,私钥不落盘、不出进程. auth-center 单实例部署,前端每次登录即时取公钥再加密,故轮换对可用性无影响.
 *
 * 填充与前端 Web Crypto {@code RSA-OAEP}(hash=SHA-256) 对齐: Java 侧显式指定 {@link OAEPParameterSpec} 的
 * MGF1 也用 SHA-256(Java 默认 MGF1 为 SHA-1, 不显式指定会解密失败).
 */
@Component
public class LoginCryptoService {

    private static final Logger log = LoggerFactory.getLogger(LoginCryptoService.class);

    /** RSA 密钥长度 (bits). */
    private static final int RSA_KEY_SIZE = 2048;

    /** RSA-OAEP(SHA-256) 变换名. */
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final KeyPair keyPair;
    private final String publicKeyBase64;

    /**
     * 构造时生成进程内 RSA 密钥对.
     *
     * @throws NoSuchAlgorithmException RSA 算法不可用(标准 JDK 恒可用)
     */
    public LoginCryptoService() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        this.keyPair = generator.generateKeyPair();
        this.publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        log.info("[LoginCrypto] 登录密码解密 RSA-{} 密钥对已生成(进程内, 重启轮换)", RSA_KEY_SIZE);
    }

    /**
     * 获取用于前端加密的公钥 -- X.509/SPKI DER 的 Base64 编码.
     *
     * @return Base64 公钥(前端 Web Crypto {@code importKey('spki', ...)} 直接可用)
     */
    public String getPublicKeyBase64() {
        return publicKeyBase64;
    }

    /**
     * 用私钥解密前端 RSA-OAEP(SHA-256) 加密的 Base64 密文.
     *
     * @param base64Ciphertext 前端提交的 Base64 密文
     * @return 解密后的明文(UTF-8)
     * @throws GeneralSecurityException 密文非法 / 填充不符 / 密钥不匹配(调用方应统一按凭据错误处理,不泄露具体原因)
     */
    public String decrypt(String base64Ciphertext) throws GeneralSecurityException {
        byte[] cipherBytes = Base64.getDecoder().decode(base64Ciphertext);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        OAEPParameterSpec oaep =
                new OAEPParameterSpec(
                        "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);
        cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(), oaep);
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
