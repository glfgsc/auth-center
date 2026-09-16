package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 已接入平台/系统注册表实体.
 *
 * 对应数据库表 {@code auth_system}，登记认证中心已接入的各子系统（BI 平台、Agent 平台、埋点平台、认证中心、全局等）。权限集与用户绑定的 {@code
 * system_code} 均对齐此表的 {@code code}。
 *
 * 管理台「用户与权限」页据此动态渲染系统分组（不再前端硬编码系统白名单），新增平台只需在此登记一行即自动出现。
 */
@TableName("auth_system")
public class AuthSystem {

    /**
     * 全局档的系统编码 —— 不属于任何单一产品的那一档。
     *
     * 登录设置以它为兜底档（产品没有自己那一行时回落到它）；用户组以它表示全平台组。各处按产品分区的代码都引这里，不要再各写一份字面量。
     */
    public static final String CODE_GLOBAL = "global";

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统编码，与权限集/绑定的 system_code 对齐，如 "bi"、"agent"、"global" */
    private String code;

    /** 系统展示名（前端缺 i18n 时回退） */
    private String name;

    /** 管理台展示顺序（升序） */
    private Integer sortOrder;

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
     * 获取系统编码.
     *
     * @return 系统编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 设置系统编码.
     *
     * @param code 系统编码
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * 获取系统展示名.
     *
     * @return 系统展示名
     */
    public String getName() {
        return name;
    }

    /**
     * 设置系统展示名.
     *
     * @param name 系统展示名
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取展示顺序.
     *
     * @return 展示顺序
     */
    public Integer getSortOrder() {
        return sortOrder;
    }

    /**
     * 设置展示顺序.
     *
     * @param sortOrder 展示顺序
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
}
