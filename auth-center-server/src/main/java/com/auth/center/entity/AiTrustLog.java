package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * AI 信任层遥测记录 —— agent 平台每次 LLM 调用经信任层的留痕。
 *
 * 与统一活动审计({@link AuditLog},「谁改了什么」)是不同域:此为 AI 安全可观测性。
 *
 * 这里只是轮级那一层:谁在哪个会话问的、拦没拦、整轮花了多少、多久。往下还有两层 ——
 * 模型究竟看到了什么、原样吐了什么在 {@link AiGeneration}(一次调用一行,ReAct 一轮有 N 次);
 * 各检测器判了什么在 {@link AiContentSignal}(一次判定一行)。三张表按 {@code requestId} 对齐,
 * 而那个键与智能体侧的 {@code agent_interaction.interaction_id}、span 树的 trace_id 同值。
 *
 * 由 agent-server 经 trust ingest 端点推送。
 */
@TableName("auth_ai_trust_log")
public class AiTrustLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 调用来源: RUNTIME(智能体) / BI_UTILITY(BI 单发)。 */
    private String source;

    /** 请求 id(= 产品侧该轮的 turn_id,入口生成一次两边同值 —— 语义面与消耗面的对账键)。 */
    private String requestId;

    /** 产品侧会话 ID(运行时上报,平台不解释;账本行凭它链回产品的会话回放页)。 */
    private String sessionId;

    /** 智能体 key。 */
    private String agentKey;

    /** 终端用户 id。 */
    private Long userId;

    /**
     * 用户问题(已掩码)。
     *
     * 注意名字:这是用户问的那句,不是模型收到的提示词 —— 后者一轮有 N 份,在
     * {@code auth_ai_generation.promptText} 里。旧列名 {@code masked_prompt} 正因这层歧义而改掉。
     */
    private String questionText;

    /** 交付给用户那份(已去掩码)。模型原样返回在 {@code auth_ai_generation.rawResponse}。 */
    private String responseText;

    /** 是否拦截(1=是)。 */
    private Integer blocked;

    /** 拦截原因: INJECTION / TOXICITY。 */
    private String blockReason;

    /** 接地来源。 */
    private String groundingSource;

    /** 时延(毫秒)。 */
    private Long latencyMs;

    /** LLM 提供方。 */
    private String provider;

    /** 模型。 */
    private String model;

    /** 产品标识(bi / tracking 等)。空 = 历史行,统计时归入未知产品。 */
    private String systemCode;

    /**
     * 整轮 token 用量（V14）。全部可空：流式下 usage 只在末个 chunk 回，模型未开用量上报、或网关不回 model 名时都拿不到 —— 此时留 {@code null}
     * 而非 0，避免把「没采到」伪装成「没花钱」。
     *
     * {@code reasoningTokens} 是 {@code outputTokens} 的子集、{@code cachedInputTokens} 是 {@code
     * inputTokens} 的子集，均不另加；合计恒为输入+输出。
     */
    private Integer inputTokens;

    private Integer outputTokens;

    private Integer reasoningTokens;

    private Integer cachedInputTokens;

    /** 本轮实际发生的模型调用次数。一轮不是一次调用（ReAct 每步一次），这个数才解释得了「一轮几万 token」。 */
    private Integer llmCallCount;

    /**
     * 本轮真实出网次数（含 SDK 内部重试与非 2xx，V15）。
     *
     * {@code llmHttpAttempts - llmCallCount} 就是少报量：回调层只记「成功且带 model 名」的调用，而超时 / 4xx /
     * 重试一概不触发它，那些调用服务商可能已计费。刻意不去修正 token 总和 —— 它们没回用量，补任何数字都是编的。
     */
    private Integer llmHttpAttempts;

    /** 其中非 2xx 的次数（V15）。 */
    private Integer llmFailedAttempts;

    /** 发生时间。为空时由 MetaObjectHandler 兜底填充。 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getAgentKey() {
        return agentKey;
    }

    public void setAgentKey(String agentKey) {
        this.agentKey = agentKey;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getResponseText() {
        return responseText;
    }

    public void setResponseText(String responseText) {
        this.responseText = responseText;
    }

    public Integer getBlocked() {
        return blocked;
    }

    public void setBlocked(Integer blocked) {
        this.blocked = blocked;
    }

    public String getBlockReason() {
        return blockReason;
    }

    public void setBlockReason(String blockReason) {
        this.blockReason = blockReason;
    }

    public String getGroundingSource() {
        return groundingSource;
    }

    public void setGroundingSource(String groundingSource) {
        this.groundingSource = groundingSource;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getReasoningTokens() {
        return reasoningTokens;
    }

    public void setReasoningTokens(Integer reasoningTokens) {
        this.reasoningTokens = reasoningTokens;
    }

    public Integer getCachedInputTokens() {
        return cachedInputTokens;
    }

    public void setCachedInputTokens(Integer cachedInputTokens) {
        this.cachedInputTokens = cachedInputTokens;
    }

    public Integer getLlmCallCount() {
        return llmCallCount;
    }

    public void setLlmCallCount(Integer llmCallCount) {
        this.llmCallCount = llmCallCount;
    }

    public Integer getLlmHttpAttempts() {
        return llmHttpAttempts;
    }

    public void setLlmHttpAttempts(Integer llmHttpAttempts) {
        this.llmHttpAttempts = llmHttpAttempts;
    }

    public Integer getLlmFailedAttempts() {
        return llmFailedAttempts;
    }

    public void setLlmFailedAttempts(Integer llmFailedAttempts) {
        this.llmFailedAttempts = llmFailedAttempts;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
