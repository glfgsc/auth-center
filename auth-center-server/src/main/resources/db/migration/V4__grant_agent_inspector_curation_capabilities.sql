-- V4__grant_agent_inspector_curation_capabilities.sql
-- 补齐两个从未授予过任何人的知数技能组能力:agent:use:inspector / agent:use:curation。
-- 值与前端 SYSTEM_PS_CAPS(bi-front/src/constants/capabilities.ts)对齐——单一事实源,改默认能力须两处同步。
--
-- 背景:运行时按 agent:use:<分组> 裁剪技能(agent-runtime/src/skills/governance.py),分组共四个
-- (qa / authoring / inspector / curation),但 V2 只注册并授予了前两个。于是对**所有非管理员**:
--   submit_pulse_scan(inspector)—— 哨兵 Agent 唯一的实质技能
--   submit_prepare_data(curation)—— 备数 Agent 的数据准备技能
-- 一直被静默过滤,这两个内建 Agent 实际只剩对话外壳(submit_converse / submit_clarify 恒可用)。
-- 此前调度 Agent 自己持有全部主题、从不把请求委派出去,故该缺口不显形;多 Agent 编排把流量真正
-- 导向专职 Agent 后,它会表现为「转接过去却什么都干不了」。

-- ① 注册 bi 系统能力码(幂等)
INSERT IGNORE INTO auth_system_capability (system_code, capability_code, category, label, description) VALUES
('bi', 'agent:use:inspector', 'agent', '知数 · 指标巡检', '使用指标巡检类技能(健康度扫描 / 异常识别)'),
('bi', 'agent:use:curation', 'agent', '知数 · 数据准备', '使用数据准备类技能(清洗建议 / 数据整备)');

-- ② 防锁死回填:给现持 agent:chat 者补授,与 V2 第 7 节同一姿态。
-- 幂等:NOT JSON_CONTAINS 守卫;capabilities 为 NULL 的行 JSON_CONTAINS 返回 NULL → 不命中。
UPDATE auth_permission_set
   SET capabilities = JSON_ARRAY_APPEND(
           JSON_ARRAY_APPEND(capabilities, '$', 'agent:use:inspector'),
           '$', 'agent:use:curation')
 WHERE JSON_CONTAINS(capabilities, '"agent:chat"')
   AND NOT JSON_CONTAINS(capabilities, '"agent:use:inspector"');
