package com.auth.center.security.crypto;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * AES-256-GCM 对称加解密 -- 认证中心里静态凭据的落库保护.
 *
 * 线格式: {@code "ENC:" + base64(iv || ciphertext || gcm-tag)}. 与洞察侧同格式,便于凭据从 bi_core 迁入时按原密钥
 * 解出再以本服务密钥重加密.
 *
 * 密钥按两级取:先看 {@code auth.crypto.master-key}(环境变量 {@code AUTH_CRYPTO_KEY});没配则回退到密钥目录 {@code
 * auth.crypto.key-dir}(默认与 JWT 的 {@code auth.jwt.rsa-key-dir} 同目录)下的 {@value #KEY_FILE} --
 * 文件在就读、不在就生成一个 32 字节随机密钥落盘,与 {@code RsaKeyPairProvider} 处理 RSA 密钥对同一套做法.
 *
 * 回退这一级的意义是让密钥既不必写进配置中心(那里有版本历史,明文会永久留痕), 也不必为它单开一个 k8s Secret. 代价是密钥跟着卷走:换卷等于换密钥,
 * 已落库的密文全部解不开.
 *
 * 两级都拿不到时仍允许启动,但一旦 encrypt 或对密文 decrypt 即 fail-closed 抛错 -- 让认证中心因为一个尚未启用的功能起不来不合算,
 * 而静默用空密钥加密是更坏的结果.
 */
@Service
public class CryptoService implements ICryptoService {

    private static final Logger log = LoggerFactory.getLogger(CryptoService.class);

    /** 标识加密值的线格式前缀. */
    public static final String CIPHER_PREFIX = "ENC:";

    /** AES-GCM 初始化向量长度(字节). */
    private static final int IV_BYTES = 12;

    /** AES-GCM 认证标签长度(位). */
    private static final int TAG_BITS = 128;

    /** JCE 加密算法标识. */
    private static final String CIPHER_ALGO = "AES/GCM/NoPadding";

    /** 密钥目录下的主密钥文件名. */
    static final String KEY_FILE = "crypto-master.key";

    /** 生成主密钥时的字节数 -- AES-256 所需. */
    private static final int GENERATED_KEY_BYTES = 32;

    private final String configuredKey;
    private final String keyDir;
    private SecretKey secretKey;
    private final SecureRandom rng = new SecureRandom();

    /**
     * 构造方法.
     *
     * @param masterKey 主密钥,取自配置或环境变量;未配置时为空串
     * @param keyDir 主密钥文件所在目录,默认跟随 JWT 的 RSA 密钥对目录(生产上是持久卷)
     */
    public CryptoService(
            @Value("${auth.crypto.master-key:${AUTH_CRYPTO_KEY:}}") String masterKey,
            @Value("${auth.crypto.key-dir:${auth.jwt.rsa-key-dir:./data/keys}}") String keyDir) {
        this.configuredKey = masterKey;
        this.keyDir = keyDir;
    }

    /** 初始化 AES 密钥 -- 先取配置,再回退密钥文件. */
    @PostConstruct
    void init() {
        String key = configuredKey;
        if (key == null || key.isBlank()) {
            key = loadOrCreateKeyFile();
        }
        if (key == null || key.isBlank()) {
            log.warn(
                    "[Crypto] auth.crypto.master-key (env AUTH_CRYPTO_KEY) not set and key file"
                            + " unavailable -- encrypt and decrypt-of-ciphertext will fail if"
                            + " invoked. Expected only while the notification credential feature is"
                            + " unused.");
            this.secretKey = null;
            return;
        }
        // SHA-256 把任意长度口令归一成 32 字节 AES-256 密钥,免得换成 24 字符的轮换令牌就 InvalidKeyException.
        this.secretKey = new SecretKeySpec(sha256(key.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    /**
     * 从密钥目录读主密钥,没有就生成一个并落盘.
     *
     * 目录不可写时返回 {@code null} 而不抛 -- 认证中心的其余功能与本密钥无关,不该因为它起不来;调用加解密时自会 fail-closed.
     *
     * @return base64 编码的主密钥;读写均失败时为 {@code null}
     */
    private String loadOrCreateKeyFile() {
        Path path = Paths.get(keyDir).resolve(KEY_FILE);
        try {
            if (Files.exists(path)) {
                String existing = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (!existing.isEmpty()) {
                    log.info("[Crypto] 已从 {} 读取主密钥", path.toAbsolutePath());
                    return existing;
                }
                log.warn("[Crypto] 主密钥文件为空, 重新生成: {}", path.toAbsolutePath());
            }
            byte[] fresh = new byte[GENERATED_KEY_BYTES];
            rng.nextBytes(fresh);
            String encoded = Base64.getEncoder().encodeToString(fresh);
            Files.createDirectories(path.getParent());
            Files.writeString(path, encoded, StandardCharsets.UTF_8);
            restrictFilePermissions(path);
            log.info("[Crypto] 已生成主密钥并保存至 {}", path.toAbsolutePath());
            return encoded;
        } catch (IOException e) {
            log.warn("[Crypto] 主密钥文件不可用({}): {}", path.toAbsolutePath(), e.getMessage());
            return null;
        }
    }

    /**
     * 把密钥文件权限收窄到仅属主可读写.
     *
     * @param path 密钥文件路径
     */
    private void restrictFilePermissions(Path path) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException e) {
            // Windows -- 文件系统无 POSIX 权限支持。生产 (Linux) 不会走到这里;真走到了说明密钥文件
            // 未被收窄权限,故留痕而非静默吞。
            log.debug("[Crypto] 文件系统不支持 POSIX 权限, 跳过密钥文件权限收窄: {}", path);
        } catch (IOException e) {
            log.warn("[Crypto] 无法设置密钥文件权限: {}", e.getMessage());
        }
    }

    /**
     * 密钥就绪守卫 -- 没有密钥就抛,而不是静默用弱密钥.
     *
     * @param op 操作名,仅用于错误信息
     */
    private void requireKey(String op) {
        if (secretKey == null) {
            throw new IllegalStateException(
                    "[Crypto] "
                            + op
                            + " invoked but no master key is available: neither"
                            + " auth.crypto.master-key (env AUTH_CRYPTO_KEY) nor the key file under"
                            + " auth.crypto.key-dir.");
        }
    }

    @Override
    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(CIPHER_PREFIX);
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        if (isEncrypted(plaintext)) return plaintext;
        requireKey("encrypt");
        try {
            byte[] iv = new byte[IV_BYTES];
            rng.nextBytes(iv);
            Cipher c = Cipher.getInstance(CIPHER_ALGO);
            c.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] joined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, joined, 0, iv.length);
            System.arraycopy(ct, 0, joined, iv.length, ct.length);
            return CIPHER_PREFIX + Base64.getEncoder().encodeToString(joined);
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    @Override
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) return cipherText;
        if (!isEncrypted(cipherText)) return cipherText;
        requireKey("decrypt");
        try {
            byte[] joined =
                    Base64.getDecoder().decode(cipherText.substring(CIPHER_PREFIX.length()));
            int minLength = IV_BYTES + 16;
            if (joined.length < minLength) {
                throw new IllegalStateException("ciphertext shorter than IV+tag");
            }
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(joined, 0, iv, 0, IV_BYTES);
            byte[] ct = new byte[joined.length - IV_BYTES];
            System.arraycopy(joined, IV_BYTES, ct, 0, ct.length);
            Cipher c = Cipher.getInstance(CIPHER_ALGO);
            c.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed (tampered ciphertext or wrong key)", e);
        }
    }

    private static byte[] sha256(byte[] in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(in);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 missing from JCE -- JVM is broken", e);
        }
    }
}
