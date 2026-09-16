package com.auth.center.entity;

import com.auth.center.security.crypto.EncryptedStringTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 通知渠道凭据 -- 机器人 webhook、SMTP 口令等静态凭据的唯一落点.
 *
 * 洞察的订阅摘要与指标告警、知数的流程通知步骤都按 {@code credKey} 引用它,各产品自己不再存地址.
 *
 * {@code autoResultMap = true} 不能删:少了它 SELECT 侧不会应用 {@link EncryptedStringTypeHandler},
 * 表现成写进去是密文、读出来还是密文,而写入路径看着完全正常.
 *
 * @see com.auth.center.security.crypto.EncryptedStringTypeHandler
 */
@TableName(value = "auth_notification_credential", autoResultMap = true)
public class NotificationCredential {

    /** 主键 ID, 自增. */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属产品: {@code bi}=洞察 / {@code agent}=知数;取自 /admin/systems 注册表. */
    private String targetSystem;

    /** 引用名,告警与订阅以它引用;同产品内唯一. */
    private String credKey;

    /** 渠道类型: DINGTALK_BOT / LARK_BOT / WECOM_BOT / WECOM_APP / SMTP / HTTP_BEARER / HTTP_BASIC. */
    private String credType;

    /** 显示名. */
    private String displayName;

    /** 备注,如这个群是干什么的. */
    private String description;

    /**
     * 秘密 JSON -- 地址、加签密钥、口令全在里面,经 AES-256-GCM 落库,读回一律掩码.
     *
     * webhook 地址本身就是凭据(企微 {@code ?key=} / 飞书 {@code /hook/<token>} / 钉钉 {@code ?access_token=}),
     * 故整体加密而不是只加密"密钥"字段.
     */
    @TableField(typeHandler = EncryptedStringTypeHandler.class)
    private String encryptedSecretJson;

    /**
     * 作用域引用,由 {@link #targetSystem} 自行解释 -- 认证中心只做字符串匹配,永不解析语义.
     *
     * 洞察写 {@code workspace:0}(全站)或 {@code workspace:5}; 知数暂时写 null(全局可用). 把 BI 的 workspace_id
     * 逐字搬进认证中心是抽象泄漏 --「工作区」是洞察特有的概念,知数没有.
     */
    private String scopeRef;

    /** 停用后不下发,引用它的告警与订阅会投递失败. */
    private Integer enabled;

    /** 最近一次连通性测试: OK / FAILED. */
    private String lastTestStatus;

    /** 最近一次连通性测试时间. */
    private LocalDateTime lastTestAt;

    /** 测试结果说明;不得写入完整 URL -- 地址本身是凭据. */
    private String lastTestMessage;

    /** 密钥最近一次被整体替换的时间. */
    private LocalDateTime lastRotatedAt;

    /** 创建者用户 ID. */
    private Long createdBy;

    /** 创建时间,交给库的默认值. */
    private LocalDateTime createdAt;

    /** 更新时间,交给库的默认值. */
    private LocalDateTime updatedAt;

    /** 逻辑删除标记. */
    @TableLogic private Integer deleted;

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
     * 获取归属产品.
     *
     * @return 归属产品
     */
    public String getTargetSystem() {
        return targetSystem;
    }

    /**
     * 设置归属产品.
     *
     * @param targetSystem 归属产品
     */
    public void setTargetSystem(String targetSystem) {
        this.targetSystem = targetSystem;
    }

    /**
     * 获取引用名.
     *
     * @return 引用名
     */
    public String getCredKey() {
        return credKey;
    }

    /**
     * 设置引用名.
     *
     * @param credKey 引用名
     */
    public void setCredKey(String credKey) {
        this.credKey = credKey;
    }

    /**
     * 获取渠道类型.
     *
     * @return 渠道类型
     */
    public String getCredType() {
        return credType;
    }

    /**
     * 设置渠道类型.
     *
     * @param credType 渠道类型
     */
    public void setCredType(String credType) {
        this.credType = credType;
    }

    /**
     * 获取显示名.
     *
     * @return 显示名
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 设置显示名.
     *
     * @param displayName 显示名
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取备注.
     *
     * @return 备注
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置备注.
     *
     * @param description 备注
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取秘密 JSON(已解密).
     *
     * @return 秘密 JSON
     */
    public String getEncryptedSecretJson() {
        return encryptedSecretJson;
    }

    /**
     * 设置秘密 JSON(明文,落库时自动加密).
     *
     * @param encryptedSecretJson 秘密 JSON
     */
    public void setEncryptedSecretJson(String encryptedSecretJson) {
        this.encryptedSecretJson = encryptedSecretJson;
    }

    /**
     * 获取作用域引用.
     *
     * @return 作用域引用
     */
    public String getScopeRef() {
        return scopeRef;
    }

    /**
     * 设置作用域引用.
     *
     * @param scopeRef 作用域引用
     */
    public void setScopeRef(String scopeRef) {
        this.scopeRef = scopeRef;
    }

    /**
     * 获取启用状态.
     *
     * @return 启用状态
     */
    public Integer getEnabled() {
        return enabled;
    }

    /**
     * 设置启用状态.
     *
     * @param enabled 启用状态
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取最近测试状态.
     *
     * @return 最近测试状态
     */
    public String getLastTestStatus() {
        return lastTestStatus;
    }

    /**
     * 设置最近测试状态.
     *
     * @param lastTestStatus 最近测试状态
     */
    public void setLastTestStatus(String lastTestStatus) {
        this.lastTestStatus = lastTestStatus;
    }

    /**
     * 获取最近测试时间.
     *
     * @return 最近测试时间
     */
    public LocalDateTime getLastTestAt() {
        return lastTestAt;
    }

    /**
     * 设置最近测试时间.
     *
     * @param lastTestAt 最近测试时间
     */
    public void setLastTestAt(LocalDateTime lastTestAt) {
        this.lastTestAt = lastTestAt;
    }

    /**
     * 获取测试结果说明.
     *
     * @return 测试结果说明
     */
    public String getLastTestMessage() {
        return lastTestMessage;
    }

    /**
     * 设置测试结果说明.
     *
     * @param lastTestMessage 测试结果说明
     */
    public void setLastTestMessage(String lastTestMessage) {
        this.lastTestMessage = lastTestMessage;
    }

    /**
     * 获取密钥最近轮换时间.
     *
     * @return 密钥最近轮换时间
     */
    public LocalDateTime getLastRotatedAt() {
        return lastRotatedAt;
    }

    /**
     * 设置密钥最近轮换时间.
     *
     * @param lastRotatedAt 密钥最近轮换时间
     */
    public void setLastRotatedAt(LocalDateTime lastRotatedAt) {
        this.lastRotatedAt = lastRotatedAt;
    }

    /**
     * 获取创建者用户 ID.
     *
     * @return 创建者用户 ID
     */
    public Long getCreatedBy() {
        return createdBy;
    }

    /**
     * 设置创建者用户 ID.
     *
     * @param createdBy 创建者用户 ID
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

    /**
     * 获取逻辑删除标记.
     *
     * @return 逻辑删除标记
     */
    public Integer getDeleted() {
        return deleted;
    }

    /**
     * 设置逻辑删除标记.
     *
     * @param deleted 逻辑删除标记
     */
    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
