package com.auth.center.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT 认证过滤器 -- 从 Authorization 请求头解析并验证 JWT.
 *
 * 验证成功后将用户身份信息写入 Spring Security 上下文，使 {@code .authenticated()} 规则通过。验证失败或无 token 时不设置上下文，由后续
 * Spring Security 链决定 401。
 *
 * 写进上下文的 authorities 有两类：
 *
 *   - {@code ROLE_<权限集编码大写>} —— 供 {@link AuthPerm#isAdmin()} 判定「是不是 admin 权限集」；
 *   - 能力码本身（如 {@code admin:manage_user}）—— 供 {@link AuthPerm#hasCapability(String)}
 *       做细粒度判定。
 *
 * 能力码取 {@code capabilities} claim（向后兼容标量）与 {@code systemPermissions} claim（按系统的 {@code {ps,
 * caps[]}}）的并集 —— 取并集是因为同一个能力可能只经某个子系统绑定授予，只读标量会漏掉它。两个 claim 都由 {@link
 * SystemPermissionResolver} 在签发时写入（登录与 CAS 两条签发路径一致）。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** Authorization 请求头名称 */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Bearer 前缀（含空格） */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 向后兼容的能力码标量 claim（JSON 数组字符串） */
    private static final String CLAIM_CAPABILITIES = "capabilities";

    /** 按系统权限 claim（system -> {ps, caps[]}） */
    private static final String CLAIM_SYSTEM_PERMISSIONS = "systemPermissions";

    /** {@code systemPermissions} 条目里的能力码字段名 */
    private static final String FIELD_CAPS = "caps";

    private final JwtService jwtService;
    private final ITokenBlacklistService tokenBlacklistService;

    /**
     * 构造函数，注入依赖.
     *
     * @param jwtService JWT 签发/验证服务
     * @param tokenBlacklistService Token 黑名单服务
     */
    public JwtAuthenticationFilter(
            JwtService jwtService, ITokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /** {@inheritDoc} */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtService.parseToken(token);

            // 检查 token 是否已被吊销 —— 两道闸都要过:按 sid 覆盖该会话签发过的全部令牌,按 jti 只钉注册表记着的当前那枚。
            // 本过滤器不是可选的第二道防线:/api/auth/** 在 ingress 里直连本服务、不经网关,漏掉会话级那道,
            // 被顶下线的设备凭续期前的旧令牌照样能读用户信息。
            String jti = claims.getId();
            String sessionId = jwtService.sessionIdOf(claims);
            boolean revoked =
                    (jti != null && tokenBlacklistService.isRevoked(jti))
                            || (sessionId != null
                                    && tokenBlacklistService.sessionRevokeReason(sessionId)
                                            != null);
            if (revoked) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = claims.getSubject();
            Long userId = claims.get("userId", Long.class);
            String permissionSet = claims.get("permissionSet", String.class);

            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(
                    new SimpleGrantedAuthority(
                            "ROLE_"
                                    + (permissionSet != null
                                            ? permissionSet.toUpperCase()
                                            : "USER")));
            for (String cap : extractCapabilities(claims)) {
                authorities.add(new SimpleGrantedAuthority(cap));
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(username, null, authorities);
            authentication.setDetails(
                    Map.of(
                            "userId", userId != null ? userId : 0L,
                            "permissionSet", permissionSet != null ? permissionSet : ""));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (JwtException e) {
            log.debug("JWT 认证失败: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从 claim 里取该用户持有的能力码全集.
     *
     * 并集口径见类注释。claim 形态异常（类型不符 / 结构不对）时按「该来源没有能力码」跳过，不抛异常 —— 认证本身已由签名校验保证，能力解析失败只应导致细粒度端点
     * 403，而不是整条链 500。
     *
     * @param claims 已验签的 JWT claims
     * @return 能力码集合（可能为空，不为 null）
     */
    private static Set<String> extractCapabilities(Claims claims) {
        Set<String> caps = new LinkedHashSet<>();

        caps.addAll(
                SystemPermissionResolver.parseCaps(claims.get(CLAIM_CAPABILITIES, String.class)));

        Object systemPermissions = claims.get(CLAIM_SYSTEM_PERMISSIONS);
        if (systemPermissions instanceof Map<?, ?> bySystem) {
            for (Object entry : bySystem.values()) {
                if (!(entry instanceof Map<?, ?> detail)) {
                    continue;
                }
                if (detail.get(FIELD_CAPS) instanceof Iterable<?> list) {
                    for (Object cap : list) {
                        if (cap instanceof String s && !s.isBlank()) {
                            caps.add(s.trim());
                        }
                    }
                }
            }
        }
        return caps;
    }
}
