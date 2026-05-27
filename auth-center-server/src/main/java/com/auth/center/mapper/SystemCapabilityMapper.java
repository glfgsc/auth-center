package com.auth.center.mapper;

import com.auth.center.entity.SystemCapability;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 跨系统能力注册 Mapper 接口.
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供 {@link SystemCapability} 的基础 CRUD 操作。</p>
 */
@Mapper
public interface SystemCapabilityMapper extends BaseMapper<SystemCapability> {
}
