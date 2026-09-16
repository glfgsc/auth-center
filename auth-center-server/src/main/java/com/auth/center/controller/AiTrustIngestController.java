package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AiContentSignal;
import com.auth.center.entity.AiGeneration;
import com.auth.center.entity.AiTrustLog;
import com.auth.center.service.IAiTrustLogService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
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
 * AI 信任遥测内部汇入端点 —— 接收 agent-server 推送的一轮 LLM 留痕并落三张表。
 *
 * 三层各归各表:轮级事实进 {@link AiTrustLog},每次模型调用进 {@link AiGeneration},每条检测判定进
 * {@link AiContentSignal}。此前逐次用量与检测结果都塞在轮级那一行的 JSON 列里,导致「上周有多少次结论没通过数字溯源」
 * 这类问题得全表扫 JSON —— 而那正是效果评估天天要问的。
 *
 * 调用方:agent-server 的信任层遥测接收器(经 {@code auth.center.url} 直连,不走网关)。端点内常量时间校验
 * {@code X-Internal-Service-Token} fail-closed;best-effort,失败不回传错误。活动审计走另一端点
 * {@link AuditIngestController}。
 */
@RestController
@RequestMapping("/api/auth/audit/internal")
public class AiTrustIngestController {

    private static final Logger log = LoggerFactory.getLogger(AiTrustIngestController.class);

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final IAiTrustLogService trustLogService;

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入。
     *
     * @param trustLogService AI 信任遥测服务
     */
    public AiTrustIngestController(IAiTrustLogService trustLogService) {
        this.trustLogService = trustLogService;
    }

    /**
     * 汇入请求体 —— 字段名与 agent 的 {@code AgentLlmAudit} 一致。
     *
     * @param requestId 请求 id(= 产品侧该轮 turn_id;与调用级、信号、智能体侧遥测全部靠它对齐)
     * @param sessionId 产品侧会话 ID(可空;账本行凭它链回产品的会话回放页)
     * @param agentKey 智能体 key
     * @param source 调用来源(RUNTIME / BI_UTILITY)
     * @param userId 终端用户 id
     * @param questionText 用户问题(已掩码)。不是模型收到的提示词 —— 后者一轮 N 份,在 {@code generations} 里
     * @param responseText 交付给用户那份(已去掩码)
     * @param blocked 是否拦截。必须是 {@code Boolean}:上游 {@code AgentLlmAudit.blocked} 序列化出的是 JSON
     *     {@code false}/{@code true},声明成 {@code Integer} 会让 Jackson 拒绝强转 → 整个请求 400;而转推器是
     *     best-effort、用 {@code BodyHandlers.discarding()} 且不看状态码,这个 400 会被完全吞掉,遥测静默全丢、两侧日志都干净
     * @param blockReason 拦截原因
     * @param groundingSource 接地来源
     * @param latencyMs 时延(毫秒)
     * @param provider LLM 提供方
     * @param model 模型
     * @param systemCode 产品标识(bi / tracking 等);空 = 归入未知产品
     * @param inputTokens 整轮输入 token(含缓存命中部分)
     * @param outputTokens 整轮输出 token(含思考部分)
     * @param reasoningTokens 其中思考 token,是输出的子集
     * @param cachedInputTokens 其中命中缓存的输入 token,是输入的子集
     * @param llmCallCount 本轮实际模型调用次数(已记账的)
     * @param llmHttpAttempts 本轮真实出网次数(含 SDK 重试与非 2xx)
     * @param llmFailedAttempts 其中非 2xx 的次数
     * @param generations 本轮各次模型调用
     * @param signals 本轮各检测器判定
     * @param createdAt 发生时间
     */
    public record TrustIngestRequest(
            String requestId,
            String sessionId,
            String agentKey,
            String source,
            Long userId,
            String questionText,
            String responseText,
            Boolean blocked,
            String blockReason,
            String groundingSource,
            Long latencyMs,
            String provider,
            String model,
            String systemCode,
            Integer inputTokens,
            Integer outputTokens,
            Integer reasoningTokens,
            Integer cachedInputTokens,
            Integer llmCallCount,
            Integer llmHttpAttempts,
            Integer llmFailedAttempts,
            List<GenerationPayload> generations,
            List<SignalPayload> signals,
            LocalDateTime createdAt) {}

