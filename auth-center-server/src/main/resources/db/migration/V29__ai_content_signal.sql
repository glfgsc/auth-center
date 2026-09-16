-- V29:质量信号结构化 —— 一个检测器一次判定一行,取代 auth_ai_trust_log 上那几个扁平列。
--
-- 此前的形态是「一个布尔 + 一个毒性最高分 + 一个 JSON」:能回答「这轮被拦了吗」,回答不了
-- 「上周有多少次结论没通过数字溯源」「检索层坏还是生成层坏」。而后面这类问题才是效果评估要问的。
--
-- **对我们价值最大的是后三个检测器**:口径闸、数字溯源、限定落位对账现在只在过程卡上渲染一次就没了 ——
-- 判定明明做了,却不留账。写成同一张表的行之后,它们既是排查证据,也直接成为质量指标的数据源。
--
-- 取值分两列而不是一列:检测器天生两种量纲 —— 毒性/PII 是 0–1 的分,指令遵循/任务解决是三值档。
-- 硬塞进一列要么丢精度要么丢语义,两列各记各的,读的人按 detector_type 就知道该看哪一列。

CREATE TABLE IF NOT EXISTS auth_ai_content_signal (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    request_id    VARCHAR(64)  NOT NULL COMMENT '所属轮次 = 入口播种的 requestId',
    generation_id VARCHAR(64)  DEFAULT NULL COMMENT '所属调用(auth_ai_generation);轮级判定为空',
    system_code   VARCHAR(32)  DEFAULT NULL COMMENT '算在哪个产品头上',
    detector_type VARCHAR(32)  NOT NULL COMMENT 'PII / TOXICITY / PROMPT_DEFENSE / INSTRUCTION_ADHERENCE / TASK_RESOLUTION / SCOPE_LANDED / NUMBER_GROUNDED / VALUE_GROUNDED',
    content_type  VARCHAR(8)   DEFAULT NULL COMMENT 'INPUT 用户侧 / OUTPUT 模型侧 —— 合规须能分辨「是用户问得脏还是模型答得脏」',
    category      VARCHAR(64)  DEFAULT NULL COMMENT '细分类别(PII 的 Name/Email、毒性的 hate/violence 等)',
    value_num     DECIMAL(4,3) DEFAULT NULL COMMENT '0.000–1.000 的分;三值档检测器为空',
    value_label   VARCHAR(16)  DEFAULT NULL COMMENT 'HIGH/LOW/UNCERTAIN 或 FULLY/PARTIALLY/UNRESOLVED;打分型检测器为空',
    detail        VARCHAR(1000) DEFAULT NULL COMMENT '判定说理 / 命中项(不放证据原文)',
    created_at    DATETIME(3)  NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_signal_request (request_id),
    KEY idx_signal_type_time (detector_type, created_at),
    KEY idx_signal_system_time (system_code, detector_type, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心 AI 质量信号';

-- 存量迁入:PII 命中数与毒性分本就是判定结果,行化后历史统计不断档。
-- 毒性两侧分开建行 —— 原先那个 JSON 里就是分侧存的,合成一行会把「用户问得脏」和「模型答得脏」拌在一起。
INSERT INTO auth_ai_content_signal (request_id, system_code, detector_type, content_type, category, value_num, created_at)
SELECT request_id, system_code, 'PII', 'INPUT', pii_categories, NULL, created_at
FROM auth_ai_trust_log
WHERE request_id IS NOT NULL AND pii_hit_count IS NOT NULL AND pii_hit_count > 0;

INSERT INTO auth_ai_content_signal (request_id, system_code, detector_type, content_type, value_num, created_at)
SELECT request_id, system_code, 'TOXICITY', 'INPUT',
       CAST(JSON_UNQUOTE(JSON_EXTRACT(toxicity_scores_json, '$.input')) AS DECIMAL(4,3)), created_at
FROM auth_ai_trust_log
WHERE request_id IS NOT NULL AND JSON_VALID(toxicity_scores_json)
  AND JSON_EXTRACT(toxicity_scores_json, '$.input') IS NOT NULL;

INSERT INTO auth_ai_content_signal (request_id, system_code, detector_type, content_type, value_num, created_at)
SELECT request_id, system_code, 'TOXICITY', 'OUTPUT',
       CAST(JSON_UNQUOTE(JSON_EXTRACT(toxicity_scores_json, '$.output')) AS DECIMAL(4,3)), created_at
FROM auth_ai_trust_log
WHERE request_id IS NOT NULL AND JSON_VALID(toxicity_scores_json)
  AND JSON_EXTRACT(toxicity_scores_json, '$.output') IS NOT NULL;
