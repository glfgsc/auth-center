package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 平台配置项 —— 认证中心按系统分区统一管理的键值配置(特性开关 + 平台级设置)。
 *
 * {@code systemCode} 维度(global / bi / agent / tracking / auth_center);各子系统经读端点取自己的中心配置。 BI
 * 的运行时调优配置(query 限额等)仍留 BI 本地 {@code sys_config},不在此表。
 */
@TableName("auth_platform_config")
public class PlatformConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统: global / bi / agent / tracking / auth_center。 */
    private String systemCode;

    /** 配置键。 */
    private String configKey;

    /** 配置值(按 valueType 解释)。 */
    private String configValue;

    /** 类型: STRING / INTEGER / DOUBLE / BOOLEAN / JSON。 */
    private String valueType;

    /** 分类(admin 页分组)。 */
    private String category;

    /** 显示名。 */
    private String label;

    /** 说明。 */
    private String description;

    /** 默认值(重置用)。 */
    private String defaultValue;

    /** 排序。 */
    private Integer sortOrder;

    /** 最后修改人 userId。 */
    private Long updatedBy;

    /** 最后修改时间。 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 创建时间。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
