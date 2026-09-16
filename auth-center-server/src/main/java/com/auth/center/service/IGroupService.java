package com.auth.center.service;

import com.auth.center.entity.AuthGroup;
import com.auth.center.entity.AuthGroupMember;
import java.util.List;

/**
 * 用户组服务接口 -- 组的 CRUD 与成员管理.
 *
 * 系统预置组（{@code is_system=1}）不可删除。用户注册时自动加入 {@code all_users} 组。
 */
public interface IGroupService {

    /**
     * 列出用户组，可按产品收窄.
     *
     * @param systemCode 产品编码；为空则不收窄，返回全部产品的组
     * @return 用户组列表
     */
    List<AuthGroup> listAll(String systemCode);

    /**
     * 按 ID 查询用户组.
     *
     * @param id 组 ID
     * @return 用户组或 null
     */
    AuthGroup getById(Long id);

    /**
     * 创建用户组.
     *
     * @param group 组信息（code / name / description）
     * @param creatorId 创建人 ID
     * @return 创建后的组（含 id）
     */
    AuthGroup create(AuthGroup group, Long creatorId);

    /**
     * 更新用户组信息（name / description）.
     *
     * @param id 组 ID
     * @param patch 待更新字段
     * @return 更新后的组
     */
    AuthGroup update(Long id, AuthGroup patch);

    /**
     * 删除用户组（系统预置组不可删）.
     *
     * @param id 组 ID
     * @throws IllegalStateException 系统预置组时抛出
     */
    void delete(Long id);

    /**
     * 查询组的所有成员（JOIN 用户信息）.
     *
     * @param groupId 组 ID
     * @return 带用户信息的成员列表
     */
    List<AuthGroupMember> listMembers(Long groupId);

    /**
     * 批量添加成员到组（已存在的自动跳过）.
     *
     * @param groupId 组 ID
     * @param userIds 用户 ID 列表
     */
    void addMembers(Long groupId, List<Long> userIds);

    /**
     * 批量移除组成员.
     *
     * @param groupId 组 ID
     * @param userIds 用户 ID 列表
     */
    void removeMembers(Long groupId, List<Long> userIds);

    /**
     * 查询用户所属的所有组 ID.
     *
     * @param userId 用户 ID
     * @return 组 ID 列表
     */
    List<Long> getGroupIdsByUserId(Long userId);

    /**
     * 将用户加入 all_users 系统组（注册时调用）.
     *
     * @param userId 新注册的用户 ID
     */
    void ensureAllUsersGroup(Long userId);

    /**
     * 级联删除用户的所有组成员关联（用户删除时调用）.
     *
     * @param userId 被删除的用户 ID
     */
    void removeAllMembershipsForUser(Long userId);
}
