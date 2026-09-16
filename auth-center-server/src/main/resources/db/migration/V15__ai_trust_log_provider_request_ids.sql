-- V15:落库服务商请求 ID 与出网次数 —— 让「和账单对不上」能从看板点到具体哪一次调用。
--
-- V14 已经能回答「哪个产品、哪个用户花了多少」,但对不上时还是查不动:一轮四万 token
-- 是十次调用凑出来的,不知道是哪十次,就只能拿一个总数去跟服务商干瞪眼。
--
-- provider_request_ids 存的是**服务商自己的**请求 ID(响应头里取的,如 x-request-id),
-- 不是我们的 request_id、也不是 LangChain 的 run id —— 后两者在服务商控制台里查不到东西。
-- 流式下 llm_output 为空,回调层根本拿不到真 ID,故这份只能由 HTTP 层留痕提供
-- (见 agent-runtime 的 llm/http_audit)。
--
-- 另两列是对账的分母。回调层只记「成功且带 model 名」的调用,而超时 / 4xx / SDK 内部重试
-- 一概不触发它 —— 那些调用服务商可能已经计费。所以:
--   · llm_call_count(V14)  = 已记账次数,与 token 总和自洽;
--   · llm_http_attempts    = 真实出网次数;
--   · 两者之差             = 少报量,即账单会高出来的部分。
-- 不去「修正」token 总和:那些调用没回用量,补任何数字都是编的。如实并列,让差值自己说话。
--
-- 全部可空:HTTP 层留痕未启用或上游是旧版本时留 NULL,不写 0 假装「一次都没失败」。
--
-- 单列单条 ALTER:H2(MODE=MYSQL,集成测试环境)不支持逗号分隔多列 ADD。

ALTER TABLE auth_ai_trust_log ADD COLUMN provider_request_ids TEXT NULL COMMENT '本轮各次调用的服务商请求 ID(JSON 数组);拿去服务商控制台逐条对账用';
ALTER TABLE auth_ai_trust_log ADD COLUMN llm_http_attempts INT NULL COMMENT '本轮真实出网次数(含 SDK 重试与非 2xx);减去 llm_call_count 即少报量';
ALTER TABLE auth_ai_trust_log ADD COLUMN llm_failed_attempts INT NULL COMMENT '其中非 2xx 的次数;这些调用服务商可能已计费而我们没记用量';
