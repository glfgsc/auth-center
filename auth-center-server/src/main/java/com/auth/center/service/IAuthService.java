package com.auth.center.service;

import com.auth.center.common.Result;
import java.util.Map;

/**
 * 认证服务接口 -- 提供登录、用户信息查询与注销等核心认证操作.
 *
 * 所有认证相关的业务逻辑在此接口定义，Controller 层通过此接口调用认证服务，不直接依赖具体实现类。
 */
public interface IAuthService {

    /**
     * 用户登录 —— 验证用户名和密码，成功后签发 JWT、创建 refresh 族并登记活跃会话，返回用户信息、权限集和能力列表。
     *
     * @param username 用户名
     * @param password 明文密码
     * @param systemCode 发起登录的产品编码（可空）—— 记进登录历史，供管理台按产品筛；调用方未报即为空
     * @param ip 客户端 IP（会话展示）
     * @param userAgent 客户端 User-Agent（会话展示）
     * @return 包含 token、用户信息、permissionSet、capabilities 的结果
     */
    Result<Map<String, Object>> login(
            String username, String password, String systemCode, String ip, String userAgent);

    /**
     * 登录成功后统一收尾 —— 记成功登录历史(含新 IP / 新设备 / 异地异常检测)并登记活跃会话。
     *
     * 账密登录内部调用;CAS 单点登录也须调用,否则 CAS 用户不出现在会话治理列表(连带无 IP / 设备 / 地点)。
     *
     * @param sessionId 会话 ID(refresh 族 ID)
     * @param userId 登录用户 ID
     * @param username 登录用户名
     * @param accessToken 刚签发的 access token
     * @param systemCode 发起登录的产品编码(可空)
     * @param ip 客户端真实 IP(调用方须已按 X-Forwarded-For 首段解析)
     * @param userAgent 客户端 User-Agent
     */
    void establishSession(
            String sessionId,
            Long userId,
            String username,
            String accessToken,
            String systemCode,
            String ip,
            String userAgent);

    /**
     * 获取当前用户基本信息.
     *
     * 解析 JWT 获取用户 ID，查询并返回 id、username、nickname 等基本字段。
     *
     * @param token JWT 令牌字符串
     * @return 包含用户基本信息的结果
     */
    Result<Map<String, Object>> getUserInfo(String token);

    /**
     * 获取当前用户的权限集信息.
     *
     * 解析 JWT 获取用户 ID，查询并返回权限集编码和能力列表。
     *
     * @param token JWT 令牌字符串
     * @return 包含 permissionSet code 和 capabilities 数组的结果
     */
    Result<Map<String, Object>> getPermissionInfo(String token);

    /**
     * 刷新令牌 — 验证 refresh token 并签发新的 access + refresh token 对.
     *
     * 实现 token rotation: 旧 refresh token 消费后立即失效,同一 refresh token 被重用视为泄露,整个 family 作废。
     *
     * @param refreshToken refresh token JWT 字符串
     * @return 包含新 token、refreshToken 和用户信息的结果;失败时返回错误
     */
    Result<Map<String, Object>> refreshToken(String refreshToken);

    /**
     * 用户注销 -- 将指定 JWT 加入黑名单，并移除对应会话、作废其 refresh 族.
     *
     * @param jti JWT ID（jti claim）
     * @param expiryMs JWT 剩余有效时间（毫秒），黑名单条目在此时间后自动清除
     * @param sessionId 会话 ID（access token 的 sid claim，= refresh 族 ID）；为空时仅拉黑当前令牌
     */
    void logout(String jti, long expiryMs, String sessionId);
}
