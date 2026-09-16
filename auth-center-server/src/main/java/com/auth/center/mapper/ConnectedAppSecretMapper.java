package com.auth.center.mapper;

import com.auth.center.entity.ConnectedAppSecret;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** Connected App Secret Mapper -- 外部应用密钥数据访问. */
@Mapper
public interface ConnectedAppSecretMapper extends BaseMapper<ConnectedAppSecret> {}
