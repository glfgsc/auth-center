package com.auth.center.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 管理控制台 SPA 静态资源配置。
 *
 * auth-center-admin 前端构建产物放在 {@code classpath:/static/admin/}， Spring Boot 在此路径下服务静态资源。对于 SPA
 * history 模式路由（如 {@code /admin/sso}），浏览器刷新时服务端找不到对应文件，需 fallback 到 {@code admin/index.html}
 * 由前端路由接管。
 */
@Configuration
public class AdminSpaConfig implements WebMvcConfigurer {

    /** SPA 入口页路径。 */
    private static final String INDEX_HTML = "static/admin/index.html";

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // /admin 和 /admin/ 不被 /admin/** 资源模式匹配，需手动 forward 到 index.html
        registry.addViewController("/admin").setViewName("forward:/admin/index.html");
        registry.addViewController("/admin/").setViewName("forward:/admin/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/admin/**")
                .addResourceLocations("classpath:/static/admin/")
                .resourceChain(true)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws IOException {
                                Resource resource = location.createRelative(resourcePath);
                                // 静态资源存在则直接返回，否则 fallback 到 index.html（SPA 路由）
                                if (resource.exists() && resource.isReadable()) {
                                    return resource;
                                }
                                return new ClassPathResource(INDEX_HTML);
                            }
                        });
    }
}
