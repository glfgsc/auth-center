package com.auth.center.mapper;

import com.auth.center.entity.AuthGroupMember;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户组成员 Mapper -- 支持 JOIN 查询用户信息和按用户查组 ID 列表.
 */
@Mapper
public interface AuthGroupMemberMapper extends BaseMapper<AuthGroupMember> {

    /**
     * 查询组的所有成员，JOIN auth_user 填充用户名/昵称/邮箱.
     *
     * @param groupId 组 ID
     * @return 带用户信息的成员列表
     */
    @Select("SELECT gm.*, u.username, u.nickname, u.email " +
            "FROM auth_group_member gm " +
            "INNER JOIN auth_user u ON gm.user_id = u.id " +
            "WHERE gm.group_id = #{groupId} " +
            "ORDER BY gm.created_at")
    List<AuthGroupMember> selectMembersWithUserInfo(@Param("groupId") Long groupId);

    /**
     * 查询用户所属的所有组 ID.
     *
     * @param userId 用户 ID
     * @return 组 ID 列表
     */
    @Select("SELECT group_id FROM auth_group_member WHERE user_id = #{userId}")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);
}
