package com.auth.center.mapper;

import com.auth.center.entity.UserPermissionSet;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-权限集关联 Mapper 接口.
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供 {@link UserPermissionSet} 的基础 CRUD 操作。</p>
 */
@Mapper
public interface UserPermissionSetMapper extends BaseMapper<UserPermissionSet> {
}
