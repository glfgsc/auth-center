package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 认证用户实体.
 *
 * <p>对应数据库表 {@code auth_user}，存储系统用户的基础认证信息。
 * 密码字段使用 BCrypt 加密存储，序列化时仅允许写入不允许读出。</p>
 */
@TableName("auth_user")
public class AuthUser {

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名，唯一 */
    private String username;

    /** 密码（BCrypt 哈希），仅允许反序列化写入 */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 创建时间，插入时自动填充 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间，插入和更新时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 逻辑删除标记：0=未删除，1=已删除 */
    @TableLogic
    private Integer deleted;

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
     * 获取密码（BCrypt 哈希）.
     *
     * @return 密码哈希
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置密码（BCrypt 哈希）.
     *
     * @param password 密码哈希
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 获取昵称.
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
     * 获取头像 URL.
     *
     * @return 头像 URL
     */
    public String getAvatar() {
        return avatar;
    }

    /**
     * 设置头像 URL.
     *
     * @param avatar 头像 URL
     */
    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    /**
     * 获取邮箱.
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

    /**
     * 获取手机号.
     *
     * @return 手机号
     */
    public String getPhone() {
        return phone;
    }

    /**
     * 设置手机号.
     *
     * @param phone 手机号
     */
    public void setPhone(String phone) {
        this.phone = phone;
    }

    /**
     * 获取创建时间.
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间.
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间.
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间.
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    /**
     * 获取逻辑删除标记.
     *
     * @return 0=未删除，1=已删除
     */
    public Integer getDeleted() {
        return deleted;
    }

    /**
     * 设置逻辑删除标记.
     *
     * @param deleted 0=未删除，1=已删除
     */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
