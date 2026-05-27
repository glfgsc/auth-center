package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.security.JwtService;
import com.auth.center.service.IAuthService;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证控制器 -- 提供登录、注册、用户信息查询、权限查询和注销端点.
 *
 * <p>登录和注册为公开端点（permitAll），其余端点需携带有效 JWT。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final IAuthService authService;
    private final JwtService jwtService;

    /**
     * 构造函数，注入认证服务和 JWT 服务.
     *
     * @param authService 认证服务
     * @param jwtService  JWT 服务
     */
    public AuthController(IAuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    /**
     * 用户登录.
     *
     * @param params 请求体，需包含 username 和 password
     * @return 包含 token、用户信息、权限集和能力列表的结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");

        if (username == null || username.isBlank()) {
            return Result.fail("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return Result.fail("密码不能为空");
        }

        return authService.login(username, password);
    }

    /**
     * 用户注册.
     *
     * @param params 请求体，需包含 username、password，可选 nickname
     * @return 包含 token、用户信息、权限集和能力列表的结果
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        String nickname = params.get("nickname");

        if (username == null || username.isBlank()) {
            return Result.fail("用户名不能为空");
        }
        if (password == null || password.isBlank()) {
            return Result.fail("密码不能为空");
        }

        return authService.register(username, password, nickname);
    }

    /**
     * 获取当前用户基本信息.
     *
     * @param authorization Authorization 请求头（Bearer token）
     * @return 包含用户基本信息的结果
     */
    @GetMapping("/info")
    public Result<Map<String, Object>> getUserInfo(
            @RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return Result.fail("缺少有效的认证令牌");
        }
        return authService.getUserInfo(token);
    }

    /**
     * 获取当前用户的权限集信息.
     *
     * @param authorization Authorization 请求头（Bearer token）
     * @return 包含权限集编码和能力列表的结果
     */
    @GetMapping("/permission-set")
    public Result<Map<String, Object>> getPermissionSet(
            @RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return Result.fail("缺少有效的认证令牌");
        }
        return authService.getPermissionInfo(token);
    }

    /**
     * 用户注销 -- 将当前 JWT 加入黑名单.
     *
     * @param authorization Authorization 请求头（Bearer token）
     * @return 注销结果
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String authorization) {
        String token = extractToken(authorization);
        if (token == null) {
            return Result.fail("缺少有效的认证令牌");
        }

        try {
            Claims claims = jwtService.parseToken(token);
            String jti = claims.getId();
            long expiryMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (expiryMs > 0) {
                authService.logout(jti, expiryMs);
            }
        } catch (Exception e) {
            log.warn("注销时解析 token 失败: {}", e.getMessage());
            return Result.fail("令牌无效或已过期");
        }

        return Result.ok();
    }

    /**
     * 从 Authorization 请求头中提取 JWT 字符串.
     *
     * @param authorization Authorization 请求头值
     * @return JWT 字符串，格式不正确时返回 {@code null}
     */
    private String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }
}
