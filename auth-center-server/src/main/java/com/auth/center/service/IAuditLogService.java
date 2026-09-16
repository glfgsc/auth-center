package com.auth.center.service;

import com.auth.center.entity.AuditLog;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 统一活动审计服务 —— 写入与查询全平台「谁改了什么」的活动审计。
 *
 * 写入来源:认证中心自身({@link com.auth.center.aspect.SetupAuditAspect})+ BI(ingest 端点)。查询供管理员审计控制台按来源 /
 * 操作人 / 模块 / 操作 / 状态 / 时间分页检索。AI 信任遥测是不同域,见 {@link IAiTrustLogService}。
 */
public interface IAuditLogService {

    /**
     * 记录一条活动审计。
     *
     * @param log 审计记录
     */
    void record(AuditLog log);

    /**
     * 分页查询(按发生时间倒序)。
     *
     * @param source 来源系统精确过滤(可空)
     * @param actor 操作人用户名模糊过滤(可空)
     * @param module 模块精确过滤(可空)
     * @param operationType 操作类型精确过滤(可空)
     * @param status 状态分类:{@code success}=200,{@code failed}=非 200
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @param page 页码(从 1 起)
     * @param size 每页条数
     * @return 当前页记录
     */
    List<AuditLog> query(
            String source,
            String actor,
            String module,
            String operationType,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size);

    /**
     * 统计符合过滤条件的总条数。
     *
     * @param source 来源系统精确过滤(可空)
     * @param actor 操作人用户名模糊过滤(可空)
     * @param module 模块精确过滤(可空)
     * @param operationType 操作类型精确过滤(可空)
     * @param status 状态分类(同 {@link #query})
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @return 总条数
     */
    long count(
            String source,
            String actor,
            String module,
            String operationType,
            String status,
            LocalDateTime start,
            LocalDateTime end);
}
