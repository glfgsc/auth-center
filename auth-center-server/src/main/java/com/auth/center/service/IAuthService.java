package com.auth.center.service;

import com.auth.center.common.Result;

import java.util.Map;

/**
 * 认证服务接口 -- 提供登录、注册、用户信息查询与注销等核心认证操作.
 *
 * <p>所有认证相关的业务逻辑在此接口定义，Controller 层通过此接口调用认证服务，
 * 不直接依赖具体实现类。</p>
 */
public interface IAuthService {

    /**
     * 用户登录.
     *
     * <p>验证用户名和密码，成功后签发 JWT 并返回用户信息、权限集和能力列表。</p>
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 包含 token、用户信息、permissionSet、capabilities 的结果
     */
    Result<Map<String, Object>> login(String username, String password);

    /**
     * 用户注册.
     *
     * <p>校验用户名唯一性，BCrypt 加密密码后入库，分配默认权限集（viewer），签发 JWT。</p>
     *
     * @param username 用户名
     * @param password 明文密码
     * @param nickname 昵称
     * @return 包含 token、用户信息、permissionSet、capabilities 的结果
     */
    Result<Map<String, Object>> register(String username, String password, String nickname);

    /**
     * 获取当前用户基本信息.
     *
     * <p>解析 JWT 获取用户 ID，查询并返回 id、username、nickname 等基本字段。</p>
     *
     * @param token JWT 令牌字符串
     * @return 包含用户基本信息的结果
     */
    Result<Map<String, Object>> getUserInfo(String token);

    /**
     * 获取当前用户的权限集信息.
     *
     * <p>解析 JWT 获取用户 ID，查询并返回权限集编码和能力列表。</p>
     *
     * @param token JWT 令牌字符串
     * @return 包含 permissionSet code 和 capabilities 数组的结果
     */
    Result<Map<String, Object>> getPermissionInfo(String token);

    /**
     * 用户注销 -- 将指定 JWT 加入黑名单.
     *
     * @param jti      JWT ID（jti claim）
     * @param expiryMs JWT 剩余有效时间（毫秒），黑名单条目在此时间后自动清除
     */
    void logout(String jti, long expiryMs);
}
