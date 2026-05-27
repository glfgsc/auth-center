package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户组成员关联实体.
 *
 * <p>对应数据库表 {@code auth_group_member}，记录用户属于哪些组。
 * {@link #username}、{@link #nickname}、{@link #email} 是 JOIN 查询时填充的瞬态字段，
 * 不持久化到数据库。</p>
 */
@TableName("auth_group_member")
public class AuthGroupMember {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** FK -> auth_group.id */
    private Long groupId;

    /** FK -> auth_user.id */
    private Long userId;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 用户名（JOIN 填充，非持久化） */
    @TableField(exist = false)
    private String username;

    /** 昵称（JOIN 填充，非持久化） */
    @TableField(exist = false)
    private String nickname;

    /** 邮箱（JOIN 填充，非持久化） */
    @TableField(exist = false)
    private String email;

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
     * 获取组 ID.
     *
     * @return 组 ID
     */
    public Long getGroupId() {
        return groupId;
    }

    /**
     * 设置组 ID.
     *
     * @param groupId 组 ID
     */
    public void setGroupId(Long groupId) {
        this.groupId = groupId;
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
     * 获取用户名（JOIN 填充）.
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
     * 获取昵称（JOIN 填充）.
     *
     * @return 昵称
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 设置昵称.
     *
     * @param nickname 昵称
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * 获取邮箱（JOIN 填充）.
     *
     * @return 邮箱
     */
    public String getEmail() {
        return email;
    }

    /**
     * 设置邮箱.
     *
     * @param email 邮箱
     */
    public void setEmail(String email) {
        this.email = email;
    }
}
