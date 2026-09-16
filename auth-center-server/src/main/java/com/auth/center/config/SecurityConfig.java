package com.auth.center.config;

import com.auth.center.security.JwtAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置 -- 定义认证过滤链和密码编码器.
 *
 * 采用无状态（Stateless）会话策略，关闭 CSRF（纯 API + CAS 仅用 cookie 传递 TGT）。公开端点包括登录/注册、CAS 协议、JWKS
 * 和健康检查，管理端点需要认证。
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
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * 构造函数，注入依赖.
     *
     * @param objectMapper Jackson ObjectMapper
     * @param jwtAuthenticationFilter JWT 认证过滤器
     */
    public SecurityConfig(
            ObjectMapper objectMapper, JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.objectMapper = objectMapper;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * 安全过滤链配置.
     *
     *   - CSRF 关闭（无状态 API）；会话策略 STATELESS
     *   - 公开路径: /api/auth/login, /cas/, /.well-known/, /actuator/**, /error
     *   - 其余请求（含 /api/admin/**）需要认证
     *   - 401/403 返回自定义 JSON 响应
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
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 请求授权规则
                .authorizeHttpRequests(
                        auth ->
                                auth
                                        // 公开端点:登录
                                        .requestMatchers("/api/auth/login")
                                        .permitAll()
                                        // 公开端点:登录密码传输加密公钥（登录前取公钥加密密码）
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/auth/public-key")
                                        .permitAll()
                                        // 公开端点: CAS 协议
                                        .requestMatchers("/cas/**")
                                        .permitAll()
                                        // 公开端点: JWKS
                                        .requestMatchers("/.well-known/**")
                                        .permitAll()
                                        // OAuth 2.1 Authorization Server 协议端点
                                        .requestMatchers("/oauth2/**")
                                        .permitAll()
                                        .requestMatchers("/api/auth/oauth/public-config")
                                        .permitAll()
                                        // 公开端点: Actuator 健康检查
                                        .requestMatchers("/actuator/**")
                                        .permitAll()
                                        // 公开端点:错误页
                                        .requestMatchers("/error")
                                        .permitAll()
                                        // 公开端点:管理控制台 SPA 静态资源（认证由 SPA 内 JWT 拦截器处理）
                                        .requestMatchers("/admin/**")
                                        .permitAll()
                                        // 公开端点:能力查询（前端/子系统读取能力列表）
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.GET,
                                                "/api/auth/capabilities")
                                        .permitAll()
                                        // 服务间端点:能力注册（子系统启动期调用，此刻无用户上下文，故用户级认证在此放行；
                                        // 端点内常量时间校验 X-Internal-Service-Token fail-closed，网关剥除外部伪造头。
                                        // 注册是「先删后插」的全量替换，仅靠 systemCode 白名单挡不住——那是调用方自填的值）。
                                        .requestMatchers(
                                                org.springframework.http.HttpMethod.POST,
                                                "/api/auth/capabilities/register")
                                        .permitAll()
                                        // 公开端点: SSO 公开配置（登录页渲染使用）
                                        .requestMatchers("/api/auth/sso/public-config")
                                        .permitAll()
                                        // 公开端点:单点登出跳转计算（登出后即便 token 已失效也应可调用）
                                        .requestMatchers("/api/auth/sso/cas/logout-redirect")
                                        .permitAll()
                                        // 公开端点: SPA 便捷验票
                                        .requestMatchers("/api/auth/cas/ticket-validate")
                                        .permitAll()
                                        // 公开端点: token 刷新（access token 已过期时调用，自身用 refresh token 校验）
                                        .requestMatchers("/api/auth/refresh")
                                        .permitAll()
                                        // 公开端点:用户组内部查询（服务间调用，Docker 网络隔离）
                                        .requestMatchers("/api/auth/groups/internal/**")
                                        .permitAll()
                                        // 服务间端点:嵌入会话铸造（bi-core-workspace 直连调用，端点内自校验
                                        // X-Internal-Service-Token，网关剥除外部伪造头，故公网不可达）
                                        .requestMatchers("/api/auth/embed/internal/**")
                                        .permitAll()
                                        // 服务间端点:审计汇入（BI/agent 直连推送,端点内自校验
                                        // X-Internal-Service-Token,网关剥除外部伪造头）
                                        .requestMatchers("/api/auth/audit/internal/**")
                                        .permitAll()
                                        // 服务间端点:平台配置内部读取（子系统取自己系统的中心配置,端点内自校验内部令牌）
                                        .requestMatchers("/api/auth/config/internal/**")
                                        .permitAll()
                                        // 服务间端点:会话跨系统活跃度上报（网关/tracking 直连推送,端点内自校验
                                        // X-Internal-Service-Token）
                                        .requestMatchers("/api/auth/session/internal/**")
                                        .permitAll()
                                        // 服务间端点:按 userId 权威解析权限集（bi-core 数据授权裁决判「是不是管理员」,
                                        // 不能采信调用方自报的 authorities;端点内自校验 X-Internal-Service-Token）
                                        .requestMatchers("/api/auth/permission-sets/internal/**")
                                        .permitAll()
                                        // 服务间端点:用户目录（观测台把 create_user 显示成称呼,只回
                                        // id/username/nickname;端点内自校验 X-Internal-Service-Token）
                                        .requestMatchers("/api/auth/users/internal/**")
                                        .permitAll()
                                        // SSO 管理端点:需要认证
                                        .requestMatchers("/api/auth/sso/admin/**")
                                        .authenticated()
                                        // 管理端点:需要认证
                                        .requestMatchers("/api/auth/admin/**")
                                        .authenticated()
                                        // 其余请求:需要认证
                                        .anyRequest()
                                        .authenticated())

                // JWT 认证过滤器 — 解析 Authorization 头，设置 SecurityContext
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // 自定义 401 响应
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, authException) -> {
                                                    response.setStatus(SC_UNAUTHORIZED);
                                                    response.setContentType(JSON_CONTENT_TYPE);
                                                    Map<String, Object> body =
                                                            Map.of(
                                                                    "code",
                                                                    SC_UNAUTHORIZED,
                                                                    "message",
                                                                    "未认证，请先登录");
                                                    response.getWriter()
                                                            .write(
                                                                    objectMapper.writeValueAsString(
                                                                            body));
                                                })
                                        // 自定义 403 响应
                                        .accessDeniedHandler(
                                                (request, response, accessDeniedException) -> {
                                                    response.setStatus(SC_FORBIDDEN);
                                                    response.setContentType(JSON_CONTENT_TYPE);
                                                    Map<String, Object> body =
                                                            Map.of(
                                                                    "code",
                                                                    SC_FORBIDDEN,
                                                                    "message",
                                                                    "权限不足");
                                                    response.getWriter()
                                                            .write(
                                                                    objectMapper.writeValueAsString(
                                                                            body));
                                                }));

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
