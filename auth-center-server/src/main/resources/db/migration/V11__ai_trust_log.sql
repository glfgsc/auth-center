-- AI 信任层遥测 —— 认证中心汇集 agent 平台每次 LLM 调用的信任层留痕。
-- 与「活动审计」(谁改了什么)不同域:这是 AI 安全可观测性(PII / 毒性 / 越狱拦截 / 接地)。
-- 结构镜像 agent 的 agent_llm_audit;由 agent-server 经 trust ingest 端点推送。
CREATE TABLE IF NOT EXISTS auth_ai_trust_log (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    source               VARCHAR(32)  DEFAULT NULL COMMENT '调用来源: RUNTIME(智能体) / BI_UTILITY(BI 单发)',
    request_id           VARCHAR(64)  DEFAULT NULL COMMENT '请求 id',
    agent_key            VARCHAR(128) DEFAULT NULL COMMENT '智能体 key',
    user_id              BIGINT       DEFAULT NULL COMMENT '终端用户 id',
    prompt_hash          VARCHAR(64)  DEFAULT NULL COMMENT 'prompt SHA-256',
    masked_prompt        TEXT         DEFAULT NULL COMMENT '脱敏后 prompt',
    answer               MEDIUMTEXT   DEFAULT NULL COMMENT '安全答复',
    pii_categories       VARCHAR(512) DEFAULT NULL COMMENT 'PII 命中类别',
    pii_hit_count        INT          DEFAULT NULL COMMENT 'PII 命中数',
    toxicity_max         DOUBLE       DEFAULT NULL COMMENT '毒性最高分',
    toxicity_scores_json TEXT         DEFAULT NULL COMMENT '各侧毒性分数 JSON',
    blocked              TINYINT      DEFAULT NULL COMMENT '是否拦截(1=是)',
    block_reason         VARCHAR(64)  DEFAULT NULL COMMENT '拦截原因: INJECTION / TOXICITY',
    grounding_source     VARCHAR(128) DEFAULT NULL COMMENT '接地来源',
    latency_ms           BIGINT       DEFAULT NULL COMMENT '时延(毫秒)',
    provider             VARCHAR(64)  DEFAULT NULL COMMENT 'LLM 提供方',
    model                VARCHAR(128) DEFAULT NULL COMMENT '模型',
    created_at           DATETIME     NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_agent_key (agent_key),
    KEY idx_blocked (blocked),
    KEY idx_user (user_id),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心 AI 信任层遥测';