    /**
     * 一次模型调用。
     *
     * @param seq 本轮第几次(1 起)。标识由服务端按 {@code requestId-seq} 派生,上游不必造 id ——
     *     重复上报因此天然幂等,同一次调用不会在账上出现两次把用量算重
     * @param provider LLM 提供方
     * @param model 模型
     * @param modelRole 模型角色(reasoning / fast 等)
     * @param promptText 送给模型那份(已掩码);按 {@code textRetention} 决定是否带全文
     * @param rawResponse 模型原样返回
     * @param textRetention {@code FULL} 留全文 / {@code DIGEST} 只留摘要与长度
     * @param promptChars 提示词字数(不留全文时照样给 —— 「没采到」与「按策略没留」靠它分开)
     * @param responseChars 返回字数
     * @param inputTokens 本次输入 token
     * @param outputTokens 本次输出 token
     * @param reasoningTokens 其中思考 token
     * @param cachedInputTokens 其中命中缓存的输入 token
     * @param providerRequestId 服务商请求 ID
     * @param streamed 是否流式
     * @param latencyMs 本次耗时
     * @param finishReason 结束原因;{@code length} 意味着输出被截断
     * @param errorType 本次失败的异常类型
     * @param stepId 对应智能体侧 {@code agent_interaction_step.id}
     */
    public record GenerationPayload(
            Integer seq,
            String provider,
            String model,
            String modelRole,
            String promptText,
            String rawResponse,
            String textRetention,
            Integer promptChars,
            Integer responseChars,
            Integer inputTokens,
            Integer outputTokens,
            Integer reasoningTokens,
            Integer cachedInputTokens,
            String providerRequestId,
            Boolean streamed,
            Long latencyMs,
            String finishReason,
            String errorType,
            Long stepId) {}

    /**
     * 一条检测判定。
     *
     * @param detectorType 检测器(见 {@link AiContentSignal} 的常量)
     * @param contentType {@code INPUT} 用户侧 / {@code OUTPUT} 模型侧
     * @param category 细分类别
     * @param valueNum 0–1 的分(打分型检测器)
     * @param valueLabel 三值档(判档型检测器)
     * @param detail 判定说理 / 命中项
     * @param generationSeq 属于第几次调用;轮级判定留空(如整轮的限定落位对账不属于某一次调用)
     */
    public record SignalPayload(
            String detectorType,
            String contentType,
            String category,
            BigDecimal valueNum,
            String valueLabel,
            String detail,
            Integer generationSeq) {}

