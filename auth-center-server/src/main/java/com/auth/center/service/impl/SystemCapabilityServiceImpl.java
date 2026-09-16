package com.auth.center.service.impl;

import com.auth.center.entity.SystemCapability;
import com.auth.center.mapper.SystemCapabilityMapper;
import com.auth.center.service.ISystemCapabilityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统能力码服务实现 -- 按系统全量替换 / 查询能力码.
 *
 * 注册采用「先删该系统旧能力码、再批量插入」的全量替换语义，整体置于单事务内，保证注册的原子性（部分失败时回滚，不残留半量能力码）。
 */
@Service
public class SystemCapabilityServiceImpl implements ISystemCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(SystemCapabilityServiceImpl.class);

    private final SystemCapabilityMapper systemCapabilityMapper;

    /**
     * 构造函数，注入系统能力 Mapper.
     *
     * @param systemCapabilityMapper 系统能力 Mapper
     */
    public SystemCapabilityServiceImpl(SystemCapabilityMapper systemCapabilityMapper) {
        this.systemCapabilityMapper = systemCapabilityMapper;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int replaceForSystem(String systemCode, List<SystemCapability> capabilities) {
        // 删除该系统编码下所有旧能力码
        LambdaQueryWrapper<SystemCapability> deleteQuery = new LambdaQueryWrapper<>();
        deleteQuery.eq(SystemCapability::getSystemCode, systemCode);
        systemCapabilityMapper.delete(deleteQuery);

        // 批量插入新能力码（systemCode 统一回填，避免调用方与路径参数不一致）
        int count = 0;
        for (SystemCapability cap : capabilities) {
            cap.setSystemCode(systemCode);
            systemCapabilityMapper.insert(cap);
            count++;
        }
        log.info("系统 {} 注册了 {} 个能力码", systemCode, count);
        return count;
    }

    /** {@inheritDoc} */
    @Override
    public List<SystemCapability> list(String systemCode) {
        LambdaQueryWrapper<SystemCapability> query = new LambdaQueryWrapper<>();
        if (systemCode != null && !systemCode.isBlank()) {
            query.eq(SystemCapability::getSystemCode, systemCode);
        }
        query.orderByAsc(SystemCapability::getSystemCode, SystemCapability::getCategory);
        return systemCapabilityMapper.selectList(query);
    }
}
