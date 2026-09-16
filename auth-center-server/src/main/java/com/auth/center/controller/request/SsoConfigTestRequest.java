package com.auth.center.controller.request;

import jakarta.validation.constraints.Size;

/**
 * SSO 配置连通性测试请求 DTO.
 *
 * 替代原 {@code Map<String, Object>} 参数，仅需 configJson 字段。
 */
public class SsoConfigTestRequest {

    /** 扩展配置 JSON（包含 serverUrl 等连接参数） */
    @Size(max = 4000, message = "configJson 长度不能超过 4000")
    private String configJson;

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
}
