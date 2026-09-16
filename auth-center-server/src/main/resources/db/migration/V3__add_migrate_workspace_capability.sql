-- V3__add_migrate_workspace_capability.sql
-- 注册「工作区资产迁移」能力 admin:migrate_workspace，并补授给 admin 权限集。
-- 值与前端 SYSTEM_PS_CAPS(bi-front/src/constants/capabilities.ts)对齐——单一事实源，改默认能力须两处同步。

-- ① 注册 bi 系统能力码（幂等 INSERT IGNORE）
INSERT IGNORE INTO auth_system_capability (system_code, capability_code, category, label, description) VALUES
('bi', 'admin:migrate_workspace', 'admin', '工作区资产迁移', '跨环境导出 / 导入工作区的全部数据资产');

-- ② 补授 admin 权限集（幂等：NOT JSON_CONTAINS 守卫；capabilities 为 NULL 时 JSON_CONTAINS 返回 NULL → 不命中，
--    但 V2 已为 admin 种子完整能力列表，此处 IS NOT NULL 恒成立）
UPDATE auth_permission_set
   SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'admin:migrate_workspace')
 WHERE code = 'admin'
   AND capabilities IS NOT NULL
   AND NOT JSON_CONTAINS(capabilities, '"admin:migrate_workspace"');
