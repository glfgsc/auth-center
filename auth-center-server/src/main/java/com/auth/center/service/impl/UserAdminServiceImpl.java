package com.auth.center.service.impl;

import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.entity.UserPermissionSet;
import com.auth.center.mapper.AuthUserMapper;
import com.auth.center.mapper.UserPermissionSetMapper;
import com.auth.center.service.IPermissionSetService;
import com.auth.center.service.IUserAdminService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户管理服务实现 -- 提供用户 CRUD 和权限集分配的具体业务逻辑.
 *
 * <p>创建用户时密码使用 BCrypt 加密；分配权限集时先清除旧关联再插入新记录，
 * 保证每个用户仅关联一个权限集。</p>
 */
@Service
public class UserAdminServiceImpl implements IUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    private final AuthUserMapper authUserMapper;
    private final UserPermissionSetMapper userPermissionSetMapper;
    private final PasswordEncoder passwordEncoder;
    private final IPermissionSetService permissionSetService;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param authUserMapper          用户 Mapper
     * @param userPermissionSetMapper  用户-权限集关联 Mapper
     * @param passwordEncoder         密码编码器
     * @param permissionSetService    权限集服务
     */
    public UserAdminServiceImpl(AuthUserMapper authUserMapper,
                                UserPermissionSetMapper userPermissionSetMapper,
                                PasswordEncoder passwordEncoder,
                                IPermissionSetService permissionSetService) {
        this.authUserMapper = authUserMapper;
        this.userPermissionSetMapper = userPermissionSetMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionSetService = permissionSetService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AuthUser> list() {
        return authUserMapper.selectList(null);
    }

    /**
     * {@inheritDoc}
     *
     * <p>批量查询用户、关联记录、权限集，在内存中 JOIN 以避免 N+1 查询。</p>
     */
    @Override
    public List<Map<String, Object>> listWithPermissionInfo() {
        List<AuthUser> users = authUserMapper.selectList(null);
        List<PermissionSet> allPs = permissionSetService.list();
        List<UserPermissionSet> allUps = userPermissionSetMapper.selectList(null);

        Map<Long, PermissionSet> psById = allPs.stream()
                .collect(Collectors.toMap(PermissionSet::getId, Function.identity()));
        Map<Long, Long> userToPsId = allUps.stream()
                .collect(Collectors.toMap(
                        UserPermissionSet::getUserId,
                        UserPermissionSet::getPermissionSetId,
                        (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>(users.size());
        for (AuthUser user : users) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", user.getId());
            row.put("username", user.getUsername());
            row.put("nickname", user.getNickname());
            row.put("avatar", user.getAvatar());
            row.put("email", user.getEmail());
            row.put("phone", user.getPhone());
            row.put("createTime", user.getCreateTime());
            row.put("updateTime", user.getUpdateTime());

            Long psId = userToPsId.get(user.getId());
            if (psId != null) {
                PermissionSet ps = psById.get(psId);
                if (ps != null) {
                    row.put("permissionSet", ps.getCode());
                    row.put("permissionSetName", ps.getName());
                } else {
                    row.put("permissionSet", null);
                    row.put("permissionSetName", null);
                }
            } else {
                row.put("permissionSet", null);
                row.put("permissionSetName", null);
            }
            result.add(row);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AuthUser getById(Long id) {
        return authUserMapper.selectById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long create(AuthUser user) {
        // BCrypt 加密密码
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        authUserMapper.insert(user);
        log.info("创建用户 {}, id={}", user.getUsername(), user.getId());
        return user.getId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void update(AuthUser user) {
        // 如果密码字段非空，重新加密
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            // 置空以避免 MyBatis-Plus 更新为空串
            user.setPassword(null);
        }
        authUserMapper.updateById(user);
        log.info("更新用户 id={}", user.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(Long id) {
        AuthUser existing = authUserMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在, id=" + id);
        }

        // 级联清理: 删除用户-权限集关联记录
        LambdaQueryWrapper<UserPermissionSet> upsQuery = new LambdaQueryWrapper<>();
        upsQuery.eq(UserPermissionSet::getUserId, id);
        userPermissionSetMapper.delete(upsQuery);

        authUserMapper.deleteById(id);
        log.info("删除用户 id={}, username={}", id, existing.getUsername());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissionSet(Long userId, Long permissionSetId) {
        // 先移除该用户的所有旧权限集关联（保证单权限集）
        LambdaQueryWrapper<UserPermissionSet> deleteQuery = new LambdaQueryWrapper<>();
        deleteQuery.eq(UserPermissionSet::getUserId, userId);
        userPermissionSetMapper.delete(deleteQuery);

        // 插入新关联
        UserPermissionSet ups = new UserPermissionSet();
        ups.setUserId(userId);
        ups.setPermissionSetId(permissionSetId);
        userPermissionSetMapper.insert(ups);

        log.info("为用户 {} 分配权限集 {}", userId, permissionSetId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissionSetByCode(Long userId, String permissionSetCode) {
        PermissionSet ps = permissionSetService.getByCode(permissionSetCode);
        if (ps == null) {
            throw new IllegalArgumentException("权限集编码不存在: " + permissionSetCode);
        }
        assignPermissionSet(userId, ps.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removePermissionSet(Long userId, Long permissionSetId) {
        LambdaQueryWrapper<UserPermissionSet> query = new LambdaQueryWrapper<>();
        query.eq(UserPermissionSet::getUserId, userId)
             .eq(UserPermissionSet::getPermissionSetId, permissionSetId);
        userPermissionSetMapper.delete(query);

        log.info("移除用户 {} 的权限集 {}", userId, permissionSetId);
    }
}
