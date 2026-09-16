package com.auth.center.mapper;

import com.auth.center.entity.NotificationCredential;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 通知渠道凭据 Mapper -- 跨产品统一的渠道凭据数据访问. */
@Mapper
public interface NotificationCredentialMapper extends BaseMapper<NotificationCredential> {}
