package com.auth.center.service.impl;

import com.auth.center.entity.AuditLog;
import com.auth.center.mapper.AuditLogMapper;
import com.auth.center.service.IAuditLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link IAuditLogService} 默认实现。 */
@Service
public class AuditLogServiceImpl implements IAuditLogService {

    /** 成功状态码 —— 与 {@code Result.SUCCESS_CODE} 一致。 */
    private static final int STATUS_SUCCESS = 200;

    /** 每页条数上限 —— 防超大分页拉垮查询。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuditLogMapper auditMapper;

    /**
     * 构造注入。
     *
     * @param auditMapper 统一活动审计 Mapper
     */
    public AuditLogServiceImpl(AuditLogMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    public void record(AuditLog log) {
        auditMapper.insert(log);
    }

    @Override
    public List<AuditLog> query(
            String source,
            String actor,
            String module,
            String operationType,
            String status,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * safeSize;

        LambdaQueryWrapper<AuditLog> wrapper =
                buildWrapper(source, actor, module, operationType, status, start, end)
                        .orderByDesc(AuditLog::getCreatedAt)
                        .last("LIMIT " + offset + ", " + safeSize);
        return auditMapper.selectList(wrapper);
    }

    @Override
    public long count(
            String source,
            String actor,
            String module,
            String operationType,
            String status,
            LocalDateTime start,
            LocalDateTime end) {
        return auditMapper.selectCount(
                buildWrapper(source, actor, module, operationType, status, start, end));
    }

    /**
     * 构造过滤条件 —— query 与 count 共用,保证一致。
     *
     * @param source 来源系统精确过滤(可空)
     * @param actor 操作人用户名模糊过滤(可空)
     * @param module 模块精确过滤(可空)
     * @param operationType 操作类型精确过滤(可空)
     * @param status 状态分类:{@code success}=200,{@code failed}=非 200
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @return 查询包装器
     */
    private LambdaQueryWrapper<AuditLog> buildWrapper(
            String source,
            String actor,
            String module,
            String operationType,
            String status,
            LocalDateTime start,
            LocalDateTime end) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (source != null && !source.isBlank()) {
            wrapper.eq(AuditLog::getSourceSystem, source.trim());
        }
        if (actor != null && !actor.isBlank()) {
            wrapper.like(AuditLog::getActorUsername, actor.trim());
        }
        if (module != null && !module.isBlank()) {
            wrapper.eq(AuditLog::getModule, module.trim());
        }
        if (operationType != null && !operationType.isBlank()) {
            wrapper.eq(AuditLog::getOperationType, operationType.trim());
        }
        if ("success".equalsIgnoreCase(status)) {
            wrapper.eq(AuditLog::getStatus, STATUS_SUCCESS);
        } else if ("failed".equalsIgnoreCase(status)) {
            wrapper.ne(AuditLog::getStatus, STATUS_SUCCESS);
        }
        if (start != null) {
            wrapper.ge(AuditLog::getCreatedAt, start);
        }
        if (end != null) {
            wrapper.le(AuditLog::getCreatedAt, end);
        }
        return wrapper;
    }
}
