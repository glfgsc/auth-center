package com.auth.center.cas;

import java.util.UUID;

/**
 * CAS ST (Service Ticket) -- 一次性使用的服务票据.
 *
 * 在 TGT 有效期内,用户请求访问某个服务时签发 ST. 服务端通过 ST 向 CAS 服务器验证用户身份, ST 验证后即标记为已使用 (一次性). ST ID 格式为 {@code
 * ST-} 加 UUID, 默认有效期 30 秒.
 */
public class ServiceTicket {

    /** ST ID 前缀 */
    private static final String ST_PREFIX = "ST-";

    /** 票据唯一标识 */
    private String id;

    /** 所属 TGT 的 ID */
    private String tgtId;

    /** 请求服务的回调 URL */
    private String serviceUrl;

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

    /** 是否已使用 (一次性票据) */
    private boolean used;

    /** 无参构造 -- JSON 反序列化框架需要. */
    public ServiceTicket() {}

    /**
     * 创建新的 Service Ticket.
     *
     * @param tgtId 所属 TGT 的 ID
     * @param serviceUrl 请求服务的回调 URL
     * @param userId 用户 ID
     * @param username 用户名
     * @param permissionSet 权限集名称
     * @param capabilities 逗号分隔的能力列表
     * @param ttlMs 有效期 (毫秒)
     */
    public ServiceTicket(
            String tgtId,
            String serviceUrl,
            Long userId,
            String username,
            String permissionSet,
            String capabilities,
            long ttlMs) {
        this.id = ST_PREFIX + UUID.randomUUID();
        this.tgtId = tgtId;
        this.serviceUrl = serviceUrl;
        this.userId = userId;
        this.username = username;
        this.permissionSet = permissionSet;
        this.capabilities = capabilities;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = this.createdAt + ttlMs;
        this.used = false;
    }

    /**
     * 判断该 ST 是否已过期.
     *
     * @return {@code true} 表示已过期, {@code false} 表示仍有效
     */
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /**
     * 获取票据唯一标识.
     *
     * @return ST ID
     */
    public String getId() {
        return id;
    }

    /**
     * 设置票据唯一标识.
     *
     * @param id ST ID
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * 获取所属 TGT 的 ID.
     *
     * @return TGT ID
     */
    public String getTgtId() {
        return tgtId;
    }

    /**
     * 设置所属 TGT 的 ID.
     *
     * @param tgtId TGT ID
     */
    public void setTgtId(String tgtId) {
        this.tgtId = tgtId;
    }

    /**
     * 获取请求服务的回调 URL.
     *
     * @return 服务回调 URL
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * 设置请求服务的回调 URL.
     *
     * @param serviceUrl 服务回调 URL
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
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

    /**
     * 获取是否已使用.
     *
     * @return {@code true} 表示已使用
     */
    public boolean isUsed() {
        return used;
    }

    /**
     * 设置是否已使用.
     *
     * @param used 是否已使用
     */
    public void setUsed(boolean used) {
        this.used = used;
    }
}
