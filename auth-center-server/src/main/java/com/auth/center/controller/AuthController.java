package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.controller.request.LoginRequest;
import com.auth.center.controller.request.RefreshTokenRequest;
import com.auth.center.security.JwtService;
import com.auth.center.security.LoginCryptoService;
import com.auth.center.service.IAuthService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证控制器 -- 提供登录、用户信息查询、权限查询和注销端点.
 *
 * 登录为公开端点（permitAll），其余端点需携带有效 JWT。
 */
@RestController
@Validated
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Authorization 请求头前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 登录密码传输载荷最长有效期(ms) —— 从取公钥(server time)到提交登录的窗口，超期按抗重放拒绝。 */
    private static final long LOGIN_PAYLOAD_MAX_AGE_MS = 300_000L;

    /** 允许的时钟提前量(ms) —— serverTime 由服务端签发理论不超前，留少量容差防御。 */
    private static final long LOGIN_PAYLOAD_MAX_SKEW_MS = 60_000L;

    private final IAuthService authService;
    private final JwtService jwtService;
    private final LoginCryptoService loginCryptoService;

    /**
     * 构造函数，注入认证服务、JWT 服务与登录密码解密服务.
     *
     * @param authService 认证服务
     * @param jwtService JWT 服务
     * @param loginCryptoService 登录密码传输解密服务
     */
    public AuthController(
            IAuthService authService,
            JwtService jwtService,
            LoginCryptoService loginCryptoService) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.loginCryptoService = loginCryptoService;
    }

    /**
     * 获取登录密码传输加密公钥.
     *
     * 前端登录前取此公钥,用 RSA-OAEP(SHA-256) 加密 {@code <serverTime>:<password>} 后提交,使密码不以明文进入请求体。{@code
     * serverTime} 由服务端签发,前端原样并入密文,供服务端校验新鲜度抗重放。
     *
     * @return 含 {@code publicKey}（Base64 SPKI）与 {@code serverTime}（epoch ms）的结果
     */
    @GetMapping("/public-key")
    public Result<Map<String, Object>> loginPublicKey() {
        Map<String, Object> data = new HashMap<>();
        data.put("publicKey", loginCryptoService.getPublicKeyBase64());
        data.put("serverTime", System.currentTimeMillis());
        return Result.ok(data);
    }

    /**
     * 用户登录.
     *
     * 密码经前端以 {@code GET /public-key} 公钥 RSA-OAEP 加密 {@code <serverTime>:<password>} 后提交,
     * 此处以私钥解密、校验时间戳新鲜度(抗重放)后再做凭据校验;解密 / 格式 / 新鲜度任一失败统一按凭据错误返回,不泄露具体原因。
     *
     * @param request 登录请求（username + RSA 密文 password + 可选 service）
     * @return 包含 token、用户信息、权限集和能力列表的结果
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            HttpServletRequest httpRequest) {
        String decrypted = tryDecrypt(request.getPassword());
        String password;
        if (decrypted != null) {
            // 有效密文:强制载荷格式 + 时间戳新鲜度(抗重放)
            int sep = decrypted.indexOf(':');
            if (sep < 0) {
                return Result.fail("用户名或密码错误");
            }
            long clientTs;
            try {
                clientTs = Long.parseLong(decrypted.substring(0, sep));
            } catch (NumberFormatException e) {
                return Result.fail("用户名或密码错误");
            }
            long age = System.currentTimeMillis() - clientTs;
            if (age > LOGIN_PAYLOAD_MAX_AGE_MS || age < -LOGIN_PAYLOAD_MAX_SKEW_MS) {
                return Result.fail("登录请求已过期，请重试");
            }
            password = decrypted.substring(sep + 1);
        } else {
            // 向后兼容:非密文按明文处理(尚未升级公钥加密的客户端,如管理控制台旧包 / 浏览器缓存旧前端)。
            // 记 warn 以便追踪迁移进度 —— 全部客户端升级后可收紧为「仅接受密文」。
            log.warn("登录收到未加密密码(user={}),客户端应升级为公钥加密提交", request.getUsername());
            password = request.getPassword();
        }
        return authService.login(
                request.getUsername(),
                password,
                request.getSystem(),
                clientIp(httpRequest),
                userAgent);
    }

    /**
     * 提取客户端真实 IP —— 优先 {@code X-Forwarded-For} 首段(网关/代理链最外层客户端),回退 {@code remoteAddr}.
     *
     * @param request HTTP 请求
     * @return 客户端 IP
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 尝试以登录私钥解密 RSA 密文 —— 非有效密文(明文 / 非法 Base64 / 填充错误)返回 {@code null}, 供调用方回退明文兼容处理.
     *
     * @param raw 请求提交的 password 字段(密文或明文)
     * @return 解密后的明文载荷;非有效密文时 {@code null}
     */
    private String tryDecrypt(String raw) {
        try {
            return loginCryptoService.decrypt(raw);
        } catch (Exception e) {
            return null;
        }
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
     * 刷新令牌 — 用 refresh token 换取新的 access + refresh token 对.
     *
     * 无需 Authorization 头(access token 可能已过期), refresh token 在请求体中提交。每次调用执行 token rotation: 旧
     * refresh token 作废,签发全新一对。
     *
     * @param request 刷新令牌请求
     * @return 包含新 token 和 refreshToken 的结果
     */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.getRefreshToken());
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
            // 拉黑当前令牌(已过期则 revoke 内部 no-op)+ 移除会话 + 作废 refresh 族
            authService.logout(
                    claims.getId(),
                    claims.getExpiration().getTime(),
                    jwtService.sessionIdOf(claims));
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
