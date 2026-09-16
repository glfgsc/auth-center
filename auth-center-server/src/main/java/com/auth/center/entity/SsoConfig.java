package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * SSO 配置实体 -- 外部 CAS 集成配置.
 *
 * 对应数据库表 {@code auth_sso_config}，存储外部 SSO 服务器集成参数。一个产品一行（{@code systemCode}），唯一键 {@code
 * (system_code, type)}；产品没有自己那一行时登录期回落到 {@code global} 兜底档。支持三种运行模式:
 *
 *   - {@code disabled} - 关闭外部 SSO，仅使用内置认证
 *   - {@code mixed} - 混合模式，同时显示本地登录和 SSO 按钮
 *   - {@code enforced} - 强制 SSO，所有用户必须通过外部 SSO 登录
 */
@TableName("auth_sso_config")
public class SsoConfig {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属产品，对齐 {@code auth_system.code}（bi / agent / tracking / auth_center / global）。
     *
     * {@code global} 是兜底档：产品没有自己那一行时，登录期回落到它。
     */
    private String systemCode;

    /** SSO 类型，当前支持 CAS */
    private String type;

    /** 运行模式: disabled / mixed / enforced */
    private String mode;

    /** 外部 CAS 服务器地址，如 https://sso.example.com/cas */
    private String serverUrl;

    /** SSO 按钮显示名称，如 "企业 SSO 登录" */
    private String displayName;

    /** SSO 按钮图标（emoji 或图标标识） */
    private String icon;

    /** 扩展配置 JSON（属性映射、协议版本、自动注册等） */
    private String configJson;

    /** 是否启用该 SSO 配置 */
    private Boolean enabled;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /* ---------- Getters / Setters ---------- */

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
     * 获取所属产品编码.
     *
     * @return 产品编码
     */
    public String getSystemCode() {
        return systemCode;
    }

    /**
     * 设置所属产品编码.
     *
     * @param systemCode 产品编码
     */
    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    /**
     * 获取 SSO 类型.
     *
     * @return SSO 类型
     */
    public String getType() {
        return type;
    }

    /**
     * 设置 SSO 类型.
     *
     * @param type SSO 类型
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取运行模式.
     *
     * @return 运行模式 (disabled / mixed / enforced)
     */
    public String getMode() {
        return mode;
    }

    /**
     * 设置运行模式.
     *
     * @param mode 运行模式 (disabled / mixed / enforced)
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * 获取外部 CAS 服务器地址.
     *
     * @return 服务器地址
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * 设置外部 CAS 服务器地址.
     *
     * @param serverUrl 服务器地址
     */
    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    /**
     * 获取 SSO 按钮显示名称.
     *
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置 SSO 按钮显示名称.
     *
     * @param displayName 显示名称
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取图标.
     *
     * @return 图标标识
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 设置图标.
     *
     * @param icon 图标标识
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * 获取扩展配置 JSON.
     *
     * @return 配置 JSON 字符串
     */
    public String getConfigJson() {
        return configJson;
    }

    /**
     * 设置扩展配置 JSON.
     *
     * @param configJson 配置 JSON 字符串
     */
    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    /**
     * 获取是否启用.
     *
     * @return {@code true} 表示启用
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用.
     *
     * @param enabled 是否启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
