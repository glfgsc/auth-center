package com.auth.center.service.impl;

import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.PermissionSetMapper;
import com.auth.center.service.IPermissionSetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 权限集服务实现 -- 提供权限集 CRUD 和用户权限解析的具体业务逻辑.
 *
 * <p>系统预设权限集（isSystem=1）不允许删除。
 * 用户权限解析时若未找到关联权限集则降级到默认 viewer。</p>
 */
@Service
public class PermissionSetServiceImpl implements IPermissionSetService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSetServiceImpl.class);

    /** 系统预设标记值: 预设 */
    private static final int SYSTEM_PRESET_FLAG = 1;

    /** 默认权限集编码 */
    private static final String DEFAULT_PERMISSION_SET_CODE = "viewer";

    private final PermissionSetMapper permissionSetMapper;

    /**
     * 构造函数，注入权限集 Mapper.
     *
     * @param permissionSetMapper 权限集 Mapper
     */
    public PermissionSetServiceImpl(PermissionSetMapper permissionSetMapper) {
        this.permissionSetMapper = permissionSetMapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<PermissionSet> list() {
        return permissionSetMapper.selectList(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionSet getById(Long id) {
        return permissionSetMapper.selectById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionSet getByCode(String code) {
        return permissionSetMapper.selectByCode(code);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long save(PermissionSet ps) {
        if (ps.getId() == null) {
            permissionSetMapper.insert(ps);
            log.info("新增权限集 code={}, id={}", ps.getCode(), ps.getId());
        } else {
            permissionSetMapper.updateById(ps);
            log.info("更新权限集 id={}, code={}", ps.getId(), ps.getCode());
        }
        return ps.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        PermissionSet existing = permissionSetMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("权限集不存在, id=" + id);
        }
        if (existing.getIsSystem() != null && existing.getIsSystem() == SYSTEM_PRESET_FLAG) {
            throw new IllegalStateException("系统预设权限集不允许删除, code=" + existing.getCode());
        }
        permissionSetMapper.deleteById(id);
        log.info("删除权限集 id={}, code={}", id, existing.getCode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PermissionSet resolveForUser(Long userId) {
        // 查询用户关联的权限集
        PermissionSet ps = permissionSetMapper.selectByUserId(userId);
        if (ps != null) {
            return ps;
        }

        // 降级到默认 viewer 权限集
        ps = permissionSetMapper.selectByCode(DEFAULT_PERMISSION_SET_CODE);
        if (ps != null) {
            return ps;
        }

        // viewer 也不存在时，构造空对象兜底
        log.warn("未找到默认权限集 {}，为用户 {} 返回空 viewer", DEFAULT_PERMISSION_SET_CODE, userId);
        PermissionSet empty = new PermissionSet();
        empty.setCode(DEFAULT_PERMISSION_SET_CODE);
        empty.setName("查看者");
        empty.setCapabilities("[]");
        return empty;
    }
}
