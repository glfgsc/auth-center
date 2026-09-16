package com.auth.sdk;

import io.jsonwebtoken.Claims;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Auth Center 统一用户主体 -- 从 JWT claims 或网关 Header 构建.
 *
 * 实现 Spring Security {@link UserDetails} 接口,可直接用于 {@code SecurityContextHolder} 中的认证主体,并支持
 * {@code @PreAuthorize} SpEL 表达式进行权限校验.
 *
 * 权限映射规则:权限集名称映射为 {@code PS_<permissionSet>} 形式的 Authority;每个 capability 直接映射为 Authority (如
 * {@code dashboard:view})。
 *
 * 本类为不可变对象,线程安全.
 */
public class AuthUserDetails implements UserDetails {

    @Serial private static final long serialVersionUID = 1L;

    /** 权限集 Authority 前缀 */
    private static final String PERMISSION_SET_PREFIX = "PS_";

    /** JWT claim: 用户 ID */
    private static final String CLAIM_USER_ID = "userId";

    /** JWT claim: 权限集名称 */
    private static final String CLAIM_PERMISSION_SET = "permissionSet";

    /** JWT claim: 能力列表 (逗号分隔) */
    private static final String CLAIM_CAPABILITIES = "capabilities";

    /** JWT claim: 按系统权限 (system -> {ps, caps[]}) */
    private static final String CLAIM_SYSTEM_PERMISSIONS = "systemPermissions";

    /** systemPermissions 条目字段:权限集编码 */
    private static final String SP_FIELD_PS = "ps";

    /** systemPermissions 条目字段:能力码列表 */
    private static final String SP_FIELD_CAPS = "caps";

    /** 跨系统通用角色的系统编码 */
    private static final String GLOBAL_SYSTEM = "global";

    /** JWT claim: 租户 ID */
    private static final String CLAIM_TENANT_ID = "tenantId";

    /** JWT claim: 工作区 ID */
    private static final String CLAIM_WORKSPACE_ID = "workspaceId";

    /** 网关 Header: 用户 ID */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 网关 Header: 用户名 */
    public static final String HEADER_USERNAME = "X-Username";

    /** 网关 Header: 租户 ID */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 网关 Header: 工作区 ID */
    public static final String HEADER_WORKSPACE_ID = "X-Workspace-Id";

    /** 网关 Header: 权限集名称 */
    public static final String HEADER_PERMISSION_SET = "X-Permission-Set";

    /** 网关 Header: 能力列表 (逗号分隔) */
    public static final String HEADER_CAPABILITIES = "X-Capabilities";

    /** 能力列表分隔符 */
    private static final String CAPABILITIES_DELIMITER = ",";

    private final Long userId;
    private final String username;
    private final Long tenantId;
    private final Long workspaceId;
    private final String permissionSet;
    private final Set<String> capabilities;
    private final List<GrantedAuthority> authorities;

    /**
     * 全参构造函数.
     *
     * @param userId 用户 ID, 不可为 null
     * @param username 用户名,不可为 null
     * @param tenantId 租户 ID, 可为 null (单租户场景)
     * @param workspaceId 工作区 ID, 可为 null
     * @param permissionSet 权限集名称,不可为 null
     * @param capabilities 能力集合,不可为 null (可为空集合)
     */
    public AuthUserDetails(
            Long userId,
            String username,
            Long tenantId,
            Long workspaceId,
            String permissionSet,
            Set<String> capabilities) {
        this.userId = userId;
        this.username = username;
        this.tenantId = tenantId;
        this.workspaceId = workspaceId;
        this.permissionSet = permissionSet;
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        this.authorities = buildAuthorities(permissionSet, capabilities);
    }

    /**
     * 从网关注入的 HTTP Header 构建用户主体.
     *
     * 网关已完成 JWT 验证,将 claims 转为标准 Header 注入请求. 本方法从这些 Header 中提取用户信息.
     *
     * @param request Servlet 请求 (需包含网关注入的 Header)
     * @return 用户主体实例
     * @throws IllegalArgumentException X-User-Id 或 X-Username Header 缺失时抛出
     */
    public static AuthUserDetails fromGatewayHeaders(
            jakarta.servlet.http.HttpServletRequest request) {
        String userIdStr = request.getHeader(HEADER_USER_ID);
        String usernameVal = request.getHeader(HEADER_USERNAME);

        if (userIdStr == null || userIdStr.isEmpty()) {
            throw new IllegalArgumentException("缺少必需的网关 Header: " + HEADER_USER_ID);
        }
        if (usernameVal == null || usernameVal.isEmpty()) {
            throw new IllegalArgumentException("缺少必需的网关 Header: " + HEADER_USERNAME);
        }

        Long userId = Long.valueOf(userIdStr);
        Long tenantId = parseLongSafe(request.getHeader(HEADER_TENANT_ID));
        Long workspaceId = parseLongSafe(request.getHeader(HEADER_WORKSPACE_ID));
        String permissionSet = request.getHeader(HEADER_PERMISSION_SET);
        if (permissionSet == null) {
            permissionSet = "";
        }
        Set<String> capabilities = parseCapabilities(request.getHeader(HEADER_CAPABILITIES));

        return new AuthUserDetails(
                userId, usernameVal, tenantId, workspaceId, permissionSet, capabilities);
    }

