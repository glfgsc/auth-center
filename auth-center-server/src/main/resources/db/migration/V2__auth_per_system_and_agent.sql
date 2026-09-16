-- V2: 授权模型「按系统隔离」(per-system) + 预置能力种子 + Agent 平台系统隔离
-- ----------------------------------------------------------------------------
-- system_code：权限集/绑定归属的系统（bi / agent / ...）或 'global'（跨系统超管）。
-- 一个用户每系统至多一个角色，故 auth_user_permission_set 唯一键收敛为 (user_id, system_code)，
-- 支持「bi=admin、tracking=viewer」并存。
--
-- 条件 DDL 用 information_schema 守卫 + PREPARE/EXECUTE：MySQL 无 ADD COLUMN IF NOT EXISTS，
-- 而以 baseline-on-migrate 引入 Flyway 的老库可能已含 system_code（曾手工执行过等价迁移），
-- 守卫保证此版本在「全新库 / per-system 前老库 / 已迁移老库」上均幂等收敛、不报错。

-- ── 1) 权限集归属系统列 ──────────────────────────────────────────────────────
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'auth_permission_set'
                 AND COLUMN_NAME = 'system_code');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE auth_permission_set ADD COLUMN system_code VARCHAR(50) NOT NULL DEFAULT ''global'' AFTER code',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 2) 绑定归属系统列 ────────────────────────────────────────────────────────
SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'auth_user_permission_set'
                 AND COLUMN_NAME = 'system_code');
SET @ddl := IF(@exist = 0,
    'ALTER TABLE auth_user_permission_set ADD COLUMN system_code VARCHAR(50) NOT NULL DEFAULT ''global'' AFTER user_id',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 3) 回填权限集归属系统（bi 平台角色）──────────────────────────────────────
UPDATE auth_permission_set SET system_code = 'bi'
 WHERE code IN ('platform_analyst', 'self_service_analyst', 'viewer', 'Editor')
   AND system_code <> 'bi';

-- ── 4) 回填绑定归属系统 = 其权限集归属系统 ───────────────────────────────────
-- 仅修正绑定系统与权限集归属不一致 *且* 权限集非 global 的行，避免误改「故意用 global
-- 权限集绑定特定系统」的场景。IGNORE 防唯一键冲突。此步在改唯一键之前，保证新键值已就位。
UPDATE IGNORE auth_user_permission_set ups
  JOIN auth_permission_set ps ON ps.id = ups.permission_set_id
   SET ups.system_code = ps.system_code
 WHERE ups.system_code <> ps.system_code
   AND ps.system_code <> 'global';

-- ── 5) 唯一键 (user_id, permission_set_id) → (user_id, system_code) ───────────
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'auth_user_permission_set'
               AND INDEX_NAME = 'uk_user_ps');
SET @ddl := IF(@idx > 0,
    'ALTER TABLE auth_user_permission_set DROP INDEX uk_user_ps',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE()
               AND TABLE_NAME = 'auth_user_permission_set'
               AND INDEX_NAME = 'uk_user_sys');
