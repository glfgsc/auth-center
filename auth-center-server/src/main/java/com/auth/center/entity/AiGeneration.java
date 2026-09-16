package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * 一次模型调用 —— 对应 {@code auth_ai_generation} 表。
 *
 * 轮级那一行({@link AiTrustLog})回答「这一轮谁问的、拦没拦、总共花了多少」;这里回答「模型究竟看到了什么、原样吐了什么」。
 * ReAct 一轮有 N 次调用,所以「最终提示词」根本不是一份而是 N 份 —— 只有拆到调用级才存得下。
 *
 * {@code requestId} = 入口播种的 requestId,与智能体侧 {@code agent_interaction.interaction_id}、span 树的
 * trace_id 同值,全链路一个键。
 */
@TableName("auth_ai_generation")
public class AiGeneration {

    /** 正文留全文。 */
    public static final String RETENTION_FULL = "FULL";

    /** 正文只留摘要与长度(默认档)。 */
    public static final String RETENTION_DIGEST = "DIGEST";

    /** 本次调用标识(运行时生成)。 */
    @TableId private String generationId;

    /** 所属轮次。 */
    private String requestId;

    /** 产品侧会话标识(引用)。 */
    private String sessionId;

    /** 本轮第几次调用(1 起)。 */
    private Integer seq;

    /** 算在哪个产品头上。 */
    private String systemCode;

    private Long userId;

    /** {@code RUNTIME} / {@code BI_UTILITY}。 */
    private String source;

    /** LLM 提供方(api_base URL)。 */
    private String provider;

    private String model;

    /** 模型角色(reasoning / fast 等)—— 多角色绑定下按模型分账要靠它。 */
    private String modelRole;

    /** 送给模型那份(已掩码);按 {@link #textRetention} 决定是否留全文。 */
    private String promptText;

    /** 模型原样返回(未去掩码);与交付文本并排即可看出改写了什么。 */
    private String rawResponse;

    /** {@link #RETENTION_FULL} / {@link #RETENTION_DIGEST}。 */
    private String textRetention;

    /** 提示词字数 —— 即便不留全文也照实记,故「没采到」与「按策略没留」分得开。 */
    private Integer promptChars;

    private Integer responseChars;

    private Integer inputTokens;

    private Integer outputTokens;

    /** 思考 token;是输出的子集,不另加。 */
    private Integer reasoningTokens;

    /** 命中提示缓存的输入 token;是输入的子集,不另加。 */
    private Integer cachedInputTokens;

    /** 服务商请求 ID —— 拿去服务商控制台逐条对账的索引。 */
    private String providerRequestId;

    /** 是否流式(1=是);流式拿不到服务商原文,对账时可疑度更高。 */
    private Integer streamed;

    private Long latencyMs;

    /** 结束原因;{@code length} 意味着输出被截断 —— 那是「答得不全」最直接的解释。 */
    private String finishReason;

    /** 本次失败的异常类型;成功为空。 */
    private String errorType;

    /** 对应智能体侧 {@code agent_interaction_step.id};并非每次调用都属于某一步,故可空。 */
    private Long stepId;

    private LocalDateTime createdAt;

    public String getGenerationId() {
        return generationId;
    }

    public void setGenerationId(String generationId) {
        this.generationId = generationId;
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

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
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

    public String getModelRole() {
        return modelRole;
    }

    public void setModelRole(String modelRole) {
        this.modelRole = modelRole;
    }

    public String getPromptText() {
        return promptText;
    }

    public void setPromptText(String promptText) {
        this.promptText = promptText;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public void setRawResponse(String rawResponse) {
        this.rawResponse = rawResponse;
    }

    public String getTextRetention() {
        return textRetention;
    }

    public void setTextRetention(String textRetention) {
        this.textRetention = textRetention;
    }

    public Integer getPromptChars() {
        return promptChars;
    }

    public void setPromptChars(Integer promptChars) {
        this.promptChars = promptChars;
    }

    public Integer getResponseChars() {
        return responseChars;
    }

    public void setResponseChars(Integer responseChars) {
        this.responseChars = responseChars;
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

    public String getProviderRequestId() {
        return providerRequestId;
    }

    public void setProviderRequestId(String providerRequestId) {
        this.providerRequestId = providerRequestId;
    }

    public Integer getStreamed() {
        return streamed;
    }

    public void setStreamed(Integer streamed) {
        this.streamed = streamed;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public Long getStepId() {
        return stepId;
    }

    public void setStepId(Long stepId) {
        this.stepId = stepId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
