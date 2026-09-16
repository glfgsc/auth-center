package com.auth.center.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 权限集分配请求 DTO.
 *
 * 替代原 {@code Map<String, String>} 参数，对权限集编码做非空和长度约束。
 */
public class AssignPermissionSetRequest {

    /** 权限集编码，如 admin / viewer */
    @NotBlank(message = "permissionSetCode 不能为空")
    @Size(max = 50, message = "permissionSetCode 长度不能超过 50")
    private String permissionSetCode;

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
}
