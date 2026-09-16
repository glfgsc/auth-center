package com.auth.center.service.impl;

import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.PermissionSetMapper;
import com.auth.center.security.SystemPermissionResolver;
import com.auth.center.service.IPermissionSetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 权限集服务实现 -- 提供权限集 CRUD 和用户权限解析的具体业务逻辑.
 *
 * 系统预设权限集（isSystem=1）不允许删除。用户权限解析时若未找到关联权限集则降级到默认 viewer。
 */
@Service
public class PermissionSetServiceImpl implements IPermissionSetService {

    private static final Logger log = LoggerFactory.getLogger(PermissionSetServiceImpl.class);

    /** 系统预设标记值:预设 */
    private static final int SYSTEM_PRESET_FLAG = 1;

    /** 默认权限集编码 */
    private static final String DEFAULT_PERMISSION_SET_CODE = "viewer";

    /** 权限集管理能力码 —— 自指能力，见 {@link #assertManagePermissionSetSurvives(PermissionSet)} */
    private static final String CAP_MANAGE_PERMISSION_SET = "admin:manage_permission_set";

    private final PermissionSetMapper permissionSetMapper;

    /**
     * 构造函数，注入权限集 Mapper.
     *
     * @param permissionSetMapper 权限集 Mapper
     */
    public PermissionSetServiceImpl(PermissionSetMapper permissionSetMapper) {
        this.permissionSetMapper = permissionSetMapper;
    }

    /** {@inheritDoc} */
    @Override
    public List<PermissionSet> list() {
        return permissionSetMapper.selectList(null);
    }

    /** {@inheritDoc} */
    @Override
    public List<PermissionSet> list(String systemCode) {
        if (systemCode == null || systemCode.isBlank()) {
            return permissionSetMapper.selectList(null);
        }
        // 指定系统可用 = 该系统 + global 通用
        LambdaQueryWrapper<PermissionSet> query = new LambdaQueryWrapper<>();
        query.and(
                w ->
                        w.eq(PermissionSet::getSystemCode, systemCode)
                                .or()
                                .eq(
                                        PermissionSet::getSystemCode,
                                        SystemPermissionResolver.GLOBAL_SYSTEM));
        query.orderByAsc(PermissionSet::getSortOrder);
        return permissionSetMapper.selectList(query);
    }

    /** {@inheritDoc} */
    @Override
    public PermissionSet getById(Long id) {
        return permissionSetMapper.selectById(id);
    }

    /** {@inheritDoc} */
    @Override
    public PermissionSet getByCode(String code) {
        return permissionSetMapper.selectByCode(code);
    }

    /** {@inheritDoc} */
    @Override
    public Long save(PermissionSet ps) {
        assertManagePermissionSetSurvives(ps);
        if (ps.getId() == null) {
            // 新建时归属系统缺省为 global（跨系统通用）
            if (ps.getSystemCode() == null || ps.getSystemCode().isBlank()) {
                ps.setSystemCode(SystemPermissionResolver.GLOBAL_SYSTEM);
            }
            permissionSetMapper.insert(ps);
            log.info(
                    "新增权限集 code={}, system={}, id={}",
                    ps.getCode(),
                    ps.getSystemCode(),
                    ps.getId());
        } else {
            permissionSetMapper.updateById(ps);
            log.info("更新权限集 id={}, code={}", ps.getId(), ps.getCode());
        }
        return ps.getId();
    }

    /**
     * 拦下会让「权限集管理」能力在全平台绝迹的保存.
     *
     * {@code admin:manage_permission_set} 是自指的:权限集编辑器本身由它把守。若最后一个持有它的权限集把它勾掉，就再
     * 没有任何人能打开权限集编辑器，也就再没有办法把它加回来 —— 这是不可逆的自锁，只能靠改库或跑迁移解，因此在写入前挡住。
     *
     * 其余能力码不设此守卫:勾掉 {@code admin:manage_user} 之类只是失去那块功能，仍可经权限集编辑器加回来，属可恢复操作。删除路径无需守卫 —— 持有它的
     * admin 是系统预设集，{@link #delete(Long)} 本就禁止删除预设集。
     *
     * @param incoming 待保存的权限集
     * @throws IllegalStateException 该保存会抹掉全平台最后一份权限集管理能力
     */
    private void assertManagePermissionSetSurvives(PermissionSet incoming) {
        // 新建不会减少持有者；capabilities 为 null 表示本次不改能力（MyBatis-Plus NOT_NULL 跳过该列）
        if (incoming.getId() == null || incoming.getCapabilities() == null) {
            return;
        }
        if (SystemPermissionResolver.parseCaps(incoming.getCapabilities())
                .contains(CAP_MANAGE_PERMISSION_SET)) {
            return;
        }
        for (PermissionSet other : permissionSetMapper.selectList(null)) {
            if (incoming.getId().equals(other.getId())) {
                continue;
            }
            if (SystemPermissionResolver.parseCaps(other.getCapabilities())
                    .contains(CAP_MANAGE_PERMISSION_SET)) {
                return;
            }
        }
        throw new IllegalStateException(
                "不能移除最后一份「"
                        + CAP_MANAGE_PERMISSION_SET
                        + "」能力：移除后将没有任何人能再打开权限集编辑器，"
                        + "该操作不可逆。请先把该能力授予另一个权限集，再从本权限集移除。");
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public PermissionSet resolveForUser(Long userId) {
        // 用户的平台级主角色 = global 系统那一条(见 selectGlobalByUserId)。不能取「随便一条」——
        // 用户同时持 global=admin 与 agent=agent_admin 时,取到 agent 的那条会把管理员误判成非管理员。
        PermissionSet ps = permissionSetMapper.selectGlobalByUserId(userId);
        if (ps != null) {
            return ps;
        }

        // 未绑定 global 系统 → 降级到默认 viewer 权限集
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
