package com.auth.center.security.crypto;

/**
 * 静态凭据的对称加解密 -- 保护需要服务端可恢复的密文(webhook 地址、加签密钥、SMTP 口令等).
 *
 * 与 {@link com.auth.center.security.LoginCryptoService} 划清界限:那个是登录密码的传输加密,密钥对进程内生成、重启即轮换;
 * 本接口保护的是落库的东西,重启后必须还解得开,故密钥来自外部配置.
 *
 * 用户账户密码不得走本接口 -- 那类东西只能单向哈希,见 auth_user 的 BCrypt.
 */
public interface ICryptoService {

    /**
     * 判断值是否已是密文.
     *
     * @param value 待检测字符串
     * @return 携带密文前缀即为 true
     */
    boolean isEncrypted(String value);

    /**
     * 加密明文. null / 空串原样透传,已加密的值不会被二次加密.
     *
     * @param plaintext 待加密的明文
     * @return 密文
     */
    String encrypt(String plaintext);

    /**
     * 解密密文. 无密文前缀者视为遗留明文原样返回,以支持渐进迁移.
     *
     * @param cipherText 待解密的字符串
     * @return 明文
     */
    String decrypt(String cipherText);
}
