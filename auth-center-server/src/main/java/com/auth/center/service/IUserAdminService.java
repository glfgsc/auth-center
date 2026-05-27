package com.auth.center.service;

import com.auth.center.entity.AuthUser;

import java.util.List;

/**
 * 用户管理服务接口 -- 提供用户 CRUD 及权限集分配操作.
 *
 * <p>面向管理后台使用，所有方法需配合管理员权限校验。</p>
 */
public interface IUserAdminService {

    /**
     * 查询所有用户列表.
     *
     * @return 用户列表
     */
    List<AuthUser> list();

    /**
     * 根据 ID 查询单个用户.
     *
     * @param id 用户主键 ID
     * @return 用户实体，不存在时返回 {@code null}
     */
    AuthUser getById(Long id);

    /**
     * 创建新用户.
     *
     * <p>密码将使用 BCrypt 加密后存储。</p>
     *
     * @param user 用户实体（需包含 username、password，可选 nickname/email/phone 等）
     * @return 新创建用户的主键 ID
     */
    Long create(AuthUser user);

    /**
     * 更新用户信息.
     *
     * <p>如果 password 字段不为空，将重新加密后更新；否则仅更新其他字段。</p>
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
     * 为用户分配权限集.
     *
     * <p>每个用户仅关联一个权限集，分配新权限集时会先移除旧的关联记录。</p>
     *
     * @param userId          用户 ID
     * @param permissionSetId 权限集 ID
     */
    void assignPermissionSet(Long userId, Long permissionSetId);

    /**
     * 移除用户的权限集关联.
     *
     * @param userId          用户 ID
     * @param permissionSetId 权限集 ID
     */
    void removePermissionSet(Long userId, Long permissionSetId);
}
