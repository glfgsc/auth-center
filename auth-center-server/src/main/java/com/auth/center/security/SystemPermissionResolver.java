package com.auth.center.security;

import com.auth.center.entity.PermissionSet;
import com.auth.center.entity.SystemPermissionView;
import com.auth.center.mapper.PermissionSetMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 用户「按系统」权限解析器 —— 为签发 JWT 与登录响应提供统一的 per-system 权限视图.
 *
 * 一个用户在每个系统至多绑定一个权限集，另含可选的 {@code global} 跨系统通用角色。本解析器把这些绑定整理成 {@code systemPermissions}
 * 结构（供新消费者按系统取用），并给出向后兼容的标量 {@code permissionSet} / {@code capabilities}（供未升级消费者与历史 token 过渡）。
 */
@Component
public class SystemPermissionResolver {

    /** 跨系统通用角色的系统编码 */
    public static final String GLOBAL_SYSTEM = "global";

    /** 无任何绑定时的默认角色编码 */
    private static final String DEFAULT_PERMISSION_SET_CODE = "viewer";

    /** systemPermissions 条目字段：权限集编码 */
    private static final String FIELD_PS = "ps";

    /** systemPermissions 条目字段：能力码列表 */
    private static final String FIELD_CAPS = "caps";

    private final PermissionSetMapper permissionSetMapper;

    /**
     * 构造解析器.
     *
     * @param permissionSetMapper 权限集 Mapper
     */
    public SystemPermissionResolver(PermissionSetMapper permissionSetMapper) {
        this.permissionSetMapper = permissionSetMapper;
    }

    /**
     * 解析用户的按系统权限.
     *
     * @param userId 用户 ID
     * @return 解析结果（systemPermissions + 向后兼容标量）
     */
    public Resolved resolve(Long userId) {
        List<SystemPermissionView> rows =
                permissionSetMapper.selectSystemPermissionsByUserId(userId);

        Map<String, Object> systemPermissions = new LinkedHashMap<>();
        for (SystemPermissionView row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(FIELD_PS, row.getPermissionSetCode());
            entry.put(FIELD_CAPS, parseCaps(row.getCapabilities()));
            systemPermissions.put(row.getSystemCode(), entry);
        }

        SystemPermissionView legacy = pickLegacy(rows);
        String legacyPermissionSet;
        String legacyCapabilities;
        if (legacy != null) {
            legacyPermissionSet = legacy.getPermissionSetCode();
            legacyCapabilities = legacy.getCapabilities() != null ? legacy.getCapabilities() : "[]";
        } else {
            // 无任何绑定：降级到默认 viewer，保持与旧逻辑一致
            PermissionSet def = permissionSetMapper.selectByCode(DEFAULT_PERMISSION_SET_CODE);
            legacyPermissionSet = def != null ? def.getCode() : DEFAULT_PERMISSION_SET_CODE;
            legacyCapabilities =
                    def != null && def.getCapabilities() != null ? def.getCapabilities() : "[]";
        }

        return new Resolved(legacyPermissionSet, legacyCapabilities, systemPermissions);
    }

    /**
     * 选取向后兼容标量来源：优先 global 绑定，否则取首个绑定，皆无则 null.
     *
     * @param rows 按系统绑定列表
     * @return 选中的绑定，或 null
     */
    private static SystemPermissionView pickLegacy(List<SystemPermissionView> rows) {
        SystemPermissionView first = null;
        for (SystemPermissionView row : rows) {
            if (GLOBAL_SYSTEM.equals(row.getSystemCode())) {
                return row;
            }
            if (first == null) {
                first = row;
            }
        }
        return first;
    }

    /**
     * 把能力码 JSON 数组字符串解析为干净的能力码列表（容忍逗号分隔历史格式）.
     *
     * 签发侧（本类）与消费侧（{@link JwtAuthenticationFilter} 读 {@code capabilities} claim）共用这一份，
     * 避免两处各写一个容错略有出入的解析器。
     *
     * @param json 形如 {@code ["a:b","c:d"]} 的字符串，可为 null
     * @return 能力码列表（不含括号 / 引号 / 空白）
     */
    public static List<String> parseCaps(String json) {
        List<String> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        String raw = json.trim();
        if (raw.startsWith("[")) {
            raw = raw.substring(1);
        }
        if (raw.endsWith("]")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        for (String part : raw.split(",")) {
            String code = part.replace("\"", "").trim();
            if (!code.isEmpty()) {
                out.add(code);
            }
        }
        return out;
    }

    /** 解析结果载体. */
    public static final class Resolved {

        private final String legacyPermissionSet;
        private final String legacyCapabilities;
        private final Map<String, Object> systemPermissions;

        /**
         * 构造结果.
         *
         * @param legacyPermissionSet 向后兼容的权限集标量编码
         * @param legacyCapabilities 向后兼容的能力码 JSON 字符串
         * @param systemPermissions 按系统权限结构（system -> {ps, caps[]}）
         */
        public Resolved(
                String legacyPermissionSet,
                String legacyCapabilities,
                Map<String, Object> systemPermissions) {
            this.legacyPermissionSet = legacyPermissionSet;
            this.legacyCapabilities = legacyCapabilities;
            this.systemPermissions = systemPermissions;
        }

        /**
         * 获取向后兼容权限集标量编码.
         *
         * @return 权限集编码
         */
        public String getLegacyPermissionSet() {
            return legacyPermissionSet;
        }

        /**
         * 获取向后兼容能力码 JSON 字符串.
         *
         * @return 能力码 JSON
         */
        public String getLegacyCapabilities() {
            return legacyCapabilities;
        }

        /**
         * 获取按系统权限结构.
         *
         * @return system -> {ps, caps[]} 映射
         */
        public Map<String, Object> getSystemPermissions() {
            return systemPermissions;
        }
    }
}
