package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * Connected App 密钥实体 -- 每个应用最多 2 个活跃密钥，支持无停机轮换.
 *
 * 只存 SHA-256 hash，原文仅在创建时返回一次。验证时：输入 secret → hash → 与此表比对。
 *
 * @see ConnectedApp
 */
@TableName("auth_connected_app_secret")
public class ConnectedAppSecret {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属 Connected App ID */
    private Long appId;

    /** 密钥公开标识（如 "sec_abc12345"） */
    private String secretId;

    /** 密钥原文的 SHA-256 hash */
    private String secretHash;

    /** 创建时间 */
    private LocalDateTime createdAt;

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
     * 获取所属应用 ID.
     *
     * @return 应用 ID
     */
    public Long getAppId() {
        return appId;
    }

    /**
     * 设置所属应用 ID.
     *
     * @param appId 应用 ID
     */
    public void setAppId(Long appId) {
        this.appId = appId;
    }

    /**
     * 获取密钥标识.
     *
     * @return 密钥标识
     */
    public String getSecretId() {
        return secretId;
    }

    /**
     * 设置密钥标识.
     *
     * @param secretId 密钥标识
     */
    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    /**
     * 获取密钥哈希.
     *
     * @return 密钥哈希
     */
    public String getSecretHash() {
        return secretHash;
    }

    /**
     * 设置密钥哈希.
     *
     * @param secretHash 密钥哈希
     */
    public void setSecretHash(String secretHash) {
        this.secretHash = secretHash;
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
}
