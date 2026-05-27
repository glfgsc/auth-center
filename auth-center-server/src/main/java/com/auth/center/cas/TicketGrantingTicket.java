package com.auth.center.cas;

import java.util.UUID;

/**
 * CAS TGT (Ticket Granting Ticket) -- 代表一个已认证的用户会话.
 *
 * <p>TGT 在用户成功登录后创建, 有效期内可用于签发 Service Ticket.
 * TGT ID 格式为 {@code TGT-} 加 UUID.
 */
public class TicketGrantingTicket {

    /** TGT ID 前缀 */
    private static final String TGT_PREFIX = "TGT-";

    /** 票据唯一标识 */
    private String id;

    /** 关联的用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 权限集名称 */
    private String permissionSet;

    /** 逗号分隔的能力列表 */
    private String capabilities;

    /** 创建时间戳 (毫秒) */
    private long createdAt;

    /** 过期时间戳 (毫秒) */
    private long expiresAt;

    /**
     * 创建新的 TGT.
     *
     * @param userId        用户 ID
     * @param username      用户名
     * @param permissionSet 权限集名称
     * @param capabilities  逗号分隔的能力列表
     * @param ttlMs         有效期 (毫秒)
     */
    public TicketGrantingTicket(Long userId, String username,
                                String permissionSet, String capabilities,
                                long ttlMs) {
        this.id = TGT_PREFIX + UUID.randomUUID();
        this.userId = userId;
        this.username = username;
        this.permissionSet = permissionSet;
        this.capabilities = capabilities;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + ttlMs;
    }

    /**
     * 判断该 TGT 是否已过期.
     *
     * @return {@code true} 表示已过期, {@code false} 表示仍有效
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * 获取票据唯一标识.
     *
     * @return TGT ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置票据唯一标识.
     *
     * @param id TGT ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取关联的用户 ID.
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置关联的用户 ID.
     *
     * @param userId 用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取用户名.
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名.
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
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
     * 设置权限集名称.
     *
     * @param permissionSet 权限集名称
     */
    public void setPermissionSet(String permissionSet) {
        this.permissionSet = permissionSet;
    }

    /**
     * 获取逗号分隔的能力列表.
     *
     * @return 能力列表
     */
    public String getCapabilities() {
        return capabilities;
    }

    /**
     * 设置逗号分隔的能力列表.
     *
     * @param capabilities 能力列表
     */
    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * 获取创建时间戳.
     *
     * @return 创建时间戳 (毫秒)
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间戳.
     *
     * @param createdAt 创建时间戳 (毫秒)
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取过期时间戳.
     *
     * @return 过期时间戳 (毫秒)
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * 设置过期时间戳.
     *
     * @param expiresAt 过期时间戳 (毫秒)
     */
    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }
}
