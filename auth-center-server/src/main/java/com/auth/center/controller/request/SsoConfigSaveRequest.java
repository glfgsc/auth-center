package com.auth.center.controller.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * SSO 配置保存请求 DTO.
 *
 * 替代原 {@code Map<String, Object>} 参数，对齐前端 IdentityProvider 字段模型。所有字段均可空（部分更新），但有长度上限。
 */
public class SsoConfigSaveRequest {

    /** 显示名称 */
    @Size(max = 100, message = "name 长度不能超过 100")
    private String name;

    /** 图标标识 */
    @Size(max = 500, message = "icon 长度不能超过 500")
    private String icon;

    /** 运行模式: disabled / mixed / enforced；长度上限对齐 {@code auth_sso_config.mode} 的 VARCHAR(20) */
    @Pattern(
            regexp = "^(disabled|mixed|enforced)?$",
            message = "loginMode 只允许 disabled/mixed/enforced 或空")
    @Size(max = 20, message = "loginMode 长度不能超过 20")
    private String loginMode;

    /** 扩展配置 JSON */
    @Size(max = 4000, message = "configJson 长度不能超过 4000")
    private String configJson;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 获取显示名称.
     *
     * @return 显示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置显示名称.
     *
     * @param name 显示名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取图标标识.
     *
     * @return 图标标识
     */
    public String getIcon() {
        return icon;
    }

    /**
     * 设置图标标识.
     *
     * @param icon 图标标识
     */
    public void setIcon(String icon) {
        this.icon = icon;
    }

    /**
     * 获取运行模式.
     *
     * @return 运行模式
     */
    public String getLoginMode() {
        return loginMode;
    }

    /**
     * 设置运行模式.
     *
     * @param loginMode 运行模式
     */
    public void setLoginMode(String loginMode) {
        this.loginMode = loginMode;
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
     * @return 是否启用
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
}
