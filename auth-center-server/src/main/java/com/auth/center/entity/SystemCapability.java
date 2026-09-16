package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 跨系统能力注册实体.
 *
 * 对应数据库表 {@code auth_system_capability}，用于各子系统向认证中心注册自身提供的能力码。例如 BI 系统注册 {@code
 * dashboard:view}、{@code dataset:create} 等能力， Flow 系统注册 {@code flow:deploy}、{@code flow:execute}
 * 等能力。
 *
 * 能力码由 {@code systemCode + capabilityCode} 联合唯一。
 */
@TableName("auth_system_capability")
public class SystemCapability {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 子系统编码，如 "bi"、"flow" */
    private String systemCode;

    /** 能力码，如 "dashboard:view"、"flow:deploy" */
    private String capabilityCode;

    /** 能力分类，如 "dashboard"、"dataset"、"flow" */
    private String category;

    /** 能力展示名称 */
    private String label;

    /** 能力描述 */
    private String description;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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
     * 获取能力码.
     *
     * @return 能力码
     */
    public String getCapabilityCode() {
        return capabilityCode;
    }

    /**
     * 设置能力码.
     *
     * @param capabilityCode 能力码
     */
    public void setCapabilityCode(String capabilityCode) {
        this.capabilityCode = capabilityCode;
    }

    /**
     * 获取能力分类.
     *
     * @return 能力分类
     */
    public String getCategory() {
        return category;
    }

    /**
     * 设置能力分类.
     *
     * @param category 能力分类
     */
    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * 获取能力展示名称.
     *
     * @return 能力展示名称
     */
    public String getLabel() {
        return label;
    }

    /**
     * 设置能力展示名称.
     *
     * @param label 能力展示名称
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
