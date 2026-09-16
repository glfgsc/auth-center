package com.auth.sdk;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Auth Center 统一认证过滤器 -- 支持网关 Header 和 JWT 直连两种模式.
 *
 * 认证优先级:先看网关注入的 {@code X-User-Id} Header(已由网关验证 JWT),否则用 {@code Authorization: Bearer} JWT
 * 直接验证(独立访问模式)。
 *
 * 认证成功后将 {@link AuthUserDetails} 设入 {@link SecurityContextHolder}. 若两种认证方式均不满足,则不设置认证信息,由
 * Spring Security 后续处理 (通常返回 401 Unauthorized).
 *
 * 线程安全:本过滤器为无状态对象,可安全注册为 Spring Bean 单例.
 */
public class AuthHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthHeaderFilter.class);

    /** Authorization Header 名称 */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /** Bearer Token 前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** Bearer Token 前缀长度 */
    private static final int BEARER_PREFIX_LENGTH = BEARER_PREFIX.length();

    /**
     * JWT 验证器,可为 null (仅网关模式,不支持直连 JWT 验证).
     *
     * 当服务始终部署在网关之后时可传 null, 此时仅从网关 Header 构建认证信息.
     */
    private final JwtValidator jwtValidator;

    /**
     * 当前系统编码 (如 bi / tracking), 可为 null.
     *
     * JWT 直连模式下用于把主体权限收窄到本系统 + global, 实现按系统隔离授权;为 null 时退化为旧的扁平权限解析。
     */
    private final String systemCode;

    /**
     * 构造认证过滤器 (不指定系统,兼容旧调用).
     *
     * @param jwtValidator JWT 验证器;传 null 表示仅支持网关模式,不进行 JWT 直连验证
     */
    public AuthHeaderFilter(JwtValidator jwtValidator) {
        this(jwtValidator, null);
    }

    /**
     * 构造认证过滤器,指定当前系统编码.
     *
     * @param jwtValidator JWT 验证器;传 null 表示仅支持网关模式
     * @param systemCode 当前系统编码 (如 tracking); JWT 直连时据此按系统收窄权限
     */
    public AuthHeaderFilter(JwtValidator jwtValidator, String systemCode) {
        this.jwtValidator = jwtValidator;
        this.systemCode = systemCode;
    }

    /**
     * 过滤器核心逻辑:按优先级尝试从网关 Header 或 JWT 构建认证信息.
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param filterChain 过滤器链
     * @throws ServletException Servlet 异常
     * @throws IOException IO 异常
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 若 SecurityContext 中已有认证信息,跳过处理
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        AuthUserDetails userDetails = null;

        // 优先级 1: 网关注入的 X-User-Id Header
        String userIdHeader = request.getHeader(AuthUserDetails.HEADER_USER_ID);
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            userDetails = buildFromHeaders(request);
        }

        // 优先级 2: Authorization: Bearer JWT
        if (userDetails == null) {
            String authHeader = request.getHeader(HEADER_AUTHORIZATION);
            if (authHeader != null
                    && authHeader.startsWith(BEARER_PREFIX)
                    && jwtValidator != null) {
                String token = authHeader.substring(BEARER_PREFIX_LENGTH);
                userDetails = buildFromJwt(token);
            }
        }

        // 设置认证信息到 SecurityContext
        if (userDetails != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从网关注入的 HTTP Header 构建用户主体.
     *
     * @param request HTTP 请求
     * @return 用户主体,或 null (Header 解析失败时)
     */
    private AuthUserDetails buildFromHeaders(HttpServletRequest request) {
        try {
            AuthUserDetails details = AuthUserDetails.fromGatewayHeaders(request);
            log.debug(
                    "从网关 Header 构建认证信息: userId={}, username={}",
                    details.getUserId(),
                    details.getUsername());
            return details;
        } catch (Exception e) {
            log.warn("解析网关 Header 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 JWT Bearer Token 验证并构建用户主体.
     *
     * @param token JWT 字符串 (不含 "Bearer " 前缀)
     * @return 用户主体,或 null (JWT 验证失败时)
     */
    private AuthUserDetails buildFromJwt(String token) {
        try {
            AuthUserDetails details =
                    systemCode != null
                            ? jwtValidator.toUserDetails(token, systemCode)
                            : jwtValidator.toUserDetails(token);
            log.debug(
                    "从 JWT 构建认证信息: userId={}, username={}",
                    details.getUserId(),
                    details.getUsername());
            return details;
        } catch (Exception e) {
            log.warn("JWT 验证失败: {}", e.getMessage());
            return null;
        }
    }
}
