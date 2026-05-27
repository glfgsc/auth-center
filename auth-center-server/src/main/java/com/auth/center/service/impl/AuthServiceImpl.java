package com.auth.center.service.impl;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.AuthUserMapper;
import com.auth.center.mapper.PermissionSetMapper;
import com.auth.center.security.JwtService;
import com.auth.center.security.LoginRateLimiter;
import com.auth.center.security.ITokenBlacklistService;
import com.auth.center.service.IAuthService;
import com.auth.center.service.IGroupService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证服务实现 -- 提供登录、注册、用户信息查询与注销的完整业务逻辑.
 *
 * <p>登录流程: 频率限制检查 -> 用户名查询 -> BCrypt 密码验证 -> 解析权限集 -> 签发 JWT。</p>
 * <p>注册流程: 用户名唯一性校验 -> BCrypt 密码加密 -> 入库 -> 分配默认权限集 -> 签发 JWT。</p>
 */
@Service
public class AuthServiceImpl implements IAuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** 默认权限集编码 -- 新注册用户或未分配权限集用户的降级角色 */
    private static final String DEFAULT_PERMISSION_SET_CODE = "viewer";

    /** 返回字段键名: JWT 令牌 */
    private static final String KEY_TOKEN = "token";

    /** 返回字段键名: 用户信息 */
    private static final String KEY_USER = "user";

    /** 返回字段键名: 权限集编码 */
    private static final String KEY_PERMISSION_SET = "permissionSet";

    /** 返回字段键名: 能力列表 */
    private static final String KEY_CAPABILITIES = "capabilities";

    private final AuthUserMapper authUserMapper;
    private final PermissionSetMapper permissionSetMapper;
    private final JwtService jwtService;
    private final LoginRateLimiter loginRateLimiter;
    private final ITokenBlacklistService tokenBlacklistService;
    private final PasswordEncoder passwordEncoder;
    private final IGroupService groupService;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param authUserMapper        用户 Mapper
     * @param permissionSetMapper   权限集 Mapper
     * @param jwtService            JWT 签发/解析服务
     * @param loginRateLimiter      登录频率限制器
     * @param tokenBlacklistService Token 黑名单服务
     * @param passwordEncoder       密码编码器
     * @param groupService          用户组服务
     */
    public AuthServiceImpl(AuthUserMapper authUserMapper,
                           PermissionSetMapper permissionSetMapper,
                           JwtService jwtService,
                           LoginRateLimiter loginRateLimiter,
                           ITokenBlacklistService tokenBlacklistService,
                           PasswordEncoder passwordEncoder,
                           IGroupService groupService) {
        this.authUserMapper = authUserMapper;
        this.permissionSetMapper = permissionSetMapper;
        this.jwtService = jwtService;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklistService = tokenBlacklistService;
        this.passwordEncoder = passwordEncoder;
        this.groupService = groupService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        // 频率限制检查
        if (loginRateLimiter.isLocked(username)) {
            long remaining = loginRateLimiter.remainingLockSeconds(username);
            return Result.fail("账户已被锁定，请 " + remaining + " 秒后重试");
        }

        // 按用户名查询用户
        LambdaQueryWrapper<AuthUser> query = new LambdaQueryWrapper<>();
        query.eq(AuthUser::getUsername, username);
        AuthUser user = authUserMapper.selectOne(query);

        if (user == null) {
            loginRateLimiter.recordFailure(username);
            return Result.fail("用户名或密码错误");
        }

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginRateLimiter.recordFailure(username);
            return Result.fail("用户名或密码错误");
        }

        // 登录成功，清除失败记录
        loginRateLimiter.recordSuccess(username);

        // 解析权限集
        PermissionSet ps = resolvePermissionSet(user.getId());

        // 签发 JWT
        String token = jwtService.generateAccessToken(
                user.getId(), user.getUsername(),
                ps.getCode(), ps.getCapabilities());

        // 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_TOKEN, token);
        data.put(KEY_USER, buildUserMap(user));
        data.put(KEY_PERMISSION_SET, ps.getCode());
        data.put(KEY_CAPABILITIES, ps.getCapabilities());

        log.info("用户 {} 登录成功", username);
        return Result.ok(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Result<Map<String, Object>> register(String username, String password, String nickname) {
        // 用户名唯一性校验
        LambdaQueryWrapper<AuthUser> query = new LambdaQueryWrapper<>();
        query.eq(AuthUser::getUsername, username);
        Long count = authUserMapper.selectCount(query);
        if (count != null && count > 0) {
            return Result.fail("用户名已存在");
        }

        // 创建用户，BCrypt 加密密码
        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname);
        authUserMapper.insert(user);

        // 自动加入 all_users 系统组
        groupService.ensureAllUsersGroup(user.getId());

        // 解析默认权限集（viewer）
        PermissionSet ps = resolvePermissionSet(user.getId());

        // 签发 JWT
        String token = jwtService.generateAccessToken(
                user.getId(), user.getUsername(),
                ps.getCode(), ps.getCapabilities());

        // 组装返回数据
        Map<String, Object> data = new HashMap<>();
        data.put(KEY_TOKEN, token);
        data.put(KEY_USER, buildUserMap(user));
        data.put(KEY_PERMISSION_SET, ps.getCode());
        data.put(KEY_CAPABILITIES, ps.getCapabilities());

        log.info("用户 {} 注册成功", username);
        return Result.ok(data);
    }

    /**
     * {@inheritDoc}
     */
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

    /**
     * {@inheritDoc}
     */
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
        PermissionSet ps = resolvePermissionSet(userId);

        Map<String, Object> data = new HashMap<>();
        data.put(KEY_PERMISSION_SET, ps.getCode());
        data.put(KEY_CAPABILITIES, ps.getCapabilities());
        return Result.ok(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void logout(String jti, long expiryMs) {
        tokenBlacklistService.revoke(jti, expiryMs);
        log.info("已吊销 JWT, jti={}", jti);
    }

    /* ---------- 私有辅助方法 ---------- */

    /**
     * 解析用户的权限集.
     *
     * <p>查找优先级:
     * <ol>
     *     <li>用户关联的权限集（auth_user_permission_set 关联表）</li>
     *     <li>降级到默认 viewer 权限集</li>
     *     <li>若 viewer 也不存在，构造空 viewer 对象</li>
     * </ol>
     *
     * @param userId 用户 ID
     * @return 权限集实体，保证非 null
     */
    private PermissionSet resolvePermissionSet(Long userId) {
        // 查询用户关联的权限集
        PermissionSet ps = permissionSetMapper.selectByUserId(userId);
        if (ps != null) {
            return ps;
        }

        // 降级到默认 viewer 权限集
        ps = permissionSetMapper.selectByCode(DEFAULT_PERMISSION_SET_CODE);
        if (ps != null) {
            return ps;
        }

        // viewer 也不存在时，构造空对象兜底
        log.warn("未找到默认权限集 {}，为用户 {} 返回空 viewer", DEFAULT_PERMISSION_SET_CODE, userId);
        PermissionSet empty = new PermissionSet();
        empty.setCode(DEFAULT_PERMISSION_SET_CODE);
        empty.setName("查看者");
        empty.setCapabilities("[]");
        return empty;
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
