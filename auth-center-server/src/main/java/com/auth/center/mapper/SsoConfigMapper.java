package com.auth.center.mapper;

import com.auth.center.entity.SsoConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * SSO 配置 Mapper 接口.
 *
 * <p>继承 MyBatis-Plus {@link BaseMapper}，提供 {@link SsoConfig} 的基础 CRUD 操作。</p>
 */
@Mapper
public interface SsoConfigMapper extends BaseMapper<SsoConfig> {
}