    /**
     * 汇入一轮 AI 信任遥测。
     *
     * @param internalToken 内部服务令牌
     * @param req 遥测数据
     * @return 成功;内部令牌无效 403
     */
    @PostMapping("/trust")
    public Result<Void> ingest(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @RequestBody TrustIngestRequest req) {

        if (!validInternalToken(internalToken)) {
            log.warn("[TrustIngest] rejected — invalid or missing internal service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }

        LocalDateTime at = req.createdAt() != null ? req.createdAt() : LocalDateTime.now();
        AiTrustLog entity = new AiTrustLog();
        entity.setRequestId(req.requestId());
        entity.setSessionId(req.sessionId());
        entity.setAgentKey(req.agentKey());
        entity.setSource(req.source());
        entity.setUserId(req.userId());
        entity.setQuestionText(req.questionText());
        entity.setResponseText(req.responseText());
        // 表列是 tinyint,故布尔在此转 0/1;null 保持 null(「未判定」与「判过且未拦」是两件事)。
        entity.setBlocked(req.blocked() == null ? null : (req.blocked() ? 1 : 0));
        entity.setBlockReason(req.blockReason());
        entity.setGroundingSource(req.groundingSource());
        entity.setLatencyMs(req.latencyMs());
        entity.setProvider(req.provider());
        entity.setModel(req.model());
        // 产品与用量:上游采不到就整字段缺席,这里照原样留 null —— 统计侧据此把「没采到」
        // 与「真的是 0」区分开,不做兜底(写 0 会让一次没上报用量的调用看起来像不花钱)。
        entity.setSystemCode(req.systemCode());
        entity.setInputTokens(req.inputTokens());
        entity.setOutputTokens(req.outputTokens());
        entity.setReasoningTokens(req.reasoningTokens());
        entity.setCachedInputTokens(req.cachedInputTokens());
        entity.setLlmCallCount(req.llmCallCount());
        entity.setLlmHttpAttempts(req.llmHttpAttempts());
        entity.setLlmFailedAttempts(req.llmFailedAttempts());
        entity.setCreatedAt(at);

        try {
            trustLogService.record(entity, generationsOf(req, at), signalsOf(req, at));
        } catch (Exception e) {
            log.warn("[TrustIngest] persist failed: {}", e.getMessage());
        }
        return Result.ok();
    }

    /**
     * 上报体 → 调用级实体。
     *
     * 没有 {@code requestId} 或没有 {@code seq} 的调用直接丢弃:主键由这两者派生,缺一个就既串不回本轮、
     * 也无法幂等 —— 留着只会在账上多出对不齐的孤行。
     *
     * @param req 上报体
     * @param at 发生时间
     * @return 调用级实体
     */
    private static List<AiGeneration> generationsOf(TrustIngestRequest req, LocalDateTime at) {
        List<AiGeneration> rows = new ArrayList<>();
        if (req.generations() == null || req.requestId() == null) {
            return rows;
        }
        for (GenerationPayload g : req.generations()) {
            if (g == null || g.seq() == null) {
                continue;
            }
            AiGeneration e = new AiGeneration();
            e.setGenerationId(generationId(req.requestId(), g.seq()));
            e.setRequestId(req.requestId());
            e.setSessionId(req.sessionId());
            e.setSeq(g.seq());
            e.setSystemCode(req.systemCode());
            e.setUserId(req.userId());
            e.setSource(req.source());
            // 逐次的 provider / model 优先:一轮里换过模型时,轮级那两列只记得住最后一个。
            e.setProvider(g.provider() != null ? g.provider() : req.provider());
            e.setModel(g.model() != null ? g.model() : req.model());
            e.setModelRole(g.modelRole());
            e.setPromptText(g.promptText());
            e.setRawResponse(g.rawResponse());
            e.setTextRetention(
                    AiGeneration.RETENTION_FULL.equals(g.textRetention())
                            ? AiGeneration.RETENTION_FULL
                            : AiGeneration.RETENTION_DIGEST);
            e.setPromptChars(g.promptChars());
            e.setResponseChars(g.responseChars());
            e.setInputTokens(g.inputTokens());
            e.setOutputTokens(g.outputTokens());
            e.setReasoningTokens(g.reasoningTokens());
            e.setCachedInputTokens(g.cachedInputTokens());
            e.setProviderRequestId(g.providerRequestId());
            e.setStreamed(g.streamed() == null ? null : (g.streamed() ? 1 : 0));
            e.setLatencyMs(g.latencyMs());
            e.setFinishReason(g.finishReason());
            e.setErrorType(g.errorType());
            e.setStepId(g.stepId());
            e.setCreatedAt(at);
            rows.add(e);
        }
        return rows;
    }

    /**
     * 上报体 → 信号实体。
     *
     * @param req 上报体
     * @param at 发生时间
     * @return 信号实体
     */
    private static List<AiContentSignal> signalsOf(TrustIngestRequest req, LocalDateTime at) {
        List<AiContentSignal> rows = new ArrayList<>();
        if (req.signals() == null || req.requestId() == null) {
            return rows;
        }
        for (SignalPayload sp : req.signals()) {
            if (sp == null || sp.detectorType() == null || sp.detectorType().isBlank()) {
                continue;
            }
            AiContentSignal e = new AiContentSignal();
            e.setRequestId(req.requestId());
            e.setGenerationId(
                    sp.generationSeq() == null
                            ? null
                            : generationId(req.requestId(), sp.generationSeq()));
            e.setSystemCode(req.systemCode());
            e.setDetectorType(sp.detectorType());
            e.setContentType(sp.contentType());
            e.setCategory(sp.category());
            e.setValueNum(sp.valueNum());
            e.setValueLabel(sp.valueLabel());
            e.setDetail(sp.detail());
            e.setCreatedAt(at);
            rows.add(e);
        }
        return rows;
    }

    /** 调用标识由轮次与序号派生 —— 上游不必造 id,重复上报天然幂等。 */
    private static String generationId(String requestId, int seq) {
        return requestId + "-" + seq;
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
