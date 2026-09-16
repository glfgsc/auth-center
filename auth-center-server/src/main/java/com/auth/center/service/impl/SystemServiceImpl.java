package com.auth.center.service.impl;

import com.auth.center.entity.AuthSystem;
import com.auth.center.mapper.SystemMapper;
import com.auth.center.service.ISystemService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** 系统注册表服务实现 -- 已接入平台/系统清单的查询逻辑. */
@Service
public class SystemServiceImpl implements ISystemService {

    private final SystemMapper systemMapper;

    /**
     * 构造函数，注入系统注册表 Mapper.
     *
     * @param systemMapper 系统注册表 Mapper
     */
    public SystemServiceImpl(SystemMapper systemMapper) {
        this.systemMapper = systemMapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<AuthSystem> list() {
        LambdaQueryWrapper<AuthSystem> query = new LambdaQueryWrapper<>();
        query.orderByAsc(AuthSystem::getSortOrder);
        return systemMapper.selectList(query);
    }
}
