package com.auth.center.mapper;

import com.auth.center.entity.AuthGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户组 Mapper -- 支持按编码查询.
 */
@Mapper
public interface AuthGroupMapper extends BaseMapper<AuthGroup> {

    /**
     * 按编码查询用户组.
     *
     * @param code 唯一编码
     * @return 用户组或 null
     */
    @Select("SELECT * FROM auth_group WHERE code = #{code}")
    AuthGroup selectByCode(@Param("code") String code);
}
