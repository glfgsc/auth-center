package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 权限集实体.
 *
 * 对应数据库表 {@code auth_permission_set}，每条记录代表一个权限集合（角色），如 admin / platform_analyst /
 * self_service_analyst / viewer。
 *
 * {@code capabilities} 字段以 JSON 数组形式存储该权限集包含的能力码列表，例如 {@code
 * ["dashboard:view","dashboard:edit","dataset:create"]}。
 */
@TableName("auth_permission_set")
public class PermissionSet {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 权限集编码，唯一（admin / platform_analyst / self_service_analyst / viewer） */
    @NotBlank(message = "编码不能为空")
    @Size(max = 50, message = "编码长度不能超过 50")
    private String code;

    /** 归属系统编码：某系统码（如 bi / tracking）或 'global'（跨系统通用，如超管） */
    private String systemCode;

    /** 权限集名称 */
    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称长度不能超过 100")
    private String name;

    /** 权限集描述 */
    private String description;

    /** 能力码 JSON 数组，如 ["dashboard:view","dataset:create"] */
    private String capabilities;

    /** 是否系统预设：1=预设，0=自定义 */
    private Integer isSystem;

    /** 排序序号 */
    private Integer sortOrder;

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
     * 获取权限集编码.
     *
     * @return 权限集编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置权限集编码.
     *
     * @param code 权限集编码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取归属系统编码.
     *
     * @return 系统编码或 'global'
     */
    public String getSystemCode() {
        return systemCode;
    }

    /**
     * 设置归属系统编码.
     *
     * @param systemCode 系统编码或 'global'
     */
    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    /**
     * 获取权限集名称.
     *
     * @return 权限集名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置权限集名称.
     *
     * @param name 权限集名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取权限集描述.
     *
     * @return 权限集描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置权限集描述.
     *
     * @param description 权限集描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取能力码 JSON 数组.
     *
     * @return 能力码 JSON 字符串
     */
    public String getCapabilities() {
        return capabilities;
    }

    /**
     * 设置能力码 JSON 数组.
     *
     * @param capabilities 能力码 JSON 字符串
     */
    public void setCapabilities(String capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * 获取是否系统预设标记.
     *
     * @return 1=预设，0=自定义
     */
    public Integer getIsSystem() {
        return isSystem;
    }

    /**
     * 设置是否系统预设标记.
     *
     * @param isSystem 1=预设，0=自定义
     */
    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    /**
     * 获取排序序号.
     *
     * @return 排序序号
     */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /**
     * 设置排序序号.
     *
     * @param sortOrder 排序序号
     */
    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
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
