package com.auth.center.mapper;

import com.auth.center.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 统一活动审计 Mapper。 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {}
