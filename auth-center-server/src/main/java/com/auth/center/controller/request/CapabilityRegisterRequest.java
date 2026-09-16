package com.auth.center.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 能力码批量注册请求 DTO.
 *
 * 各子系统启动时上报自身能力目录,认证中心以 {@code systemCode} 维度全量收录,作为能力码的唯一真源(供权限集勾选授权、管理台分组展示)。
 */
public class CapabilityRegisterRequest {

    /** 子系统编码，如 bi / agent / tracking */
    @NotBlank(message = "systemCode 不能为空")
    @Size(max = 50, message = "systemCode 长度不能超过 50")
    private String systemCode;

    /** 能力码列表，不可为空 */
    @NotEmpty(message = "capabilities 不能为空")
    private List<@Valid CapabilityItem> capabilities;

    /**
     * 获取子系统编码.
     *
     * @return 子系统编码
     */
    public String getSystemCode() {
        return systemCode;
    }

    /**
     * 设置子系统编码.
     *
     * @param systemCode 子系统编码
     */
    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    /**
     * 获取能力码列表.
     *
     * @return 能力码列表
     */
    public List<CapabilityItem> getCapabilities() {
        return capabilities;
    }

    /**
     * 设置能力码列表.
     *
     * @param capabilities 能力码列表
     */
    public void setCapabilities(List<CapabilityItem> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * 单个能力码条目.
     *
     * 字段与各子系统 registrar 上报的一致:{@code code / category / label / description}。
     */
    public static class CapabilityItem {

        /** 能力码标识，如 dashboard:view */
        @NotBlank(message = "code 不能为空")
        @Size(max = 100, message = "code 长度不能超过 100")
        private String code;

        /** 能力分组（资源前缀，如 dashboard / agent / spec），可空 */
        @Size(max = 50, message = "category 长度不能超过 50")
        private String category;

        /** 能力显示名称 */
        @Size(max = 200, message = "label 长度不能超过 200")
        private String label;

        /** 能力描述 */
        @Size(max = 500, message = "description 长度不能超过 500")
        private String description;

        /**
         * 获取能力码标识.
         *
         * @return 能力码标识
         */
        public String getCode() {
            return code;
        }

        /**
         * 设置能力码标识.
         *
         * @param code 能力码标识
         */
        public void setCode(String code) {
            this.code = code;
        }

        /**
         * 获取能力分组.
         *
         * @return 能力分组
         */
        public String getCategory() {
            return category;
        }

        /**
         * 设置能力分组.
         *
         * @param category 能力分组
         */
        public void setCategory(String category) {
            this.category = category;
        }

        /**
         * 获取能力显示名称.
         *
         * @return 能力显示名称
         */
        public String getLabel() {
            return label;
        }

        /**
         * 设置能力显示名称.
         *
         * @param label 能力显示名称
         */
        public void setLabel(String label) {
            this.label = label;
        }

        /**
         * 获取能力描述.
         *
         * @return 能力描述
         */
        public String getDescription() {
            return description;
        }

        /**
         * 设置能力描述.
         *
         * @param description 能力描述
         */
        public void setDescription(String description) {
            this.description = description;
        }
    }
}
