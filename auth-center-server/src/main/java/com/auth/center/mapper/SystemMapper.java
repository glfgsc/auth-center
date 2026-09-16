package com.auth.center.mapper;

import com.auth.center.entity.AuthSystem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统注册表 Mapper 接口.
 *
 * 继承 MyBatis-Plus {@link BaseMapper}，提供 {@link AuthSystem} 的基础 CRUD 操作。
 */
@Mapper
public interface SystemMapper extends BaseMapper<AuthSystem> {}
