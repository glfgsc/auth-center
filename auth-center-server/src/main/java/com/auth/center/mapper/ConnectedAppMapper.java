package com.auth.center.mapper;

import com.auth.center.entity.ConnectedApp;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Connected App Mapper -- 外部应用注册表数据访问. */
@Mapper
public interface ConnectedAppMapper extends BaseMapper<ConnectedApp> {}
