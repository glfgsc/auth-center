package com.auth.center.mapper;

import com.auth.center.entity.AuthSystem;
import com.auth.center.entity.PermissionSet;
import com.auth.center.entity.SystemPermissionView;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 权限集 Mapper 接口.
 *
 * 继承 MyBatis-Plus {@link BaseMapper}，提供 {@link PermissionSet} 的基础 CRUD 操作，并包含按用户 ID
 * 和编码查询的自定义方法。
 */
@Mapper
public interface PermissionSetMapper extends BaseMapper<PermissionSet> {

    /**
     * 查询用户在 {@code global} 系统下绑定的权限集 —— 即用户的平台级主角色。
     *
     * 权限集是按系统绑定的(每用户每系统至多一个,见 {@link #selectSystemPermissionsByUserId}):同一用户可同时持 {@code
     * global=admin}(平台管理员)与 {@code agent=agent_admin}(智能体产品角色)等多条。「用户的角色 / 是不是平台管理员」的权威判据是 {@code
     * global} 系统那一条,不是随便一条。
     *
     * 故这里显式过滤 {@code system_code='global'}:每用户至多一条 global 记录,{@code LIMIT 1} 因此确定。不带此过滤
     * + 无序 {@code LIMIT 1} 会随机取到 {@code agent} 系统的集 —— 平台管理员被解析成非管理员,数据面的管理员旁路({@code
     * DataAccessDeciderImpl.isAdmin})对真管理员失效,连自己的数据源都看不到。
     *
     * @param userId 用户 ID
     * @return 该用户的 global 权限集;未绑定 global 系统时返回 {@code null}(由调用方回落默认集)
     */
    @Select(
            "SELECT ps.* FROM auth_permission_set ps "
                    + "INNER JOIN auth_user_permission_set ups ON ups.permission_set_id = ps.id "
                    + "WHERE ups.user_id = #{userId} AND ups.system_code = '"
                    + AuthSystem.CODE_GLOBAL
                    + "' LIMIT 1")
    PermissionSet selectGlobalByUserId(@Param("userId") Long userId);

    /**
     * 根据编码查询权限集.
     *
     * @param code 权限集编码，如 "admin"、"viewer"
     * @return 匹配的权限集，不存在时返回 {@code null}
     */
    @Select("SELECT * FROM auth_permission_set WHERE code = #{code}")
    PermissionSet selectByCode(@Param("code") String code);

    /**
     * 查询用户在「各系统」下绑定的权限集（含 'global'）.
     *
     * 一个用户每系统至多一个权限集，故按 system_code 返回多行，供签发 JWT 的 systemPermissions claim 与后台按系统展示使用。
     *
     * @param userId 用户 ID
     * @return 各系统绑定的权限集投影列表（可能为空）
     */
    @Select(
            "SELECT ups.system_code AS systemCode, ps.code AS permissionSetCode, "
                    + "ps.name AS permissionSetName, ps.capabilities AS capabilities "
                    + "FROM auth_user_permission_set ups "
                    + "INNER JOIN auth_permission_set ps ON ps.id = ups.permission_set_id "
                    + "WHERE ups.user_id = #{userId}")
    List<SystemPermissionView> selectSystemPermissionsByUserId(@Param("userId") Long userId);
}
