-- V17:补 session_id + request_id 索引 —— 让 token 账本的每一轮能链回产品侧的会话回放。
--
-- 同一轮对话在两处各记半本账:产品侧(如 BI 的 bi_ai_turn)记语义面 —— 问了什么、路由到哪、
-- 答了什么;本表记消耗面 —— 调了几次模型、烧了多少 token。两半账的对账键是 request_id
-- (运行时已把它同时用作产品侧的 turn_id,入口生成一次、两边同值)。但只有 turn 级的键
-- 还回不去会话页 —— 产品侧的回放路由按会话组织,故补 session_id 一列:值由运行时上报,
-- 平台不解释其含义(各产品会话体系不同),只原样存取。
--
-- 全部可空:无会话上下文的调用面(BI 单发工具、旧版运行时)缺席即 NULL,不编值。
--
-- request_id 索引:互链的反向入口是「按 request_id 精确查一轮」,该列此前只作展示从未
-- 被查询条件用过,没有索引;全表扫在遥测表上会随留痕量线性变慢。
--
-- 单列单条 ALTER:H2(MODE=MYSQL,集成测试环境)不支持逗号分隔多列 ADD。

ALTER TABLE auth_ai_trust_log ADD COLUMN session_id VARCHAR(64) NULL COMMENT '产品侧会话 ID(运行时上报,平台不解释);账本行凭它链回产品的会话回放页';

CREATE INDEX idx_trust_request ON auth_ai_trust_log (request_id);
