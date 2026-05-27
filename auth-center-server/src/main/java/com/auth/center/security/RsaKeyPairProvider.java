package com.auth.center.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RSA 密钥对提供器 -- 生成或加载 RSA-2048 密钥对, 用于 JWT 签名与验签.
 *
 * <p>启动时检查配置路径下是否存在 PEM 文件:
 * <ul>
 *     <li>若存在 {@code private.pem} 和 {@code public.pem}, 则从文件加载</li>
 *     <li>若不存在, 生成新的 RSA-2048 密钥对并写入 PEM 文件</li>
 * </ul>
 */
@Component
public class RsaKeyPairProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyPairProvider.class);

    /** RSA 密钥长度 (bits) */
    private static final int RSA_KEY_SIZE = 2048;

    /** 私钥 PEM 文件名 */
    private static final String PRIVATE_KEY_FILE = "private.pem";

    /** 公钥 PEM 文件名 */
    private static final String PUBLIC_KEY_FILE = "public.pem";

    /** PEM 私钥头标记 */
    private static final String PEM_PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";

    /** PEM 私钥尾标记 */
    private static final String PEM_PRIVATE_END = "-----END PRIVATE KEY-----";

    /** PEM 公钥头标记 */
    private static final String PEM_PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";

    /** PEM 公钥尾标记 */
    private static final String PEM_PUBLIC_END = "-----END PUBLIC KEY-----";

    /** PEM Base64 每行最大字符数 */
    private static final int PEM_LINE_LENGTH = 64;

    @Value("${auth.jwt.rsa-key-dir:./data/keys}")
    private String keyDir;

    private KeyPair keyPair;

    /**
     * 启动时初始化密钥对: 优先从文件加载, 不存在则自动生成.
     *
     * @throws RuntimeException 密钥加载或生成失败时抛出
     */
    @PostConstruct
    public void init() {
        Path dir = Paths.get(keyDir);
        Path privatePath = dir.resolve(PRIVATE_KEY_FILE);
        Path publicPath = dir.resolve(PUBLIC_KEY_FILE);

        try {
            if (Files.exists(privatePath) && Files.exists(publicPath)) {
                // 从已有 PEM 文件加载
                keyPair = loadKeyPair(privatePath, publicPath);
                log.info("已从 {} 加载 RSA 密钥对", dir.toAbsolutePath());
            } else {
                // 生成新的密钥对并持久化
                keyPair = generateKeyPair();
                Files.createDirectories(dir);
                savePrivateKey(privatePath, keyPair.getPrivate().getEncoded());
                savePublicKey(publicPath, keyPair.getPublic().getEncoded());
                log.info("已生成 RSA-{} 密钥对并保存至 {}", RSA_KEY_SIZE, dir.toAbsolutePath());
            }
        } catch (Exception e) {
            throw new RuntimeException("RSA 密钥对初始化失败", e);
        }
    }

    /**
     * 获取完整的 RSA 密钥对.
     *
     * @return RSA 密钥对
     */
    public KeyPair getKeyPair() {
        return keyPair;
    }

    /**
     * 获取 RSA 公钥.
     *
     * @return RSA 公钥
     */
    public RSAPublicKey getPublicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /**
     * 获取 RSA 私钥.
     *
     * @return RSA 私钥
     */
    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    /**
     * 返回 JWK 格式的公钥信息, 用于 JWKS 端点.
     *
     * <p>返回的 Map 包含以下标准 JWK 字段:
     * <ul>
     *     <li>{@code kty} - 密钥类型, 固定为 "RSA"</li>
     *     <li>{@code use} - 用途, 固定为 "sig"</li>
     *     <li>{@code alg} - 算法, 固定为 "RS256"</li>
     *     <li>{@code kid} - 密钥 ID (公钥 SHA-256 指纹的 Base64url 编码)</li>
     *     <li>{@code n} - RSA 模数 (Base64url 编码)</li>
     *     <li>{@code e} - RSA 指数 (Base64url 编码)</li>
     * </ul>
     *
     * @return JWK 格式的公钥描述
     */
    public Map<String, Object> getJwk() {
        RSAPublicKey pub = getPublicKey();
        Base64.Encoder urlEncoder = Base64.getUrlEncoder().withoutPadding();

        Map<String, Object> jwk = new LinkedHashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", computeKid(pub));
        jwk.put("n", urlEncoder.encodeToString(toUnsignedBytes(pub.getModulus())));
        jwk.put("e", urlEncoder.encodeToString(toUnsignedBytes(pub.getPublicExponent())));
        return jwk;
    }

    // ======================== 内部方法 ========================

    /**
     * 生成 RSA-2048 密钥对.
     *
     * @return 新生成的密钥对
     * @throws NoSuchAlgorithmException 当 JVM 不支持 RSA 算法时抛出
     */
    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }

    /**
     * 从 PEM 文件加载密钥对.
     *
     * @param privatePath 私钥 PEM 文件路径
     * @param publicPath  公钥 PEM 文件路径
     * @return 加载的密钥对
     * @throws IOException             文件读取失败时抛出
     * @throws NoSuchAlgorithmException 不支持 RSA 算法时抛出
     * @throws InvalidKeySpecException  密钥格式无效时抛出
     */
    private KeyPair loadKeyPair(Path privatePath, Path publicPath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");

        // 加载私钥
        byte[] privateBytes = decodePem(Files.readString(privatePath),
                PEM_PRIVATE_BEGIN, PEM_PRIVATE_END);
        RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory
                .generatePrivate(new PKCS8EncodedKeySpec(privateBytes));

        // 加载公钥
        byte[] publicBytes = decodePem(Files.readString(publicPath),
                PEM_PUBLIC_BEGIN, PEM_PUBLIC_END);
        RSAPublicKey publicKey = (RSAPublicKey) keyFactory
                .generatePublic(new X509EncodedKeySpec(publicBytes));

        return new KeyPair(publicKey, privateKey);
    }

    /**
     * 将 DER 编码的私钥写入 PEM 文件.
     *
     * @param path     目标文件路径
     * @param derBytes DER 编码的私钥字节
     * @throws IOException 文件写入失败时抛出
     */
    private void savePrivateKey(Path path, byte[] derBytes) throws IOException {
        String pem = encodePem(derBytes, PEM_PRIVATE_BEGIN, PEM_PRIVATE_END);
        Files.writeString(path, pem);
    }

    /**
     * 将 DER 编码的公钥写入 PEM 文件.
     *
     * @param path     目标文件路径
     * @param derBytes DER 编码的公钥字节
     * @throws IOException 文件写入失败时抛出
     */
    private void savePublicKey(Path path, byte[] derBytes) throws IOException {
        String pem = encodePem(derBytes, PEM_PUBLIC_BEGIN, PEM_PUBLIC_END);
        Files.writeString(path, pem);
    }

    /**
     * 将 DER 字节编码为 PEM 格式字符串.
     *
     * @param derBytes DER 编码字节
     * @param header   PEM 头标记
     * @param footer   PEM 尾标记
     * @return PEM 格式字符串
     */
    private String encodePem(byte[] derBytes, String header, String footer) {
        String base64 = Base64.getMimeEncoder(PEM_LINE_LENGTH, "\n".getBytes()).encodeToString(derBytes);
        return header + "\n" + base64 + "\n" + footer + "\n";
    }

    /**
     * 从 PEM 字符串解码为 DER 字节.
     *
     * @param pem    PEM 格式字符串
     * @param header PEM 头标记
     * @param footer PEM 尾标记
     * @return DER 编码字节
     */
    private byte[] decodePem(String pem, String header, String footer) {
        String base64 = pem
                .replace(header, "")
                .replace(footer, "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(base64);
    }

    /**
     * 计算公钥的 kid (SHA-256 指纹的 Base64url 编码).
     *
     * @param publicKey RSA 公钥
     * @return Base64url 编码的指纹字符串
     */
    private String computeKid(RSAPublicKey publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(publicKey.getEncoded());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 将 BigInteger 转为无符号字节数组 (去除前导零字节).
     *
     * @param bigInt BigInteger 值
     * @return 无符号字节数组
     */
    private byte[] toUnsignedBytes(java.math.BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        // BigInteger.toByteArray() 可能包含前导零字节用于表示正数符号
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }
}
