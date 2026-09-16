package com.auth.center.security.crypto;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * 把 Spring 管的 {@link ICryptoService} 递给由 MyBatis 自行实例化的 {@link EncryptedStringTypeHandler} --
 * TypeHandler 不在 Spring 容器里,拿不到依赖注入.
 *
 * {@code @Lazy(false)} 不是装饰:本 Bean 只靠 {@code @PostConstruct} 产生副作用,没有任何其他 Bean 依赖它. 在开了 {@code
 * spring.main.lazy-initialization=true} 的部署里,无人依赖的 Bean 根本不会被实例化,桥接便永不执行 -- 于是所有加密列静默
 * passthrough, 密文原样读出,且全程无报错.
 */
@Component
@Lazy(false)
public class CryptoServiceTypeHandlerBridge {

    private final ICryptoService cryptoService;

    /**
     * 构造方法.
     *
     * @param cryptoService Spring 管理的加解密服务
     */
    public CryptoServiceTypeHandlerBridge(ICryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    /** 启动阶段完成桥接,确保首条 SQL 之前就位. */
    @PostConstruct
    void install() {
        EncryptedStringTypeHandler.install(cryptoService);
    }
}
