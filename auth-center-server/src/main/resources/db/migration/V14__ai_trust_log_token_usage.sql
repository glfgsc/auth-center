-- V14:信任层遥测补 token 用量与产品标识 —— 让「分产品、分用户的大模型消耗」有唯一权威来源。
--
-- 此前用量只落在各产品自己的库里(如 BI 的 bi_ai_turn),平台层看不全:换个产品接进来就要再建一张表、
-- 再写一套统计。而本表已经是**所有** LLM 调用的统一留痕(RUNTIME 与 BI_UTILITY 两个来源都推这里),
-- 天然按 user_id / agent_key / provider / model 分好了维度,缺的只是「花了多少」和「算在哪个产品头上」。
--
-- provider / model 早已是列而非硬编码 —— 换模型服务商时这张表和它上面的统计零改动,
-- 新服务商的调用照常按 provider 分组落进来。
--
-- 全部可空,与用量采集的既有约定一致:流式下 usage 只在末个 chunk 回,模型未开用量上报、
-- 或网关不回 model 名时都拿不到 —— 此时留 NULL 而不是写 0 冒充「这次不花钱」。
-- llm_call_count 同理:一轮不是一次调用(ReAct 每步一次),这个数才能解释「为什么一轮几万 token」。
--
-- 单列单条 ALTER:H2(MODE=MYSQL,集成测试环境)不支持逗号分隔多列 ADD。

ALTER TABLE auth_ai_trust_log ADD COLUMN system_code VARCHAR(32) NULL COMMENT '产品标识(bi / tracking 等);空=历史行,归入未知产品';
ALTER TABLE auth_ai_trust_log ADD COLUMN input_tokens INT NULL COMMENT '整轮输入 token(含缓存命中部分)';
ALTER TABLE auth_ai_trust_log ADD COLUMN output_tokens INT NULL COMMENT '整轮输出 token(含思考部分)';
ALTER TABLE auth_ai_trust_log ADD COLUMN reasoning_tokens INT NULL COMMENT '其中思考 token;是 output 的子集,不另加';
ALTER TABLE auth_ai_trust_log ADD COLUMN cached_input_tokens INT NULL COMMENT '其中命中提示缓存的输入 token;是 input 的子集,不另加';
ALTER TABLE auth_ai_trust_log ADD COLUMN llm_call_count INT NULL COMMENT '本轮实际发生的模型调用次数(ReAct 每步一次,故常 >1)';

-- 统计页的两条主查询路径:按产品+时间聚合、按用户+时间聚合。
-- 不建 (model, created_at):模型基数小,按产品/用户筛完再分组即可,多一个索引不值当写入成本。
CREATE INDEX idx_trust_system_time ON auth_ai_trust_log (system_code, created_at);
CREATE INDEX idx_trust_user_time ON auth_ai_trust_log (user_id, created_at);
