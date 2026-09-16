package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 用户组实体.
 *
 * 对应数据库表 {@code auth_group}，用于批量权限管理的逻辑分组。系统预置 {@code all_users} 组包含所有注册用户，不可删除。
 */
@TableName("auth_group")
public class AuthGroup {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属产品，对齐 {@code auth_system.code}；{@code global} 表示不属于任何单一产品的全平台组。
     *
     * 唯一键是 {@code (system_code, code)}，故不同产品可以各有一个同编码的组。
     */
    @Size(max = 32, message = "产品编码长度不能超过 32")
    private String systemCode;

    /** 唯一编码，例如 {@code all_users} */
    @NotBlank(message = "编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "编码只能包含字母、数字、下划线、点和连字符")
    @Size(max = 50, message = "编码长度不能超过 50")
    private String code;

    /** 显示名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    /** 描述 */
    private String description;

    /** 系统预置不可删：1=是，0=否 */
    private Integer isSystem;

    /** 创建人 ID */
    private Long createdBy;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /* ---------- Getters / Setters ---------- */

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
     * 获取唯一编码.
     *
     * @return 唯一编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置唯一编码.
     *
     * @param code 唯一编码
     */
    public void setCode(String code) {
        this.code = code;
    }

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
     * 获取描述.
     *
     * @return 描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置描述.
     *
     * @param description 描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取系统预置标记.
     *
     * @return 1=预置，0=自定义
     */
    public Integer getIsSystem() {
        return isSystem;
    }

    /**
     * 设置系统预置标记.
     *
     * @param isSystem 1=预置，0=自定义
     */
    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    /**
     * 获取创建人 ID.
     *
     * @return 创建人 ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建人 ID.
     *
     * @param createdBy 创建人 ID
     */
    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
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
