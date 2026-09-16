package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AiTrustLog;
import com.auth.center.service.IAiTrustLogService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 信任日志查询端点 —— 管理员审计控制台的「AI 信任日志」板块。
 *
 * 仅管理员可读({@code @authPerm.isAdmin()});写入由 agent-server 经 {@link AiTrustIngestController}
 * 推送。活动审计板块见 {@link AuditController}。
 */
@RestController
@RequestMapping("/api/auth/admin/audit/trust")
@PreAuthorize("@authPerm.isAdmin()")
public class AiTrustController {

    private final IAiTrustLogService trustLogService;

    /**
     * 构造注入。
     *
     * @param trustLogService AI 信任遥测服务
     */
    public AiTrustController(IAiTrustLogService trustLogService) {
        this.trustLogService = trustLogService;
    }

    /**
     * 分页查询 AI 信任遥测(按发生时间倒序)。
     *
     * @param agentKey 智能体 key 模糊过滤(可空)
     * @param source 上报方精确过滤(RUNTIME / BI_UTILITY,可空)—— 与 {@code systemCode} 是正交的两个维度
     * @param systemCode 产品精确过滤(可空);上报方未带产品标识的历史行按产品筛时不出现
     * @param blockedOnly 仅看被拦截的(可空)
     * @param page 页码,默认 1
     * @param size 每页条数,默认 20
     * @return {@code {records, total, page, size}}
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String agentKey,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) Boolean blockedOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<AiTrustLog> records =
                trustLogService.query(agentKey, source, systemCode, blockedOnly, page, size);
        long total = trustLogService.count(agentKey, source, systemCode, blockedOnly);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }

    /**
     * 一轮的三层明细 —— 轮级那一行之外,还有每次模型调用与每条检测判定。
     *
     * 这是「模型看到的 vs 用户看到的」并排对照的入口:{@code generations} 里逐次的 {@code promptText} /
     * {@code rawResponse} 是模型那一侧,轮级的 {@code responseText} 是用户那一侧,两边一眼可对。
     *
     * 正文按留存策略给:{@code textRetention=DIGEST} 的行只有字数没有全文(见 {@code auth_ai_generation}
     * 的建表说明)—— 前端据此说清是「按策略没留」而不是「没采到」。
     *
     * @param requestId 轮次标识(= 产品侧 turn_id)
     * @return {@code {generations, signals}};查无此轮时两者皆空列表
     */
    @GetMapping("/{requestId}/detail")
    public Result<Map<String, Object>> detail(@PathVariable String requestId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generations", trustLogService.generationsOf(requestId));
        data.put("signals", trustLogService.signalsOf(requestId));
        return Result.ok(data);
    }
}