SET @ddl := IF(@idx = 0,
    'ALTER TABLE auth_user_permission_set ADD UNIQUE KEY uk_user_sys (user_id, system_code)',
    'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── 6) 预置权限集默认能力种子（仅 capabilities IS NULL 时回填，不覆盖已有库）──────
-- 值与前端 SYSTEM_PS_CAPS(bi-front/src/constants/capabilities.ts)对齐——单一事实源，改默认能力须两处同步。
UPDATE auth_permission_set SET capabilities =
'["dashboard:view","dashboard:edit","dashboard:delete","dashboard:publish","dashboard:share","dashboard:export","dashboard:embed","story:view","story:edit","story:delete","story:share","story:export","dataset:view","dataset:create","dataset:edit","dataset:delete","dataset:publish","semantic_model:view","semantic_model:edit","semantic_model:delete","semantic_model:publish","semantic_model:certify","datasource:view","datasource:create","datasource:edit","datasource:delete","datasource:test","agent:chat","agent:use:qa","agent:use:authoring","agent:view_session","agent:delete_session","agent:approve_action","agent:debug","pulse:view","pulse:create_metric","pulse:edit_metric","pulse:subscribe","pulse:subscribe_create","pulse:subscribe_receive","pulse:subscribe_others","pulse:admin_subscriptions","governance:view_lineage","governance:manage_label","governance:view_audit","governance:export_audit","security:rls","security:cls","security:mask_rule","security:permission","workspace:create","workspace:update","workspace:delete","workspace:manage_member","flow:manage","flow:execute","admin:manage_user","admin:manage_permission_set","admin:manage_idp","admin:manage_credential","admin:manage_alert_rule","admin:manage_config"]'
 WHERE code = 'admin' AND capabilities IS NULL;
UPDATE auth_permission_set SET capabilities =
'["dashboard:view","dashboard:edit","dashboard:delete","dashboard:publish","dashboard:share","dashboard:export","story:view","story:edit","story:delete","story:share","story:export","dataset:view","dataset:create","dataset:edit","dataset:delete","dataset:publish","semantic_model:view","semantic_model:edit","semantic_model:delete","semantic_model:publish","semantic_model:certify","datasource:view","datasource:create","datasource:edit","datasource:delete","datasource:test","agent:chat","agent:use:qa","agent:use:authoring","agent:view_session","pulse:view","pulse:create_metric","pulse:edit_metric","pulse:subscribe","pulse:subscribe_create","pulse:subscribe_receive","pulse:subscribe_others","governance:view_lineage","governance:view_audit","security:rls","security:cls","security:mask_rule","security:permission","flow:manage","flow:execute","workspace:create","workspace:update","workspace:manage_member"]'
 WHERE code = 'platform_analyst' AND capabilities IS NULL;
UPDATE auth_permission_set SET capabilities =
'["dashboard:view","dashboard:edit","dashboard:delete","dashboard:publish","dashboard:share","dashboard:export","story:view","story:edit","story:delete","story:share","story:export","dataset:view","dataset:create","semantic_model:view","datasource:view","agent:chat","agent:use:qa","agent:use:authoring","agent:view_session","pulse:view","pulse:subscribe","pulse:subscribe_create","pulse:subscribe_receive","security:permission","workspace:create","workspace:update"]'
 WHERE code = 'self_service_analyst' AND capabilities IS NULL;
UPDATE auth_permission_set SET capabilities =
'["dashboard:view","story:view","dataset:view","semantic_model:view","datasource:view","agent:chat","agent:use:qa","agent:use:authoring","agent:view_session","pulse:view","pulse:subscribe","pulse:subscribe_receive"]'
 WHERE code = 'viewer' AND capabilities IS NULL;

-- ── 7) 技能治理「防锁死」回填：给现持 agent:chat 者补 agent:use:qa / agent:use:authoring ──
-- 知数技能授权强制开启后按 agent:use:<分组> 裁剪，既有权限集不补授则开启后用不了（锁死）。
-- 幂等：NOT JSON_CONTAINS 守卫；capabilities 为 NULL 的行 JSON_CONTAINS 返回 NULL → 不命中。
UPDATE auth_permission_set
   SET capabilities = JSON_ARRAY_APPEND(
           JSON_ARRAY_APPEND(capabilities, '$', 'agent:use:qa'),
           '$', 'agent:use:authoring')
 WHERE JSON_CONTAINS(capabilities, '"agent:chat"')
   AND NOT JSON_CONTAINS(capabilities, '"agent:use:qa"');

-- ── 8) Agent 平台按系统鉴权隔离种子 ─────────────────────────────────────────
-- 网关按路径(/api/agent/builder/**、/api/agent/platform/**)把这些路由收窄到 agent 系统，
-- 从 systemPermissions.agent(缺失回退 global)注入 X-Permission-Set / X-Capabilities。
-- ① 注册 agent 系统能力码
INSERT IGNORE INTO auth_system_capability (system_code, capability_code, category, label, description) VALUES
('agent', 'admin:manage_agent', 'admin', '管理 Agent', '创建 / 编辑 / 上下线 Agent 平台的 Agent 定义');

-- ② Agent 系统管理员权限集 + 回填其归属系统/能力（防历史同名行不正确）
INSERT IGNORE INTO auth_permission_set (code, system_code, name, description, capabilities, is_system, sort_order) VALUES
('agent_admin', 'agent', 'Agent Admin', 'Manage agents on the Agent platform', '["admin:manage_agent"]', 1, 10);
UPDATE auth_permission_set SET system_code = 'agent'
 WHERE code = 'agent_admin' AND system_code <> 'agent';
UPDATE auth_permission_set SET capabilities = '["admin:manage_agent"]'
 WHERE code = 'agent_admin' AND capabilities IS NULL;

-- ③ 给现有超管(global 绑定 admin 者)补授 agent 系统管理员绑定（显式化 + 后台按系统展示）。
-- 唯一键 uk_user_sys + NOT EXISTS 守卫可重入；全新空库无超管时 SELECT 空集，不产生行。
INSERT IGNORE INTO auth_user_permission_set (user_id, system_code, permission_set_id)
SELECT ups.user_id, 'agent', (SELECT id FROM auth_permission_set WHERE code = 'agent_admin')
  FROM auth_user_permission_set ups
  JOIN auth_permission_set ps ON ps.id = ups.permission_set_id
 WHERE ps.code = 'admin'
   AND ups.system_code = 'global'
   AND EXISTS (SELECT 1 FROM auth_permission_set WHERE code = 'agent_admin')
   AND NOT EXISTS (
       SELECT 1 FROM auth_user_permission_set ex
        WHERE ex.user_id = ups.user_id AND ex.system_code = 'agent');
