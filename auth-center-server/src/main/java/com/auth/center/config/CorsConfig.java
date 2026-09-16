package com.auth.center.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 跨域配置.
 *
 * 开发环境默认允许所有来源，生产环境通过配置项 {@code auth.cors.allowed-origins} 限制。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许的跨域来源，多个用逗号分隔，默认为 {@code *} */
    @Value("${auth.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册全局 CORS 映射规则.
     *
     * 通配符来源 ({@code *}) 时禁止携带凭据（防凭据泄露至任意域）。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        boolean isWildcard = origins.length == 1 && "*".equals(origins[0].trim());
        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(!isWildcard)
                .maxAge(3600);
    }
}
