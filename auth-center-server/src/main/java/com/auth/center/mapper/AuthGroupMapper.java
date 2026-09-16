package com.auth.center.mapper;

import com.auth.center.entity.AuthGroup;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 用户组 Mapper -- 支持按编码查询. */
@Mapper
public interface AuthGroupMapper extends BaseMapper<AuthGroup> {

    /**
     * 按编码查询用户组.
     *
     * @param systemCode 所属产品编码 —— 唯一键是 {@code (system_code, code)}，只按 code 查会在两个产品各有一个同编码组时命中多行
     * @param code 唯一编码
     * @return 用户组或 null
     */
    @Select("SELECT * FROM auth_group WHERE system_code = #{systemCode} AND code = #{code}")
    AuthGroup selectBySystemAndCode(
            @Param("systemCode") String systemCode, @Param("code") String code);
}
