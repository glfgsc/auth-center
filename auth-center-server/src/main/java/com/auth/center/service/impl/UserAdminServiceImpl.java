package com.auth.center.service.impl;

import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.entity.UserPermissionSet;
import com.auth.center.mapper.AuthUserMapper;
import com.auth.center.mapper.UserPermissionSetMapper;
import com.auth.center.security.RedisTokenBlacklistService;
import com.auth.center.security.SystemPermissionResolver;
import com.auth.center.service.IGroupService;
import com.auth.center.service.IPermissionSetService;
import com.auth.center.service.IUserAdminService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户管理服务实现 -- 提供用户 CRUD 和权限集分配的具体业务逻辑.
 *
 * 创建用户时密码使用 BCrypt 加密；分配权限集时先清除旧关联再插入新记录，保证每个用户仅关联一个权限集。
 */
@Service
public class UserAdminServiceImpl implements IUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    /** 用户删除后令牌撤销标记 TTL — 略大于 token 最大有效期（24h），防止时钟偏差 */
    private static final Duration USER_REVOKE_TTL = Duration.ofHours(25);

    /** 用户目录搜索默认返回条数 */
    private static final int DEFAULT_SEARCH_LIMIT = 10;

    /** 用户目录搜索单次返回上限，防止调用方拉取全表 */
    private static final int MAX_SEARCH_LIMIT = 50;

    private final AuthUserMapper authUserMapper;
    private final UserPermissionSetMapper userPermissionSetMapper;
    private final PasswordEncoder passwordEncoder;
    private final IPermissionSetService permissionSetService;
    private final IGroupService groupService;
    private final RedisTokenBlacklistService tokenBlacklistService;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param authUserMapper 用户 Mapper
     * @param userPermissionSetMapper 用户-权限集关联 Mapper
     * @param passwordEncoder 密码编码器
     * @param permissionSetService 权限集服务
     * @param groupService 用户组服务
     * @param tokenBlacklistService 令牌黑名单服务（用于用户删除时撤销所有 token）
     */
    public UserAdminServiceImpl(
            AuthUserMapper authUserMapper,
            UserPermissionSetMapper userPermissionSetMapper,
            PasswordEncoder passwordEncoder,
            IPermissionSetService permissionSetService,
            IGroupService groupService,
            RedisTokenBlacklistService tokenBlacklistService) {
        this.authUserMapper = authUserMapper;
        this.userPermissionSetMapper = userPermissionSetMapper;
        this.passwordEncoder = passwordEncoder;
        this.permissionSetService = permissionSetService;
        this.groupService = groupService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /** {@inheritDoc} */
    @Override
    public List<AuthUser> list() {
        return authUserMapper.selectList(null);
    }

    /**
     * {@inheritDoc}
     *
     * 批量查询用户、关联记录、权限集，在内存中 JOIN 以避免 N+1 查询。
     */
    @Override
    public List<Map<String, Object>> listWithPermissionInfo(String systemCode) {
        List<AuthUser> users = authUserMapper.selectList(null);
        List<PermissionSet> allPs = permissionSetService.list();
        List<UserPermissionSet> allUps = userPermissionSetMapper.selectList(null);

        Map<Long, PermissionSet> psById =
                allPs.stream().collect(Collectors.toMap(PermissionSet::getId, Function.identity()));
        // 用户 -> (系统 -> 权限集 ID)，用于按系统取该用户在指定系统的角色
        Map<Long, Map<String, Long>> userSystemToPsId = new HashMap<>();
        for (UserPermissionSet ups : allUps) {
            userSystemToPsId
                    .computeIfAbsent(ups.getUserId(), k -> new HashMap<>())
                    .put(ups.getSystemCode(), ups.getPermissionSetId());
        }

        // 按系统过滤用户：只保留在目标系统(或 global)有绑定的用户，
        // systemCode 为空时回退到全量（向后兼容）
        if (systemCode != null && !systemCode.isBlank()) {
            users =
                    users.stream()
                            .filter(
                                    u -> {
                                        Map<String, Long> sysMap = userSystemToPsId.get(u.getId());
                                        if (sysMap == null) {
                                            return false;
                                        }
                                        return sysMap.containsKey(systemCode)
                                                || sysMap.containsKey(
                                                        SystemPermissionResolver.GLOBAL_SYSTEM);
                                    })
                            .collect(Collectors.toList());
        }

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

            // 返回该用户的所有权限集绑定（含 systemCode），供前端按系统展示
            Map<String, Long> sysToPs = userSystemToPsId.getOrDefault(user.getId(), Map.of());
            List<Map<String, Object>> psList = new ArrayList<>();
            for (Map.Entry<String, Long> entry : sysToPs.entrySet()) {
                PermissionSet ps = psById.get(entry.getValue());
                if (ps != null) {
                    Map<String, Object> psMap = new LinkedHashMap<>();
                    psMap.put("id", ps.getId());
                    psMap.put("code", ps.getCode());
                    psMap.put("name", ps.getName());
                    psMap.put("systemCode", entry.getKey());
                    psList.add(psMap);
                }
            }
            row.put("permissionSets", psList);

            // 向后兼容：保留指定系统的标量字段
            Long psId = null;
            if (systemCode != null && !systemCode.isBlank()) {
                psId = sysToPs.get(systemCode);
            }
            if (psId == null) {
                psId = sysToPs.get(SystemPermissionResolver.GLOBAL_SYSTEM);
            }
            PermissionSet ps = psId != null ? psById.get(psId) : null;
            row.put("permissionSet", ps != null ? ps.getCode() : null);
            row.put("permissionSetName", ps != null ? ps.getName() : null);
            result.add(row);
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public AuthUser getById(Long id) {
        return authUserMapper.selectById(id);
    }

    /** {@inheritDoc} */
    @Override
    public Long create(AuthUser user) {
        // 密码留空 = 外部账号(经 CAS/SSO 登录),置随机占位密码使其不可本地密码登录 ——
        // 与 findOrCreateExternalCasUser 同款处理,两条建号路径语义一致。
        String raw = user.getPassword();
        boolean external = raw == null || raw.isBlank();
        user.setPassword(passwordEncoder.encode(external ? UUID.randomUUID().toString() : raw));
        authUserMapper.insert(user);
        // 自动加入 all_users 系统组
        groupService.ensureAllUsersGroup(user.getId());
        log.info("创建用户 {}, id={}", user.getUsername(), user.getId());
        return user.getId();
    }

    /** {@inheritDoc} */
    @Override
    public AuthUser findByUsername(String username) {
        LambdaQueryWrapper<AuthUser> query = new LambdaQueryWrapper<>();
        query.eq(AuthUser::getUsername, username);
        return authUserMapper.selectOne(query);
    }

    /**
     * {@inheritDoc}
     *
     * 外部 CAS 用户的自动注册收口在 service 层：置随机密码占位（不可本地登录）、昵称同用户名；默认权限集由调用方经 {@link
     * IPermissionSetService#resolveForUser} 解析（缺省 viewer）。
     */
    @Override
    public AuthUser findOrCreateExternalCasUser(String username) {
        AuthUser existing = findByUsername(username);
        if (existing != null) {
            return existing;
        }
        AuthUser user = new AuthUser();
        user.setUsername(username);
        // 外部 CAS 用户不走本地密码登录，置随机密码占位（BCrypt 加密）
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setNickname(username);
        authUserMapper.insert(user);
        log.info("外部 CAS 用户首次登录，已自动注册: username={}", username);
        return user;
    }

    /**
     * {@inheritDoc}
     *
     * 迁自 {@code UserSearchController}，把动态查询与安全字段投影收口到 service 层（Controller 不直连 Mapper）。
     */
    @Override
    public List<Map<String, Object>> search(String q, String ids, String usernames, Integer limit) {
        int cap =
                Math.max(
                        1,
                        Math.min(limit == null ? DEFAULT_SEARCH_LIMIT : limit, MAX_SEARCH_LIMIT));
        LambdaQueryWrapper<AuthUser> w = new LambdaQueryWrapper<AuthUser>().last("LIMIT " + cap);

        if (ids != null && !ids.isBlank()) {
            List<Long> idList = parseLongList(ids);
            if (idList.isEmpty()) {
                return new ArrayList<>();
            }
            w.in(AuthUser::getId, idList);
        } else if (usernames != null && !usernames.isBlank()) {
            List<String> nameList = parseStringList(usernames);
            if (nameList.isEmpty()) {
                return new ArrayList<>();
            }
            w.in(AuthUser::getUsername, nameList);
        } else {
            w.orderByDesc(AuthUser::getCreateTime);
            if (q != null && !q.isBlank()) {
                String trimmed = q.trim();
                w.and(
                        x ->
                                x.likeRight(AuthUser::getUsername, trimmed)
                                        .or()
                                        .likeRight(AuthUser::getNickname, trimmed));
            }
        }

        List<AuthUser> rows = authUserMapper.selectList(w);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (AuthUser u : rows) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", u.getId());
            v.put("username", u.getUsername());
            v.put("nickname", u.getNickname());
            v.put("avatar", u.getAvatar());
            v.put("email", u.getEmail());
            out.add(v);
        }
        return out;
    }

    /**
     * 将逗号分隔的 ID 字符串解析为 Long 列表（跳过空值和非数字项）.
     *
     * @param csv 逗号分隔的 ID 字符串
     * @return Long 列表
     */
    private static List<Long> parseLongList(String csv) {
        List<Long> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                // 跳过非数字 token — 对单个错误值宽容降级
            }
        }
        return out;
    }

    /**
     * 将逗号分隔的字符串解析为列表（跳过空值）.
     *
     * @param csv 逗号分隔的字符串
     * @return 字符串列表
     */
    private static List<String> parseStringList(String csv) {
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    /** {@inheritDoc} */
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

    /** {@inheritDoc} */
    @Override
    public void delete(Long id) {
        AuthUser existing = authUserMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在, id=" + id);
        }

        // 级联清理:删除用户-权限集关联记录
        LambdaQueryWrapper<UserPermissionSet> upsQuery = new LambdaQueryWrapper<>();
        upsQuery.eq(UserPermissionSet::getUserId, id);
        userPermissionSetMapper.delete(upsQuery);

        // 级联清理:删除用户的所有组成员关联
        groupService.removeAllMembershipsForUser(id);

        // 物理删除:软删会把用户名留在唯一索引里,导致同名用户此后既无法经 CAS 即时建号登录、
        // 也无法在管理台重建(详见 AuthUserMapper#physicalDeleteById)。
        authUserMapper.physicalDeleteById(id);

        // 安全:撤销该用户所有已签发的 JWT — 网关侧将检查此标记拒绝已删除用户的请求
        tokenBlacklistService.revokeAllTokensForUser(id, USER_REVOKE_TTL);

        log.info("删除用户 id={}, username={}", id, existing.getUsername());
    }

    /**
     * 把用户在「权限集所属系统」下的绑定换成给定权限集 —— {@link #assignPermissionSetByCode} 的内部实现。
     *
     * @param userId 用户 ID
     * @param permissionSetId 权限集 ID
     */
    private void assignPermissionSet(Long userId, Long permissionSetId) {
        // 绑定的归属系统 = 权限集的归属系统（tracking 权限集绑定到 tracking、global 绑定到 global），
        // 从而做到「按系统隔离」：每个用户每系统至多一个角色。
        PermissionSet ps = permissionSetService.getById(permissionSetId);
        if (ps == null) {
            throw new IllegalArgumentException("权限集不存在, id=" + permissionSetId);
        }
        String systemCode =
                ps.getSystemCode() == null || ps.getSystemCode().isBlank()
                        ? SystemPermissionResolver.GLOBAL_SYSTEM
                        : ps.getSystemCode();

        // 仅移除该用户「在该系统」的旧绑定（不影响其它系统的角色）
        LambdaQueryWrapper<UserPermissionSet> deleteQuery = new LambdaQueryWrapper<>();
        deleteQuery
                .eq(UserPermissionSet::getUserId, userId)
                .eq(UserPermissionSet::getSystemCode, systemCode);
        userPermissionSetMapper.delete(deleteQuery);

        // 插入新绑定
        UserPermissionSet ups = new UserPermissionSet();
        ups.setUserId(userId);
        ups.setSystemCode(systemCode);
        ups.setPermissionSetId(permissionSetId);
        userPermissionSetMapper.insert(ups);

        log.info("为用户 {} 在系统 {} 分配权限集 {}", userId, systemCode, permissionSetId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissionSetByCode(Long userId, String permissionSetCode) {
        PermissionSet ps = permissionSetService.getByCode(permissionSetCode);
        if (ps == null) {
            throw new IllegalArgumentException("权限集编码不存在: " + permissionSetCode);
        }
        assignPermissionSet(userId, ps.getId());
    }

    /** {@inheritDoc} */
    @Override
    public void removePermissionSet(Long userId, Long permissionSetId) {
        LambdaQueryWrapper<UserPermissionSet> query = new LambdaQueryWrapper<>();
        query.eq(UserPermissionSet::getUserId, userId)
                .eq(UserPermissionSet::getPermissionSetId, permissionSetId);
        userPermissionSetMapper.delete(query);

        log.info("移除用户 {} 的权限集 {}", userId, permissionSetId);
    }
}
