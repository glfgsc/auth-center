package com.auth.center.mapper;

import com.auth.center.entity.AuthUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 认证用户 Mapper 接口.
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供 {@link AuthUser} 的基础 CRUD 操作。</p>
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {
}