    /**
     * 从 JWT Claims 构建用户主体.
     *
     * Claims 中的字段名需与 Auth Center 签发的 Token 一致: {@code sub} (用户名), {@code userId}, {@code
     * permissionSet}, {@code capabilities}.
     *
     * @param claims JWT 解析后的 Claims 对象
     * @return 用户主体实例
     * @throws IllegalArgumentException userId 或 sub 缺失时抛出
     */
    public static AuthUserDetails fromJwtClaims(Claims claims) {
        Number userIdNum = claims.get(CLAIM_USER_ID, Number.class);
        if (userIdNum == null) {
            throw new IllegalArgumentException("JWT claims 缺少必需字段: " + CLAIM_USER_ID);
        }
        Long userId = userIdNum.longValue();

        String usernameVal = claims.getSubject();
        if (usernameVal == null || usernameVal.isEmpty()) {
            throw new IllegalArgumentException("JWT claims 缺少 subject (用户名)");
        }

        // tenantId 和 workspaceId 可选
        Number tenantIdNum = claims.get(CLAIM_TENANT_ID, Number.class);
        Long tenantId = tenantIdNum != null ? tenantIdNum.longValue() : null;

        Number workspaceIdNum = claims.get(CLAIM_WORKSPACE_ID, Number.class);
        Long workspaceId = workspaceIdNum != null ? workspaceIdNum.longValue() : null;

        String permissionSet = claims.get(CLAIM_PERMISSION_SET, String.class);
        if (permissionSet == null) {
            permissionSet = "";
        }

        String capStr = claims.get(CLAIM_CAPABILITIES, String.class);
        Set<String> capabilities = parseCapabilities(capStr);

        return new AuthUserDetails(
                userId, usernameVal, tenantId, workspaceId, permissionSet, capabilities);
    }

    /**
     * 从 JWT Claims 构建「指定系统」的用户主体.
     *
     * 读取 {@code systemPermissions} claim，取该用户在 {@code systemCode} 系统下的角色与能力，并叠加 {@code global}
     * 通用角色的能力。如此各平台只拿到与自己相关的权限，做到按系统隔离授权。当 token 不含 {@code systemPermissions}（历史 token）时，回退到旧的扁平
     * {@code permissionSet}/{@code capabilities}。
     *
     * @param claims JWT 解析后的 Claims 对象
     * @param systemCode 当前系统编码（如 bi / tracking）
     * @return 用户主体实例
     * @throws IllegalArgumentException userId 或 sub 缺失时抛出
     */
    public static AuthUserDetails fromJwtClaims(Claims claims, String systemCode) {
        Object spObj = claims.get(CLAIM_SYSTEM_PERMISSIONS);
        if (systemCode == null || systemCode.isEmpty() || !(spObj instanceof Map<?, ?> sp)) {
            // 无按系统权限信息：回退旧扁平 claim
            return fromJwtClaims(claims);
        }

        Number userIdNum = claims.get(CLAIM_USER_ID, Number.class);
        if (userIdNum == null) {
            throw new IllegalArgumentException("JWT claims 缺少必需字段: " + CLAIM_USER_ID);
        }
        Long userId = userIdNum.longValue();

        String usernameVal = claims.getSubject();
        if (usernameVal == null || usernameVal.isEmpty()) {
            throw new IllegalArgumentException("JWT claims 缺少 subject (用户名)");
        }

        Number tenantIdNum = claims.get(CLAIM_TENANT_ID, Number.class);
        Long tenantId = tenantIdNum != null ? tenantIdNum.longValue() : null;
        Number workspaceIdNum = claims.get(CLAIM_WORKSPACE_ID, Number.class);
        Long workspaceId = workspaceIdNum != null ? workspaceIdNum.longValue() : null;

        Object sysEntry = sp.get(systemCode);
        Object globalEntry = sp.get(GLOBAL_SYSTEM);

        Set<String> capabilities = new LinkedHashSet<>();
        capabilities.addAll(extractCaps(sysEntry));
        capabilities.addAll(extractCaps(globalEntry));

        // 权限集标量：优先本系统角色，否则 global 角色
        String sysPs = extractPs(sysEntry);
        String permissionSet = sysPs != null ? sysPs : extractPs(globalEntry);
        if (permissionSet == null) {
            permissionSet = "";
        }

        return new AuthUserDetails(
                userId, usernameVal, tenantId, workspaceId, permissionSet, capabilities);
    }

