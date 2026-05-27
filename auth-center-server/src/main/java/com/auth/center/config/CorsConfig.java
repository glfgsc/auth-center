package com.auth.center.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 跨域配置.
 *
 * <p>开发环境默认允许所有来源，生产环境通过配置项 {@code auth.cors.allowed-origins} 限制。</p>
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /** 允许的跨域来源，多个用逗号分隔，默认为 {@code *} */
    @Value("${auth.cors.allowed-origins:*}")
    private String allowedOrigins;

    /**
     * 注册全局 CORS 映射规则.
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
