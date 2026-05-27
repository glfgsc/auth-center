package com.auth.center.controller;

import com.auth.center.security.RsaKeyPairProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * JWKS 端点控制器 -- 发布 RSA 公钥供外部服务验证 JWT 签名.
 *
 * <p>遵循 RFC 7517 (JSON Web Key Set) 标准，通过
 * {@code /.well-known/jwks.json} 路径暴露公钥信息。
 * 下游服务可定期拉取该端点获取最新公钥，无需硬编码密钥。</p>
 */
@RestController
public class JwksController {

    private final RsaKeyPairProvider rsaKeyPairProvider;

    /**
     * 构造函数，注入 RSA 密钥对提供器.
     *
     * @param rsaKeyPairProvider RSA 密钥对提供器
     */
    public JwksController(RsaKeyPairProvider rsaKeyPairProvider) {
        this.rsaKeyPairProvider = rsaKeyPairProvider;
    }

    /**
     * 返回 JWKS 格式的公钥集合.
     *
     * <p>响应格式:
     * <pre>
     * {
     *   "keys": [
     *     {
     *       "kty": "RSA",
     *       "use": "sig",
     *       "alg": "RS256",
     *       "kid": "...",
     *       "n": "...",
     *       "e": "..."
     *     }
     *   ]
     * }
     * </pre>
     *
     * @return JWKS JSON 对象
     */
    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return Map.of("keys", List.of(rsaKeyPairProvider.getJwk()));
    }
}
