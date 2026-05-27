package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户-权限集关联实体.
 *
 * <p>对应数据库表 {@code auth_user_permission_set}，记录用户与权限集的绑定关系。
 * 每个用户关联一个权限集。</p>
 */
@TableName("auth_user_permission_set")
public class UserPermissionSet {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 权限集 ID */
    private Long permissionSetId;

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
     * 获取用户 ID.
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 ID.
     *
     * @param userId 用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取权限集 ID.
     *
     * @return 权限集 ID
     */
    public Long getPermissionSetId() {
        return permissionSetId;
    }

    /**
     * 设置权限集 ID.
     *
     * @param permissionSetId 权限集 ID
     */
    public void setPermissionSetId(Long permissionSetId) {
        this.permissionSetId = permissionSetId;
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
