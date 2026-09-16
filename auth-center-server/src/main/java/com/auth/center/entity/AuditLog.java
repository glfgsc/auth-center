package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 统一活动审计记录 —— 认证中心汇集全平台「谁改了什么」的活动审计。
 *
 * 字段为 BI {@code sys_audit_log} ∪ 认证中心 setup 审计的超集,{@code sourceSystem} 区分来源 (auth_center 自身管理变更
 * / bi 平台 CRUD)。认证中心自身由 {@link com.auth.center.aspect.SetupAuditAspect} 写入,BI 经 ingest 端点推送。AI
 * 信任层遥测是不同域,见 {@link AiTrustLog}。
 */
@TableName("auth_audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源系统: auth_center / bi。 */
    private String sourceSystem;

    /** 操作人用户 id(可空,BI 侧只带 username)。 */
    private Long actorUserId;

    /** 操作人用户名。 */
    private String actorUsername;

    /** 模块。 */
    private String module;

    /** 操作描述。 */
    private String operation;

    /** 操作类型: create/update/delete/query/export/...。 */
    private String operationType;

    /** HTTP 方法。 */
    private String method;

    /** 请求路径。 */
    private String path;

    /** 请求参数 / 变更载荷(脱敏后)。 */
    private String params;

    /** 响应结果(可能截断)。 */
    private String result;

    /** 变更前旧值。 */
    private String oldValue;

    /** 变更后新值。 */
    private String newValue;

    /** 变更差异描述。 */
    private String diffResult;

    /** 目标资产类型。 */
    private String targetType;

    /** 目标资产 id。 */
    private String targetId;

    /** 目标名称。 */
    private String targetName;

    /** 租户 id(BI 隔离用)。 */
    private Long tenantId;

    /** 工作区 id(BI)。 */
    private Long workspaceId;

    /** 敏感操作标记(1=敏感)。 */
    private Integer sensitiveOp;

    /** 客户端 IP。 */
    private String ip;

    /** User-Agent。 */
    private String userAgent;

    /** 结果状态(200 成功)。 */
    private Integer status;

    /** 错误信息。 */
    private String errorMsg;

    /** 耗时(毫秒)。 */
    private Long durationMs;

    /** 发生时间。为空时由 MetaObjectHandler 兜底填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId) {
        this.actorUserId = actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.params = params;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getDiffResult() {
        return diffResult;
    }

    public void setDiffResult(String diffResult) {
        this.diffResult = diffResult;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    public Integer getSensitiveOp() {
        return sensitiveOp;
    }

    public void setSensitiveOp(Integer sensitiveOp) {
        this.sensitiveOp = sensitiveOp;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
