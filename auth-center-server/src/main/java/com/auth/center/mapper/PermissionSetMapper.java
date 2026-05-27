package com.auth.center.mapper;

import com.auth.center.entity.PermissionSet;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 权限集 Mapper 接口.
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供 {@link PermissionSet} 的基础 CRUD 操作，
 * 并包含按用户 ID 和编码查询的自定义方法。</p>
 */
@Mapper
public interface PermissionSetMapper extends BaseMapper<PermissionSet> {

    /**
     * 根据用户 ID 查询其关联的权限集.
     *
     * <p>通过 {@code auth_user_permission_set} 关联表 JOIN 查询，
     * 取第一条匹配记录（每个用户通常只关联一个权限集）。</p>
     *
     * @param userId 用户 ID
     * @return 该用户关联的权限集，不存在时返回 {@code null}
     */
    @Select("SELECT ps.* FROM auth_permission_set ps "
            + "INNER JOIN auth_user_permission_set ups ON ups.permission_set_id = ps.id "
            + "WHERE ups.user_id = #{userId} LIMIT 1")
    PermissionSet selectByUserId(@Param("userId") Long userId);

    /**
     * 根据编码查询权限集.
     *
     * @param code 权限集编码，如 "admin"、"viewer"
     * @return 匹配的权限集，不存在时返回 {@code null}
     */
    @Select("SELECT * FROM auth_permission_set WHERE code = #{code}")
    PermissionSet selectByCode(@Param("code") String code);
}
