package com.auth.center.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Map;

/**
 * Spring Security 安全配置 -- 定义认证过滤链和密码编码器.
 *
 * <p>采用无状态（Stateless）会话策略，关闭 CSRF（纯 API + CAS 仅用 cookie 传递 TGT）。
 * 公开端点包括登录/注册、CAS 协议、JWKS 和健康检查，管理端点需要认证。</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** 自定义错误响应的 Content-Type */
    private static final String JSON_CONTENT_TYPE = "application/json;charset=UTF-8";

    /** HTTP 401 未授权状态码 */
    private static final int SC_UNAUTHORIZED = HttpServletResponse.SC_UNAUTHORIZED;

    /** HTTP 403 禁止访问状态码 */
    private static final int SC_FORBIDDEN = HttpServletResponse.SC_FORBIDDEN;

    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入 JSON 序列化器.
     *
     * @param objectMapper Jackson ObjectMapper
     */
    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 安全过滤链配置.
     *
     * <p>规则:
     * <ul>
     *     <li>CSRF 关闭（无状态 API）</li>
     *     <li>会话策略: STATELESS</li>
     *     <li>公开路径: /api/auth/login, /api/auth/register, /cas/**, /.well-known/**, /actuator/**, /error</li>
     *     <li>/api/admin/** 需要认证</li>
     *     <li>其余请求需要认证</li>
     *     <li>401/403 返回自定义 JSON 响应</li>
     * </ul>
     *
     * @param http HttpSecurity 构建器
     * @return 构建好的 SecurityFilterChain
     * @throws Exception 配置异常时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF（无状态 API + CAS 仅用 cookie 传递 TGT）
            .csrf(csrf -> csrf.disable())

            // 无状态会话
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开端点: 登录、注册
                .requestMatchers("/api/auth/login", "/api/auth/register").permitAll()
                // 公开端点: CAS 协议
                .requestMatchers("/cas/**").permitAll()
                // 公开端点: JWKS
                .requestMatchers("/.well-known/**").permitAll()
                // 公开端点: Actuator 健康检查
                .requestMatchers("/actuator/**").permitAll()
                // 公开端点: 错误页
                .requestMatchers("/error").permitAll()
                // 公开端点: 能力注册（子系统启动时调用）
                .requestMatchers("/api/capabilities/**").permitAll()
                // 管理端点: 需要认证
                .requestMatchers("/api/admin/**").authenticated()
                // 其余请求: 需要认证
                .anyRequest().authenticated()
            )

            // 自定义 401 响应
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(SC_UNAUTHORIZED);
                    response.setContentType(JSON_CONTENT_TYPE);
                    Map<String, Object> body = Map.of(
                            "code", SC_UNAUTHORIZED,
                            "message", "未认证，请先登录"
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
                // 自定义 403 响应
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(SC_FORBIDDEN);
                    response.setContentType(JSON_CONTENT_TYPE);
                    Map<String, Object> body = Map.of(
                            "code", SC_FORBIDDEN,
                            "message", "权限不足"
                    );
                    response.getWriter().write(objectMapper.writeValueAsString(body));
                })
            );

        return http.build();
    }

    /**
     * 密码编码器 -- 使用 BCrypt 算法.
     *
     * @return BCryptPasswordEncoder 实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
