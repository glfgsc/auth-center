package com.auth.center.service.impl;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthLoginHistory;
import com.auth.center.entity.AuthUser;
import com.auth.center.mapper.AuthUserMapper;
import com.auth.center.security.ISessionRegistryService;
import com.auth.center.security.ITokenBlacklistService;
import com.auth.center.security.IpGeoService;
import com.auth.center.security.JwtService;
import com.auth.center.security.LoginAnomalyDetector;
import com.auth.center.security.LoginRateLimiter;
import com.auth.center.security.RefreshTokenService;
import com.auth.center.security.SessionInfo;
import com.auth.center.security.SessionPolicy;
import com.auth.center.security.SessionRevokeReasons;
import com.auth.center.security.SessionRevoker;
import com.auth.center.security.SystemPermissionResolver;
import com.auth.center.service.IAuthService;
import com.auth.center.service.IGroupService;
import com.auth.center.service.ILoginHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现 -- 提供登录、注册、用户信息查询与注销的完整业务逻辑.
 *
 * 登录流程:频率限制检查 -> 用户名查询 -> BCrypt 密码验证 -> 解析权限集 -> 签发 JWT。
 *
 * 注册流程:用户名唯一性校验 -> BCrypt 密码加密 -> 入库 -> 分配默认权限集 -> 签发 JWT。
 */
