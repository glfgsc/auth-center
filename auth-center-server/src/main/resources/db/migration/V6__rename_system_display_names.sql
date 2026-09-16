-- V6: 系统注册表展示名对齐真实产品品牌
-- ----------------------------------------------------------------------------
-- auth_system.name 是「前端缺 i18n 时的回退展示名」（见 V5）。V5 播种的是占位名
-- （'BI 平台' / 'Agent 平台' / '埋点平台' / '认证中心'），与各产品顶栏真实品牌不一致：
--   bi          → 洞察（数织 · 洞察 / Loom Insight，bi-front MainLayout 品牌）
--   agent       → 知数（数织 · 知数 / Loom Ken，agent-console 品牌）
--   tracking    → 循迹（数织 · 循迹，tracking-point-front 品牌）
--   auth_center → 中心（数织 · 中心，auth-center-admin 品牌）
--   global      → 全局（跨系统作用域，非产品，保持不变）
-- 前端展示名主源在 i18n users.system.*（双语，随本迁移同步为简称），此处更新回退名保持一致。
-- 守卫式 UPDATE：仅当仍为 V5 占位名时改写，不覆盖运维手工改过的名称，二次重跑幂等。

UPDATE auth_system SET name = '洞察' WHERE code = 'bi'          AND name = 'BI 平台';
UPDATE auth_system SET name = '知数' WHERE code = 'agent'       AND name = 'Agent 平台';
UPDATE auth_system SET name = '循迹' WHERE code = 'tracking'    AND name = '埋点平台';
UPDATE auth_system SET name = '中心' WHERE code = 'auth_center' AND name = '认证中心';
