-- V30:轮级那一行瘦身 —— 已经行化的东西不再在这里留第二份。
--
-- 数据在 V28 / V29 迁完。不留过渡期:两份并存意味着读侧永远要判断一次先读哪份,而两份一旦对不上
-- (某条路径只写了一份),排查的人得先判断是哪份错了 —— 那比没有第二份更费事。
--
-- 逐列的去向:
--   llm_call_breakdown   → auth_ai_generation 的行(且那边还多了正文与 finish_reason)
--   provider_request_ids → auth_ai_generation.provider_request_id(一次调用一个,不再是数组)
--   pii_categories / pii_hit_count       → auth_ai_content_signal 的 PII 行
--   toxicity_max / toxicity_scores_json  → auth_ai_content_signal 的 TOXICITY 行(两侧各一行)
--   prompt_hash          → 直接删。它当初的用途是「同一问题反复问」的粗略聚类,而那件事现在由
--                          意图聚类做,做得好得多;留着只是一列没人读的哈希。
--
-- 留下来的仍是轮级事实:谁在哪个会话问的、拦没拦、整轮花了多少、多久。blocked / block_reason 刻意留在这里 ——
-- 它们是**裁决**(这一轮拦不拦),而检测器的**测量**才归信号表;把裁决也搬走会让「为什么拦」散成两处。

ALTER TABLE auth_ai_trust_log
    DROP COLUMN llm_call_breakdown,
    DROP COLUMN provider_request_ids,
    DROP COLUMN pii_categories,
    DROP COLUMN pii_hit_count,
    DROP COLUMN toxicity_max,
    DROP COLUMN toxicity_scores_json,
    DROP COLUMN prompt_hash;

-- masked_prompt 名实不符:它存的是**用户的问题**,不是「掩码后的最终提示词」——
-- 后者是 N 份、且现已落在 auth_ai_generation.prompt_text。改名消歧,免得下一个人照着列名理解错。
ALTER TABLE auth_ai_trust_log
    CHANGE COLUMN masked_prompt question_text TEXT NULL COMMENT '用户问题(已掩码)。注意:模型实际收到的提示词在 auth_ai_generation.prompt_text,一轮 N 份';

ALTER TABLE auth_ai_trust_log
    CHANGE COLUMN answer response_text MEDIUMTEXT NULL COMMENT '交付给用户那份(已去掩码)。模型原样返回在 auth_ai_generation.raw_response';
