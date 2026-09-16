package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuditLog;
import com.auth.center.service.IAuditLogService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动审计内部汇入端点 —— 服务间调用,接收各子系统(BI 等)推送的活动审计并落统一表。
 *
 * 调用方:BI 各服务的审计切面(经 {@code auth.center.url} 直连,不走网关)。网关剥除外部伪造的 {@code
 * X-Internal-Service-Token},故公网不可达;端点内再常量时间校验令牌 fail-closed。best-effort: 上游异步
 * fire-and-forget,中心不可达即丢弃,不阻塞业务。来源经 {@code X-Audit-Source} 头标记 (默认 bi)。AI 信任遥测走另一端点 {@link
 * AiTrustIngestController}。
 */
@RestController
@RequestMapping("/api/auth/audit/internal")
public class AuditIngestController {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestController.class);

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";
    private static final String HEADER_SOURCE = "X-Audit-Source";

    private final IAuditLogService auditLogService;

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入。
     *
     * @param auditLogService 统一活动审计服务
     */
    public AuditIngestController(IAuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /**
     * 汇入请求体 —— 字段名与 BI 的 {@code AuditLogDTO} 一致,Jackson 直接反序列化。
     *
     * @param username 操作人用户名
     * @param module 模块
     * @param operation 操作描述
     * @param operationType 操作类型
     * @param method HTTP 方法
     * @param params 请求参数(脱敏后)
     * @param result 响应结果
     * @param oldValue 变更前旧值
     * @param newValue 变更后新值
     * @param diffResult 变更差异描述
     * @param targetKind 目标资产类型
     * @param targetId 目标资产 id
     * @param tenantId 租户 id
     * @param workspaceId 工作区 id
     * @param sensitiveOp 敏感操作标记
     * @param ip 客户端 IP
     * @param userAgent User-Agent
     * @param duration 耗时(毫秒)
     * @param status 结果状态
     * @param errorMsg 错误信息
     * @param createTime 发生时间
     */
    public record AuditIngestRequest(
            String username,
            String module,
            String operation,
            String operationType,
            String method,
            String params,
            String result,
            String oldValue,
            String newValue,
            String diffResult,
            String targetKind,
            Long targetId,
            Long tenantId,
            Long workspaceId,
            Integer sensitiveOp,
            String ip,
            String userAgent,
            Long duration,
            Integer status,
            String errorMsg,
            LocalDateTime createTime) {}

    /**
     * 汇入一条活动审计。
     *
     * @param internalToken 内部服务令牌
     * @param source 来源系统({@code X-Audit-Source} 头,默认 bi)
     * @param req 审计数据
     * @return 成功;内部令牌无效 403
     */
    @PostMapping("/ingest")
    public Result<Void> ingest(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @RequestHeader(value = HEADER_SOURCE, required = false, defaultValue = "bi")
                    String source,
            @RequestBody AuditIngestRequest req) {

        if (!validInternalToken(internalToken)) {
            log.warn("[AuditIngest] rejected — invalid or missing internal service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }

        AuditLog entity = new AuditLog();
        entity.setSourceSystem(source);
        entity.setActorUsername(req.username());
        entity.setModule(req.module());
        entity.setOperation(req.operation());
        entity.setOperationType(req.operationType());
        entity.setMethod(req.method());
        entity.setParams(req.params());
        entity.setResult(req.result());
        entity.setOldValue(req.oldValue());
        entity.setNewValue(req.newValue());
        entity.setDiffResult(req.diffResult());
        entity.setTargetType(req.targetKind());
        entity.setTargetId(req.targetId() != null ? String.valueOf(req.targetId()) : null);
        entity.setTenantId(req.tenantId());
        entity.setWorkspaceId(req.workspaceId());
        entity.setSensitiveOp(req.sensitiveOp());
        entity.setIp(req.ip());
        entity.setUserAgent(req.userAgent());
        entity.setDurationMs(req.duration());
        entity.setStatus(req.status());
        entity.setErrorMsg(req.errorMsg());
        entity.setCreatedAt(req.createTime() != null ? req.createTime() : LocalDateTime.now());

        try {
            auditLogService.record(entity);
        } catch (Exception e) {
            // 汇入失败不回传错误(上游 fire-and-forget);仅告警,避免拖累推送方。
            log.warn("[AuditIngest] persist failed (source={}): {}", source, e.getMessage());
        }
        return Result.ok();
    }

    /**
     * 常量时间比对内部服务令牌。
     *
     * @param provided 请求头携带的令牌(可空)
     * @return 相等返回 true
     */
    private boolean validInternalToken(String provided) {
        if (provided == null || internalServiceToken == null || internalServiceToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
