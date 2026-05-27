package com.auth.center.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * JWT 认证过滤器 -- 从 Authorization 请求头解析并验证 JWT.
 *
 * <p>验证成功后将用户身份信息写入 Spring Security 上下文，
 * 使 {@code .authenticated()} 规则通过。验证失败或无 token 时
 * 不设置上下文，由后续 Spring Security 链决定 401。</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Authorization 请求头名称 */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀（含空格） */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ITokenBlacklistService tokenBlacklistService;

    /**
     * 构造函数，注入依赖.
     *
     * @param jwtService            JWT 签发/验证服务
     * @param tokenBlacklistService Token 黑名单服务
     */
    public JwtAuthenticationFilter(JwtService jwtService,
                                   ITokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseToken(token);

            // 检查 token 是否已被吊销
            String jti = claims.getId();
            if (jti != null && tokenBlacklistService.isRevoked(jti)) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = claims.getSubject();
            Long userId = claims.get("userId", Long.class);
            String permissionSet = claims.get("permissionSet", String.class);

            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" +
                            (permissionSet != null ? permissionSet.toUpperCase() : "USER"))
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            authentication.setDetails(Map.of(
                    "userId", userId != null ? userId : 0L,
                    "permissionSet", permissionSet != null ? permissionSet : ""
            ));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            log.debug("JWT 认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }
}
