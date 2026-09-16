-- V28:调用级审计 —— 一次模型调用一行,取代 auth_ai_trust_log 里那个 llm_call_breakdown JSON 数组。
--
-- 为什么必须拆到调用级:ReAct 一轮有 N 次模型调用,「最终提示词」根本不是一份而是 N 份。轮级那一行的
-- masked_prompt 存的是**用户的问题**(名实不符),answer 存的是交付文本 —— 中间那 N 次模型究竟看到了什么、
-- 原样吐了什么,一个字都没有。于是「模型为什么这么答」这个问题在账上无从回答。
--
-- 同构于 V40 对过程账本做的事:JSON blob 提成行,促列建索引。llm_call_breakdown 那个数组已经是逐次用量了,
-- 这里把它连同正文一起行化,并在 V30 里删掉原列 —— 两份并存意味着读侧永远要判断一次。
--
-- **正文分层存**(text_retention):一次 ReAct 十几次调用 × 每次几万字上下文,全量留存会让存储与 PII 面积一起翻倍,
-- 而九成轮次事后没人看。故默认只留摘要与长度,全文仅在三种情形落盘:本轮失败 / 命中抽样 / 管理员显式取证。
-- 这一列把策略**写在行上**,读的人才分得清「没采到」与「按策略没留」—— 留空让人猜是另一种失真。
--
-- request_id = 入口播种的 requestId = 智能体侧 agent_interaction.interaction_id = span 树的 trace_id,
-- 全链路同一个键,不另造。

CREATE TABLE IF NOT EXISTS auth_ai_generation (
    generation_id       VARCHAR(64)  NOT NULL COMMENT '本次调用标识(运行时生成)',
    request_id          VARCHAR(64)  NOT NULL COMMENT '所属轮次 = 入口播种的 requestId',
    session_id          VARCHAR(64)  DEFAULT NULL COMMENT '产品侧会话标识(引用)',
    seq                 INT          NOT NULL COMMENT '本轮第几次调用(1 起)',
    system_code         VARCHAR(32)  DEFAULT NULL COMMENT '算在哪个产品头上',
    user_id             BIGINT       DEFAULT NULL COMMENT '终端用户 id',
    source              VARCHAR(32)  DEFAULT NULL COMMENT 'RUNTIME(智能体) / BI_UTILITY(单发)',
    provider            VARCHAR(255) DEFAULT NULL COMMENT 'LLM 提供方(api_base URL)',
    model               VARCHAR(128) DEFAULT NULL COMMENT '模型',
    model_role          VARCHAR(16)  DEFAULT NULL COMMENT '模型角色(reasoning / fast 等);多角色绑定下按模型分账要靠它',
    prompt_text         MEDIUMTEXT   DEFAULT NULL COMMENT '送给模型那份(已掩码);按 text_retention 决定是否留全文',
    raw_response        MEDIUMTEXT   DEFAULT NULL COMMENT '模型原样返回(未去掩码);与交付文本并排即可看出改写了什么',
    text_retention      VARCHAR(16)  NOT NULL DEFAULT 'DIGEST' COMMENT 'FULL 留全文 / DIGEST 只留摘要与长度',
    prompt_chars        INT          DEFAULT NULL COMMENT '提示词字数(即便不留全文也照实记)',
    response_chars      INT          DEFAULT NULL COMMENT '返回字数',
    input_tokens        INT          DEFAULT NULL COMMENT '本次输入 token',
    output_tokens       INT          DEFAULT NULL COMMENT '本次输出 token',
    reasoning_tokens    INT          DEFAULT NULL COMMENT '其中思考 token;是 output 的子集,不另加',
    cached_input_tokens INT          DEFAULT NULL COMMENT '其中命中提示缓存的输入 token;是 input 的子集,不另加',
    provider_request_id VARCHAR(128) DEFAULT NULL COMMENT '服务商请求 ID —— 拿去服务商控制台逐条对账的索引',
    streamed            TINYINT      DEFAULT NULL COMMENT '是否流式(1=是);流式拿不到服务商原文,对账时可疑度更高',
    latency_ms          BIGINT       DEFAULT NULL COMMENT '本次耗时',
    finish_reason       VARCHAR(32)  DEFAULT NULL COMMENT '结束原因(stop / length / tool_calls …);length 意味着被截断',
    error_type          VARCHAR(48)  DEFAULT NULL COMMENT '本次失败的异常类型;成功为空',
    step_id             BIGINT       DEFAULT NULL COMMENT '对应智能体侧 agent_interaction_step.id(可空:并非每次调用都属于某一步)',
    created_at          DATETIME(3)  NOT NULL COMMENT '发生时间',
    PRIMARY KEY (generation_id),
    KEY idx_gen_request (request_id, seq),
    KEY idx_gen_model (provider, model, created_at),
    KEY idx_gen_system_time (system_code, created_at),
    KEY idx_gen_provider_request (provider_request_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心 AI 调用级审计';

-- 存量迁入:llm_call_breakdown 里已有逐次用量,行化后历史对账不断档。
-- 历史行没有正文(那时压根没采),故 text_retention 记 DIGEST —— 不是「策略丢的」,而是「当时就没有」,
-- 两者在读的人眼里都是空,但字数列为 NULL 可区分:有字数=留过、没字数=没采过。
INSERT IGNORE INTO auth_ai_generation (
    generation_id, request_id, session_id, seq, system_code, user_id, source,
    provider, model, input_tokens, output_tokens, reasoning_tokens, cached_input_tokens,
    provider_request_id, streamed, text_retention, created_at)
SELECT
    CONCAT(t.request_id, '-', j.seq),
    t.request_id, t.session_id, j.seq, t.system_code, t.user_id, t.source,
    t.provider, t.model,
    j.input_tokens, j.output_tokens, j.reasoning_tokens, j.cached_input_tokens,
    j.provider_request_id, j.streamed, 'DIGEST', t.created_at
FROM auth_ai_trust_log t,
     JSON_TABLE(t.llm_call_breakdown, '$[*]' COLUMNS (
         seq                 FOR ORDINALITY,
         provider_request_id VARCHAR(128) PATH '$.requestId',
         input_tokens        INT          PATH '$.inputTokens',
         output_tokens       INT          PATH '$.outputTokens',
         reasoning_tokens    INT          PATH '$.reasoningTokens',
         cached_input_tokens INT          PATH '$.cachedInputTokens',
         streamed            TINYINT      PATH '$.streamed'
     )) AS j
WHERE t.request_id IS NOT NULL
  AND t.llm_call_breakdown IS NOT NULL
  AND JSON_VALID(t.llm_call_breakdown);
