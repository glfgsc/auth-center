package com.auth.center.entity;

/**
 * 用户「按系统」生效权限的投影视图.
 *
 * 由 {@code auth_user_permission_set} 与 {@code auth_permission_set} 关联查询得到，
 * 表示某用户在某系统下绑定的权限集（编码、名称、能力码）。用于签发 JWT 的 {@code systemPermissions} claim 与后台「按系统」展示用户角色。
 */
public class SystemPermissionView {

    /** 绑定所属系统编码（某系统码或 'global'） */
    private String systemCode;

    /** 权限集编码 */
    private String permissionSetCode;

    /** 权限集名称 */
    private String permissionSetName;

    /** 能力码 JSON 数组字符串 */
    private String capabilities;

    /**
     * 获取系统编码.
     *
     * @return 系统编码或 'global'
     */
    public String getSystemCode() {
        return systemCode;
    }

    /**
     * 设置系统编码.
     *
     * @param systemCode 系统编码或 'global'
     */
    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    /**
     * 获取权限集编码.
     *
     * @return 权限集编码
     */
    public String getPermissionSetCode() {
        return permissionSetCode;
    }

    /**
     * 设置权限集编码.
     *
     * @param permissionSetCode 权限集编码
     */
    public void setPermissionSetCode(String permissionSetCode) {
        this.permissionSetCode = permissionSetCode;
    }

    /**
     * 获取权限集名称.
     *
     * @return 权限集名称
     */
    public String getPermissionSetName() {
        return permissionSetName;
    }

    /**
     * 设置权限集名称.
     *
     * @param permissionSetName 权限集名称
     */
    public void setPermissionSetName(String permissionSetName) {
        this.permissionSetName = permissionSetName;
    }

    /**
     * 获取能力码 JSON 数组字符串.
     *
     * @return 能力码 JSON
     */
    public String getCapabilities() {
        return capabilities;
    }

    /**
     * 设置能力码 JSON 数组字符串.
     *
     * @param capabilities 能力码 JSON
     */
    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }
}
