package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuditLog;
import com.auth.center.service.IAuditLogService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统一活动审计查询端点 —— 管理员审计控制台的「活动审计」板块(认证中心自身 + BI)。
 *
 * 仅管理员可读({@code @authPerm.isAdmin()});写入由 {@link com.auth.center.aspect.SetupAuditAspect}(中心自身)与
 * {@link AuditIngestController}(BI 推送)完成。 AI 信任遥测板块见 {@link AiTrustController}。
 */
@RestController
@RequestMapping("/api/auth/admin/audit")
@PreAuthorize("@authPerm.isAdmin()")
public class AuditController {

    private final IAuditLogService auditLogService;

    /**
     * 构造注入。
     *
     * @param auditLogService 统一活动审计服务
     */
    public AuditController(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 分页查询活动审计(按发生时间倒序)。
     *
     * @param source 来源系统精确过滤(auth_center / bi,可空)
     * @param actor 操作人用户名模糊过滤(可空)
     * @param module 模块精确过滤(可空)
     * @param operationType 操作类型精确过滤(可空)
     * @param status 状态分类:{@code success} / {@code failed},其它值不过滤
     * @param startTime 起始时间(可空,格式 {@code yyyy-MM-dd HH:mm:ss})
     * @param endTime 截止时间(可空,格式 {@code yyyy-MM-dd HH:mm:ss})
     * @param page 页码,默认 1
     * @param size 每页条数,默认 20
     * @return {@code {records, total, page, size}}
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<AuditLog> records =
                auditLogService.query(
                        source,
                        actor,
                        module,
                        operationType,
                        status,
                        startTime,
                        endTime,
                        page,
                        size);
        long total =
                auditLogService.count(
                        source, actor, module, operationType, status, startTime, endTime);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }
}
