package com.auth.center;

import com.auth.center.oauth.OAuthProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Auth Center 认证中心启动类.
 *
 * 作为集中式 CAS 认证服务器的入口，负责用户认证、权限集管理和跨系统能力注册。
 */
@SpringBootApplication
@MapperScan({"com.auth.center.mapper", "com.auth.center.oauth"})
@EnableConfigurationProperties(OAuthProperties.class)
public class AuthCenterApplication {

    /**
     * 应用启动入口.
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AuthCenterApplication.class, args);
    }
}