@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 用户不存在时执行虚拟 BCrypt 比对,消除时序差异防止用户名枚举。 */
    private static final String DUMMY_BCRYPT_HASH =
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ012";

    /** 返回字段键名: JWT 令牌 */
    private static final String KEY_TOKEN = "token";

    /** 返回字段键名:用户信息 */
    private static final String KEY_USER = "user";

    /** 返回字段键名:权限集编码（向后兼容标量） */
    private static final String KEY_PERMISSION_SET = "permissionSet";

    /** 返回字段键名:能力列表（向后兼容标量） */
    private static final String KEY_CAPABILITIES = "capabilities";

    /** 返回字段键名:按系统权限（system -> {ps, caps[]}） */
    private static final String KEY_SYSTEM_PERMISSIONS = "systemPermissions";

    /** 返回字段键名: refresh token */
    private static final String KEY_REFRESH_TOKEN = "refreshToken";

    /** 返回字段键名:会话吊销原因码(见 {@link SessionRevokeReasons}) */
    private static final String KEY_REASON = "reason";

    /** 续期被拒的兜底提示 —— 认得出原因时由前端按 {@link #KEY_REASON} 出话,这句只是没有码时的退路。 */
    private static final String FAIL_REFRESH_INVALID = "refresh token 已失效";

    /** 登录失败原因(管理员登录历史可见,区别于对外统一的「用户名或密码错误」):账户已锁定。 */
    private static final String FAIL_REASON_LOCKED = "账户已锁定";

    /** 登录失败原因:用户不存在。 */
    private static final String FAIL_REASON_USER_NOT_FOUND = "用户不存在";

    /** 登录失败原因:密码错误。 */
    private static final String FAIL_REASON_BAD_PASSWORD = "密码错误";

    private final AuthUserMapper authUserMapper;
    private final SystemPermissionResolver systemPermissionResolver;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final ITokenBlacklistService tokenBlacklistService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final IGroupService groupService;
    private final ISessionRegistryService sessionRegistry;
    private final ILoginHistoryService loginHistoryService;
    private final IpGeoService ipGeoService;
    private final LoginAnomalyDetector anomalyDetector;
    private final SessionPolicy sessionPolicy;
    private final SessionRevoker sessionRevoker;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param authUserMapper 用户 Mapper
     * @param systemPermissionResolver 按系统权限解析器
     * @param jwtService JWT 签发/解析服务
     * @param loginRateLimiter 登录频率限制器
     * @param tokenBlacklistService Token 黑名单服务
     * @param refreshTokenService Refresh Token 族谱管理服务
     * @param passwordEncoder 密码编码器
     * @param groupService 用户组服务
     * @param sessionRegistry 活跃会话注册表
     * @param loginHistoryService 登录历史服务
     * @param ipGeoService IP 地理归类服务
     * @param anomalyDetector 登录异常检测器
     * @param sessionPolicy 并发会话策略
     * @param sessionRevoker 会话吊销动作
     */
    public AuthServiceImpl(
            AuthUserMapper authUserMapper,
            SystemPermissionResolver systemPermissionResolver,
            JwtService jwtService,
            LoginRateLimiter loginRateLimiter,
            ITokenBlacklistService tokenBlacklistService,
            RefreshTokenService refreshTokenService,
            PasswordEncoder passwordEncoder,
            IGroupService groupService,
            ISessionRegistryService sessionRegistry,
            ILoginHistoryService loginHistoryService,
            IpGeoService ipGeoService,
            LoginAnomalyDetector anomalyDetector,
            SessionPolicy sessionPolicy,
            SessionRevoker sessionRevoker) {
        this.authUserMapper = authUserMapper;
        this.systemPermissionResolver = systemPermissionResolver;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklistService = tokenBlacklistService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
        this.sessionRegistry = sessionRegistry;
        this.loginHistoryService = loginHistoryService;
        this.ipGeoService = ipGeoService;
        this.anomalyDetector = anomalyDetector;
        this.sessionPolicy = sessionPolicy;
        this.sessionRevoker = sessionRevoker;
    }

    /** {@inheritDoc} */
    @Override
    public Result<Map<String, Object>> login(
            String username, String password, String systemCode, String ip, String userAgent) {
        // 归类 IP 并解析地点各一次,登录历史与会话登记复用
        String ipClass = ipGeoService.classify(ip);
        String location = ipGeoService.locate(ip);

        // 频率限制检查
        if (loginRateLimiter.isLocked(username)) {
            long remaining = loginRateLimiter.remainingLockSeconds(username);
            recordLoginHistory(
                    null,
                    username,
                    systemCode,
                    LoginAnomalyDetector.STATUS_FAILED,
                    FAIL_REASON_LOCKED,
                    null,
                    ip,
                    ipClass,
                    location,
                    userAgent);
            return Result.fail("账户已被锁定，请 " + remaining + " 秒后重试");
        }

        // 按用户名查询用户
        LambdaQueryWrapper<AuthUser> query = new LambdaQueryWrapper<>();
        query.eq(AuthUser::getUsername, username);
        AuthUser user = authUserMapper.selectOne(query);

        if (user == null) {
            passwordEncoder.matches(password, DUMMY_BCRYPT_HASH);
            loginRateLimiter.recordFailure(username);
            recordLoginHistory(
                    null,
                    username,
                    systemCode,
                    LoginAnomalyDetector.STATUS_FAILED,
                    FAIL_REASON_USER_NOT_FOUND,
                    null,
                    ip,
                    ipClass,
                    location,
                    userAgent);
            return Result.fail("用户名或密码错误");
        }

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginRateLimiter.recordFailure(username);
            recordLoginHistory(
                    user.getId(),
                    username,
                    systemCode,
                    LoginAnomalyDetector.STATUS_FAILED,
                    FAIL_REASON_BAD_PASSWORD,
                    null,
                    ip,
                    ipClass,
                    location,
                    userAgent);
            return Result.fail("用户名或密码错误");
        }

        // 登录成功，清除失败记录
        loginRateLimiter.recordSuccess(username);

        // 解析按系统权限;先建 refresh 族(familyId 即会话 ID),再签发带 sid 的 access token
        SystemPermissionResolver.Resolved rp = systemPermissionResolver.resolve(user.getId());

        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername());
        String refreshJti = jwtService.parseToken(refreshToken).getId();
        String sessionId =
                refreshTokenService.createFamily(
                        refreshJti, jwtService.getRefreshTokenExpiration());

        String token =
                jwtService.generateAccessToken(
                        user.getId(), user.getUsername(),
                        rp.getLegacyPermissionSet(), rp.getLegacyCapabilities(),
                        rp.getSystemPermissions(), sessionId);

        // 登录成功收尾:记成功历史(含异常检测)+ 登记活跃会话。与 CAS 登录复用 establishSession,
        // 确保两条路径的活跃会话都带真实客户端 IP / 设备 / 地点。
        establishSession(sessionId, user.getId(), username, token, systemCode, ip, userAgent);

        // 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_TOKEN, token);
        data.put(KEY_REFRESH_TOKEN, refreshToken);
        data.put(KEY_USER, buildUserMap(user));
        data.put(KEY_PERMISSION_SET, rp.getLegacyPermissionSet());
        data.put(KEY_CAPABILITIES, rp.getLegacyCapabilities());
        data.put(KEY_SYSTEM_PERMISSIONS, rp.getSystemPermissions());

        log.info("用户 {} 登录成功", username);
        return Result.ok(data);
    }

    /** {@inheritDoc} */
    @Override
    public Result<Map<String, Object>> getUserInfo(String token) {
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (Exception e) {
            log.warn("解析 token 失败: {}", e.getMessage());
            return Result.fail("无效的令牌");
        }

        Long userId = claims.get("userId", Long.class);
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        return Result.ok(buildUserMap(user));
    }

    /** {@inheritDoc} */
    @Override
    public Result<Map<String, Object>> getPermissionInfo(String token) {
        Claims claims;
        try {
            claims = jwtService.parseToken(token);
        } catch (Exception e) {
            log.warn("解析 token 失败: {}", e.getMessage());
            return Result.fail("无效的令牌");
        }

        Long userId = claims.get("userId", Long.class);
        SystemPermissionResolver.Resolved rp = systemPermissionResolver.resolve(userId);

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_PERMISSION_SET, rp.getLegacyPermissionSet());
        data.put(KEY_CAPABILITIES, rp.getLegacyCapabilities());
        data.put(KEY_SYSTEM_PERMISSIONS, rp.getSystemPermissions());
        return Result.ok(data);
    }

    /** {@inheritDoc} */
    @Override
    public Result<Map<String, Object>> refreshToken(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseToken(refreshToken);
        } catch (Exception e) {
            log.warn("Refresh token 解析失败: {}", e.getMessage());
            return Result.fail("refresh token 无效或已过期");
        }

        // 校验 token 类型
        String type = claims.get("type", String.class);
        if (!"refresh".equals(type)) {
            return Result.fail("非法的 token 类型");
        }

        Long userId = claims.get("userId", Long.class);
        if (userId == null) {
            return Result.fail("refresh token 缺少用户标识");
        }

        // 签发新的 refresh token (用于 rotation)
        String oldJti = claims.getId();
        AuthUser user = authUserMapper.selectById(userId);
        if (user == null) {
            return Result.fail("用户不存在");
        }

        String newRefreshToken = jwtService.generateRefreshToken(userId, user.getUsername());
        String newJti = jwtService.parseToken(newRefreshToken).getId();

        // Rotation: 旧 JTI 标记为已消费,注册新 JTI; replay → 整族失效
        String familyId =
                refreshTokenService.rotate(oldJti, newJti, jwtService.getRefreshTokenExpiration());
        if (familyId == null) {
            log.warn("[Security] Refresh token rotation 失败, userId={}, jti={}", userId, oldJti);
            return refreshRejected(oldJti);
        }

        // 签发新 access token(携原会话 sid=familyId,跨续期稳定)
        SystemPermissionResolver.Resolved rp = systemPermissionResolver.resolve(userId);
        String newAccessToken =
                jwtService.generateAccessToken(
                        userId,
                        user.getUsername(),
                        rp.getLegacyPermissionSet(),
                        rp.getLegacyCapabilities(),
                        rp.getSystemPermissions(),
                        familyId);

        // 更新会话的当前 access 令牌与活跃时间(会话治理列表据此展示)
        try {
            Claims newAccessClaims = jwtService.parseToken(newAccessToken);
            sessionRegistry.touch(
                    familyId,
                    newAccessClaims.getId(),
                    newAccessClaims.getExpiration().getTime(),
                    System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("[Session] 续期更新会话失败, sessionId={}: {}", familyId, e.getMessage());
        }

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_TOKEN, newAccessToken);
        data.put(KEY_REFRESH_TOKEN, newRefreshToken);
        data.put(KEY_PERMISSION_SET, rp.getLegacyPermissionSet());
        data.put(KEY_CAPABILITIES, rp.getLegacyCapabilities());
        data.put(KEY_SYSTEM_PERMISSIONS, rp.getSystemPermissions());

        return Result.ok(data);
    }

    /** {@inheritDoc} */
    @Override
    public void logout(String jti, long expiryMs, String sessionId) {
        // 出示的这一枚单独拉黑:它未必就是注册表记着的当前令牌(会话吊销按 sid 覆盖全部令牌,但那要 sid 在)。
        tokenBlacklistService.revoke(jti, expiryMs);
        // sid 缺失(如嵌入会话)时仅拉黑当前令牌
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRevoker.revoke(sessionId, SessionRevokeReasons.LOGOUT);
        }
        log.info("已吊销 JWT, jti={}, sessionId={}", jti, sessionId);
    }

    /**
     * 登录成功后的统一收尾 —— 账密登录与 CAS 单点登录共用:算 IP 归类 / 地点 → 记成功登录历史(含新 IP / 新设备 / 异地异常检测)→ 登记活跃会话。
     *
     * 两条路径必须收口一致:CAS 三个端点若只发 JWT、不登记会话,CAS 用户在会话治理列表里就整个缺席(连带无 IP / 设备 / 地点),而账密登录正常。{@code ip}
     * 由各自入口按 X-Forwarded-For 首段解析后传入。
     *
     * @param sessionId 会话 ID(refresh 族 ID)
     * @param userId 登录用户 ID
     * @param username 登录用户名
     * @param accessToken 刚签发的 access token
     * @param systemCode 发起本次登录的产品(未报为 {@code null})
     * @param ip 客户端真实 IP(调用方须已按 X-Forwarded-For 首段解析)
     * @param userAgent 客户端 User-Agent
     */
    @Override
    public void establishSession(
            String sessionId,
            Long userId,
            String username,
            String accessToken,
            String systemCode,
            String ip,
            String userAgent) {
        String ipClass = ipGeoService.classify(ip);
        String location = ipGeoService.locate(ip);
        String anomalies =
                recordLoginHistory(
                        userId,
                        username,
                        systemCode,
                        LoginAnomalyDetector.STATUS_SUCCESS,
                        null,
                        sessionId,
                        ip,
                        ipClass,
                        location,
                        userAgent);
        recordSession(
                sessionId,
                userId,
                username,
                accessToken,
                systemCode,
                ip,
                ipClass,
                userAgent,
                location,
                anomalies);
        enforceSessionLimit(sessionId, userId);
    }

    /**
     * 按并发会话上限顶掉该用户的旧会话 —— 新登录活下来,超出上限的旧会话即刻失效。
     *
     * 放在 {@link #establishSession} 里而不是 {@code login} 里:CAS 单点登录是另一条签发路径,只在账密登录处做,从 CAS 进来就绕开了限制。嵌入会话
     * ({@code scope=embed})不走这里 —— 那是服务端代 iframe 铸的短命令牌,没有 {@code sid} 也不进注册表,顶掉它只会打断嵌入报表。
     *
     * 上限读取或吊销失败绝不阻断登录:登录已经成功,此处再抛就成了「密码对了却进不去」。
     *
     * @param sessionId 本次登录的会话 ID(必留)
     * @param userId 登录用户 ID
     */
    private void enforceSessionLimit(String sessionId, Long userId) {
        try {
            int maxPerUser = sessionPolicy.maxSessionsPerUser();
            if (maxPerUser <= 0) {
                return;
            }
            sessionRevoker.enforceLimit(
                    userId, sessionId, maxPerUser, SessionRevokeReasons.CONCURRENT_LOGIN);
        } catch (Exception e) {
            log.warn("[Session] 并发会话上限执行失败, userId={}: {}", userId, e.getMessage());
        }
    }

    /* ---------- 私有辅助方法 ---------- */

    /**
     * 组装续期被拒的应答 —— 带上会话吊销原因码(取得到的话)。
     *
     * 前端的收尾路径是「业务请求 401 → 续期 → 续期也被拒 → 跳登录」,故被顶下线的用户最终看到的那一句取决于这里回了什么。只给码不给话:话术在前端,新增码时改前端的码 → 文案表。
     *
     * @param refreshJti 被拒的 refresh token JTI
     * @return 失败应答;{@code data.reason} 为吊销原因码,认不出则不带
     */
    private Result<Map<String, Object>> refreshRejected(String refreshJti) {
        String sessionId = refreshTokenService.familyOf(refreshJti);
        String reason =
                sessionId == null ? null : tokenBlacklistService.sessionRevokeReason(sessionId);
        if (reason == null) {
            return Result.fail(FAIL_REFRESH_INVALID);
        }
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_REASON, reason);
        return new Result<>(HttpStatus.UNAUTHORIZED.value(), FAIL_REFRESH_INVALID, data);
    }

    /**
     * 登记一条活跃会话到注册表(会话治理).
     *
     * 解析 access token 取当前 JTI 与过期时间;会话存活上限取 refresh 族 TTL。登记失败仅告警,不阻断登录。
     *
     * @param sessionId 会话 ID(refresh 族 ID)
     * @param userId 登录用户 ID
     * @param username 登录用户名
     * @param accessToken 刚签发的 access token
     * @param ip 客户端 IP
     * @param ipClass IP 归类(INTERNAL / PUBLIC)
     * @param systemCode 发起本次登录的产品(调用方未报为 {@code null})
     * @param userAgent 客户端 User-Agent
     * @param location 登录地点(内网或无法解析为 {@code null})
     * @param anomalies 登录时算出的异常标记快照(无为 {@code null})
     */
    private void recordSession(
            String sessionId,
            Long userId,
            String username,
            String accessToken,
            String systemCode,
            String ip,
            String ipClass,
            String userAgent,
            String location,
            String anomalies) {
        try {
            Claims claims = jwtService.parseToken(accessToken);
            long now = System.currentTimeMillis();
            SessionInfo session = new SessionInfo();
            session.setSessionId(sessionId);
            session.setUserId(userId);
            session.setUsername(username);
            session.setAccessJti(claims.getId());
            session.setAccessExpiresAt(claims.getExpiration().getTime());
            session.setLoginAt(now);
            session.setLastActiveAt(now);
            session.setExpiresAt(now + jwtService.getRefreshTokenExpiration());
            session.setIp(ip);
            session.setIpClass(ipClass);
            session.setUserAgent(userAgent);
            session.setLocation(location);
            session.setAnomalies(anomalies);
            session.setSystemCode(systemCode);
            sessionRegistry.record(session);
        } catch (Exception e) {
            log.warn("[Session] 登记会话失败, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 记录一条登录历史(成功或失败),落库前算出异常标记.
     *
     * 异常检测须在落库「之前」跑,以免把当前尝试算入历史基线。记录失败仅告警,绝不阻断登录主流程。
     *
     * @param userId 用户 ID(用户不存在的失败尝试为 {@code null})
     * @param username 登录用户名(尝试值)
     * @param status 结果({@link LoginAnomalyDetector#STATUS_SUCCESS} / {@link
     *     LoginAnomalyDetector#STATUS_FAILED})
     * @param failReason 失败原因(成功为 {@code null})
     * @param systemCode 来源产品(调用方未报为 {@code null})
     * @param sessionId 成功时的会话 ID(失败为 {@code null})
     * @param ip 客户端 IP
     * @param ipClass IP 归类(INTERNAL / PUBLIC)
     * @param location 登录地点(内网或无法解析为 {@code null})
     * @param userAgent 客户端 User-Agent
     * @return 本次算出的异常标记(逗号分隔,无为 {@code null});记录失败亦返回 {@code null}
     */
    private String recordLoginHistory(
            Long userId,
            String username,
            String systemCode,
            String status,
            String failReason,
            String sessionId,
            String ip,
            String ipClass,
            String location,
            String userAgent) {
        try {
            LocalDateTime now = LocalDateTime.now();
            String anomalies =
                    anomalyDetector.detect(userId, username, ip, ipClass, userAgent, status, now);
            AuthLoginHistory history = new AuthLoginHistory();
            history.setUserId(userId);
            history.setUsername(username);
            history.setSystemCode(systemCode);
            history.setLoginTime(now);
            history.setIp(ip);
            history.setIpClass(ipClass);
            history.setLocation(location);
            history.setUserAgent(userAgent);
            history.setStatus(status);
            history.setFailReason(failReason);
            history.setSessionId(sessionId);
            history.setAnomalies(anomalies);
            history.setCreatedAt(now);
            loginHistoryService.record(history);
            return anomalies;
        } catch (Exception e) {
            log.warn("[LoginHistory] 记录登录历史失败, username={}: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * 将用户实体转换为安全的 Map（排除密码等敏感字段）.
     *
     * @param user 用户实体
     * @return 包含 id、username、nickname、avatar、email、phone 的 Map
     */
    private Map<String, Object> buildUserMap(AuthUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("email", user.getEmail());
        map.put("phone", user.getPhone());
        return map;
    }
}