    /**
     * 从 systemPermissions 条目提取权限集编码.
     *
     * @param entry systemPermissions 中某系统对应的值（应为 Map）
     * @return 权限集编码，无则 null
     */
    private static String extractPs(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object ps = map.get(SP_FIELD_PS);
            if (ps != null) {
                return String.valueOf(ps);
            }
        }
        return null;
    }

    /**
     * 从 systemPermissions 条目提取能力码集合（兼容 List 与逗号 / JSON 字符串）.
     *
     * @param entry systemPermissions 中某系统对应的值（应为 Map）
     * @return 能力码集合（可能为空）
     */
    private static Set<String> extractCaps(Object entry) {
        Set<String> out = new LinkedHashSet<>();
        if (!(entry instanceof Map<?, ?> map)) {
            return out;
        }
        Object caps = map.get(SP_FIELD_CAPS);
        if (caps instanceof Collection<?> col) {
            for (Object c : col) {
                String s = String.valueOf(c).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        } else if (caps != null) {
            Set<String> parsed = parseCapabilities(String.valueOf(caps));
            if (parsed != null) {
                out.addAll(parsed);
            }
        }
        return out;
    }

    // ======================== UserDetails 接口实现 ========================

    /**
     * 获取用户的所有权限 (包括权限集和能力列表).
     *
     * @return 不可变的权限集合
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * 获取密码 -- JWT 模式下不适用,始终返回 null.
     *
     * @return null
     */
    @Override
    public String getPassword() {
        return null;
    }

    /**
     * 获取用户名.
     *
     * @return 用户名
     */
    @Override
    public String getUsername() {
        return username;
    }

    /**
     * 账户是否未过期 -- 始终返回 true (由 JWT 有效期控制).
     *
     * @return true
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 账户是否未锁定 -- 始终返回 true (锁定状态由 Auth Center 管理).
     *
     * @return true
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 凭证是否未过期 -- 始终返回 true (由 JWT 有效期控制).
     *
     * @return true
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 用户是否启用 -- 始终返回 true (禁用状态由 Auth Center 管理).
     *
     * @return true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    // ======================== 业务 Getters ========================

    /**
     * 获取用户 ID.
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 获取租户 ID.
     *
     * @return 租户 ID, 单租户场景下可能为 null
     */
    public Long getTenantId() {
        return tenantId;
    }

    /**
     * 获取工作区 ID.
     *
     * @return 工作区 ID, 可能为 null
     */
    public Long getWorkspaceId() {
        return workspaceId;
    }

    /**
     * 获取权限集名称.
     *
     * @return 权限集名称
     */
    public String getPermissionSet() {
        return permissionSet;
    }

    /**
     * 获取能力集合 (不可变).
     *
     * @return 能力名称集合
     */
    public Set<String> getCapabilities() {
        return capabilities;
    }

    // ======================== 内部工具方法 ========================

    /**
     * 构建 Spring Security GrantedAuthority 列表.
     *
     * 将权限集映射为 {@code PS_<name>}, 每个 capability 直接作为 authority.
     *
     * @param permissionSet 权限集名称
     * @param capabilities 能力集合
     * @return 不可变的权限列表
     */
    private static List<GrantedAuthority> buildAuthorities(
            String permissionSet, Set<String> capabilities) {
        List<GrantedAuthority> list = new ArrayList<>();
        // 权限集 authority
        if (permissionSet != null && !permissionSet.isEmpty()) {
            list.add(new SimpleGrantedAuthority(PERMISSION_SET_PREFIX + permissionSet));
        }
        // 每个 capability 作为独立 authority
        for (String cap : capabilities) {
            if (!cap.isEmpty()) {
                list.add(new SimpleGrantedAuthority(cap));
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 解析逗号分隔的能力字符串为 Set.
     *
     * @param commaSeparated 逗号分隔的能力字符串,可为 null
     * @return 能力名称集合 (不可变)
     */
    private static Set<String> parseCapabilities(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) {
            return null;
        }
        // 兼容 JSON 数组字符串：["a:b","c:d"] -> 去掉首尾括号
        String raw = commaSeparated.trim();
        if (raw.startsWith("[")) {
            raw = raw.substring(1);
        }
        if (raw.endsWith("]")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        Set<String> result = new LinkedHashSet<>();
        for (String part : raw.split(CAPABILITIES_DELIMITER)) {
            // 去除可能残留的 JSON 引号与空白
            String trimmed = part.replace("\"", "").trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 安全解析 Long 值,输入为 null 或空字符串时返回 null.
     *
     * @param value 字符串形式的数值
     * @return 解析后的 Long, 或 null
     */
    private static Long parseLongSafe(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return Long.valueOf(value);
    }
}
