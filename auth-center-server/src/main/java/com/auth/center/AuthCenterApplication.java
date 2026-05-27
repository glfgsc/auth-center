package com.auth.center;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth Center 认证中心启动类.
 *
 * <p>作为集中式 CAS 认证服务器的入口，负责用户认证、权限集管理和跨系统能力注册。</p>
 */
@SpringBootApplication
@MapperScan("com.auth.center.mapper")
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
