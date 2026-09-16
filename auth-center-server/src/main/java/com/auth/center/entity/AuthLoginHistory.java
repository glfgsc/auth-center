package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 登录历史 —— 每一次登录尝试(成功 + 失败)的留痕,支撑登录安全监控与异常检测.
 *
 * 记录谁、何时、从哪个 IP / 设备 / 地点尝试登录、成功与否、以及检出的异常标记。与「会话管理」(Redis 活跃会话)互补:会话记「谁在线」,本表记「谁尝试过、是否异常」。
 */
@TableName("auth_login_history")
public class AuthLoginHistory {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id(用户不存在的失败尝试为空)。 */
    private Long userId;

    /** 登录用户名(尝试值)。 */
    private String username;

    /**
     * 来源产品，对齐 {@code auth_system.code}。
     *
     * {@code null} = 调用方登录时未报来源产品（存量行，以及尚未接入产品维度的登录页）。按产品筛选时这些行不出现 —— 是如实反映，不是丢数据。
     */
    private String systemCode;

    /** 登录时间。 */
    private LocalDateTime loginTime;

    /** 客户端 IP。 */
    private String ip;

    /** IP 归类:INTERNAL / PUBLIC。 */
    private String ipClass;

    /** 精确地理位置(公网经 GeoIP 解析,可空)。 */
    private String location;

    /** User-Agent 原串。 */
    private String userAgent;

    /** 结果:SUCCESS / FAILED。 */
    private String status;

    /** 失败原因(可空)。 */
    private String failReason;

    /** 成功时的会话 id(= refresh 族 id)。 */
    private String sessionId;

    /** 异常标记,逗号分隔(NEW_IP / NEW_DEVICE / FAILED_BURST / CONCURRENT_LOCATION)。 */
    private String anomalies;

    /** 租户 id。 */
    private Long tenantId;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIpClass() {
        return ipClass;
    }

    public void setIpClass(String ipClass) {
        this.ipClass = ipClass;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(String anomalies) {
        this.anomalies = anomalies;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
