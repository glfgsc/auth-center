package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 外部应用（Connected App）注册实体 -- 记录被授权嵌入平台内容的第三方应用.
 *
 * 每个 Connected App 拥有独立 clientId + 一组密钥（最多 2 个，支持无停机轮换）。外部系统用 clientId + secret
 * 或私钥签的断言(Direct-Trust)证明身份，认证中心校验后铸造短时 embed 会话 JWT。从 BI(bi-core-workspace)上移到
 * auth-center，作为"外部应用与密钥"中心的注册表。
 *
 * @see ConnectedAppSecret
 */
@TableName("auth_connected_app")
public class ConnectedApp {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 应用显示名称（如"CRM 客户分析"） */
    private String name;

    /** 自动生成的应用唯一标识（UUID 格式） */
    private String clientId;

    /** JSON 数组 -- 允许嵌入的域名白名单 */
    private String allowedDomains;

    /** 目标系统：应用嵌入进哪个系统（{@code bi}=洞察 / {@code agent}=知数 / {@code tracking}=循迹） */
    private String targetSystem;

    /** 应用状态：{@code enabled} / {@code disabled} */
    private String status;

    /** Direct-Trust 断言验签公钥（PEM，RS256）；为空表示未启用直信任嵌入 */
    private String assertionPublicKeyPem;

    /** 创建者用户 ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /**
     * 获取主键 ID.
     *
     * @return 主键 ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置主键 ID.
     *
     * @param id 主键 ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取应用名称.
     *
     * @return 应用名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置应用名称.
     *
     * @param name 应用名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取应用标识.
     *
     * @return clientId
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置应用标识.
     *
     * @param clientId clientId
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取域名白名单.
     *
     * @return 域名白名单 JSON
     */
    public String getAllowedDomains() {
        return allowedDomains;
    }

    /**
     * 设置域名白名单.
     *
     * @param allowedDomains 域名白名单 JSON
     */
    public void setAllowedDomains(String allowedDomains) {
        this.allowedDomains = allowedDomains;
    }

    /**
     * 获取目标系统.
     *
     * @return 目标系统 code
     */
    public String getTargetSystem() {
        return targetSystem;
    }

    /**
     * 设置目标系统.
     *
     * @param targetSystem 目标系统 code
     */
    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    /**
     * 获取应用状态.
     *
     * @return 状态
     */
    public String getStatus() {
        return status;
    }

    /**
     * 设置应用状态.
     *
     * @param status 状态
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * 获取断言验签公钥.
     *
     * @return PEM 公钥
     */
    public String getAssertionPublicKeyPem() {
        return assertionPublicKeyPem;
    }

    /**
     * 设置断言验签公钥.
     *
     * @param assertionPublicKeyPem PEM 公钥
     */
    public void setAssertionPublicKeyPem(String assertionPublicKeyPem) {
        this.assertionPublicKeyPem = assertionPublicKeyPem;
    }

    /**
     * 获取创建者 ID.
     *
     * @return 创建者 ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建者 ID.
     *
     * @param createdBy 创建者 ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    /**
     * 获取创建时间.
     *
     * @return 创建时间
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间.
     *
     * @param createdAt 创建时间
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 获取更新时间.
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间.
     *
     * @param updatedAt 更新时间
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
