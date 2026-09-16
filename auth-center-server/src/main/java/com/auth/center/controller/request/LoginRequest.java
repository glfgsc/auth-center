package com.auth.center.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求 DTO.
 *
 * 替代原 {@code Map<String, String>} 参数，提供结构化的字段约束：用户名和密码必填，service 用于 CAS 回调（非 CAS 场景可空）。
 */
public class LoginRequest {

    /** 用户名，不可为空，最大 200 字符 */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 200, message = "用户名长度不能超过 200")
    private String username;

    /** 密码 —— RSA-OAEP 加密后的 Base64 密文（明文经前端以登录公钥加密，服务端解密后再校验），不可为空。 */
    @NotBlank(message = "密码不能为空")
    @Size(max = 1024, message = "密码密文长度非法")
    private String password;

    /** CAS 回调 service URL（非 CAS 登录时可空） */
    @Size(max = 2000, message = "service 长度不能超过 2000")
    private String service;

    /**
     * 发起登录的产品编码，对齐 {@code auth_system.code}（可空）。
     *
     * 记进登录历史的来源产品，供管理台按产品筛。不报即为空 —— 那一行不归入任何产品，按产品筛时不出现；这是如实反映，不要在服务端猜一个默认产品填进去。
     */
    @Size(max = 32, message = "产品编码长度不能超过 32")
    private String system;

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
     * 获取密码.
     *
     * @return 密码
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码.
     *
     * @param password 密码
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取 CAS 回调 service URL.
     *
     * @return service URL，可能为 {@code null}
     */
    public String getService() {
        return service;
    }

    /**
     * 设置 CAS 回调 service URL.
     *
     * @param service service URL
     */
    public void setService(String service) {
        this.service = service;
    }

    /**
     * 获取发起登录的产品编码.
     *
     * @return 产品编码，可能为 {@code null}
     */
    public String getSystem() {
        return system;
    }

    /**
     * 设置发起登录的产品编码.
     *
     * @param system 产品编码
     */
    public void setSystem(String system) {
        this.system = system;
    }
}
