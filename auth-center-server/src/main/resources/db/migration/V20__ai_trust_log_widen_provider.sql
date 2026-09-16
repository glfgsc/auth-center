-- V20:provider 列放宽到 255 —— 它存的是 api_base URL,不是短名,64 字符装不下长域名服务商。
--
-- 审计行的 provider 取自运行时 LLMFactory.describe() 的 api_base(整条 URL,如
-- ``https://llm-****.cn-beijing.maas.aliyuncs.com/compatible-mode/v1``,78 字符),
-- 列宽却按「provider 短名」定了 64。切到长域名服务商后,每条带 provider 的遥测在
-- INSERT 时 Data too long 被拒 —— 转推链路 best-effort,agent-server 与运行时两侧
-- 全绿,只有 auth-center 一行 WARN,信任账本静默断流(token 消耗统计随之全缺)。
-- 列宽必须按它实际承载的东西(URL)定,255 覆盖常见云厂商网关域名再留余量。

ALTER TABLE auth_ai_trust_log MODIFY COLUMN provider VARCHAR(255) NULL DEFAULT NULL COMMENT 'LLM 提供方(api_base URL)';
