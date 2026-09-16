package com.auth.center.service;

import com.auth.center.entity.AuthUser;
import java.util.List;
import java.util.Map;

/**
 * 用户管理服务接口 -- 提供用户 CRUD 及权限集分配操作.
 *
 * 面向管理后台使用，所有方法需配合管理员权限校验。
 */
public interface IUserAdminService {

    /**
     * 查询所有用户列表.
     *
     * @return 用户列表
     */
    List<AuthUser> list();

    /**
     * 查询用户列表，关联「指定系统」下的权限集信息.
     *
     * 当 {@code systemCode} 非空时，只返回在该系统（或 global）有权限集绑定的用户； {@code systemCode} 为空时返回全量用户（向后兼容）。
     *
     * 返回 Map 列表，{@code permissionSet}/{@code permissionSetName} 为该用户在 {@code systemCode}
     * 系统下的角色（优先该系统绑定，否则回退 global 绑定）。
     *
     * @param systemCode 目标系统编码（如 bi / tracking），可为 null
     * @return 按系统过滤后的、带权限集信息的用户列表
     */
    List<Map<String, Object>> listWithPermissionInfo(String systemCode);

    /**
     * 根据 ID 查询单个用户.
     *
     * @param id 用户主键 ID
     * @return 用户实体，不存在时返回 {@code null}
     */
    AuthUser getById(Long id);

    /**
     * 根据用户名精确查询用户.
     *
     * @param username 用户名
     * @return 用户实体，不存在时返回 {@code null}
     */
    AuthUser findByUsername(String username);

    /**
     * 轻量用户目录搜索 —— 供 {@code @mention} 选人组件等场景使用，仅返回公开安全字段（id / username / nickname / avatar /
     * email），跳过密码、手机号等敏感字段.
     *
     * 每次请求只执行一种模式，优先级 {@code ids > usernames > q}，避免调用方以填充 ids 列表绕过数量上限。
     *
     * @param q 模糊搜索关键词（按 username / nickname 前缀匹配），可空
     * @param ids 逗号分隔的用户 ID 列表（精确查询），可空
     * @param usernames 逗号分隔的用户名列表（精确查询），可空
     * @param limit 返回上限（默认 10，最大 50）
     * @return 用户公开信息 Map 列表
     */
    List<Map<String, Object>> search(String q, String ids, String usernames, Integer limit);

    /**
     * 创建新用户.
     *
     * 密码将使用 BCrypt 加密后存储。
     *
     * @param user 用户实体（需包含 username、password，可选 nickname/email/phone 等）
     * @return 新创建用户的主键 ID
     */
    Long create(AuthUser user);

    /**
     * 按用户名查找本地用户，不存在则自动注册（供外部 CAS SSO 首次登录使用）.
     *
     * 自动注册的用户置随机密码占位（不可通过本地密码登录）、昵称同用户名。
     *
     * @param username 外部 CAS 返回的用户名
     * @return 已存在或新注册的用户实体
     */
    AuthUser findOrCreateExternalCasUser(String username);

    /**
     * 更新用户信息.
     *
     * 如果 password 字段不为空，将重新加密后更新；否则仅更新其他字段。
     *
     * @param user 用户实体（id 必填，其他字段按需填写）
     */
    void update(AuthUser user);

    /**
     * 删除用户.
     *
     * @param id 用户主键 ID
     * @throws IllegalArgumentException 用户不存在时抛出
     */
    void delete(Long id);

    /**
     * 根据权限集编码为用户分配权限集.
     *
     * 通过编码解析权限集 ID，再执行分配。前端通过 code 而非 ID 指定权限集。
     *
     * @param userId 用户 ID
     * @param permissionSetCode 权限集编码，如 "admin"、"viewer"
     * @throws IllegalArgumentException 权限集编码不存在时抛出
     */
    void assignPermissionSetByCode(Long userId, String permissionSetCode);

    /**
     * 移除用户的权限集关联.
     *
     * @param userId 用户 ID
     * @param permissionSetId 权限集 ID
     */
    void removePermissionSet(Long userId, Long permissionSetId);
}
