-- V5: Agent 平台权限模块补齐（对齐 BI 分层）+ 系统注册表（管理台系统清单后端驱动）
-- ----------------------------------------------------------------------------
-- 背景：agent 系统此前仅 1 档权限集（agent_admin）+ 1 个能力码（admin:manage_agent），
-- 且管理台前端硬编码系统白名单不含 'agent' → agent 权限集在后台不可见、不可配。
-- 本迁移：
--   ① 注册 agent 系统细粒度能力目录（与 agent-server @PreAuthorize 强制的能力码一一对应）；
--   ② 补 agent_builder / agent_viewer 两档权限集，并把 agent_admin 升级为含全部细粒度能力
--      （保留 admin:manage_agent 作向后兼容伞，存量超管重登前不掉权）；
--   ③ 建 auth_system 系统注册表，令管理台系统清单由后端数据驱动（不再前端硬编码白名单）。
-- 平台隔离不变：所有 agent 能力/权限集 system_code='agent'，网关只在 /api/agent/builder|platform 注入。
-- 全部 INSERT IGNORE / 守卫式 UPDATE / CREATE TABLE IF NOT EXISTS，对既有库幂等收敛。

-- ── 1) 注册 agent 系统细粒度能力目录（category='agent'）────────────────────────
INSERT IGNORE INTO auth_system_capability (system_code, capability_code, category, label, description) VALUES
('agent', 'agent:view',         'agent', '查看智能体',   '查看 Agent 定义 / 版本 / 审计 / 运行状态（只读）'),
('agent', 'agent:create',       'agent', '创建智能体',   '在 Agent 平台创建新的 Agent'),
('agent', 'agent:edit',         'agent', '编辑智能体',   '编辑 Agent 定义 / 主题 / 转接 / 动作库'),
('agent', 'agent:publish',      'agent', '上下线智能体', '激活 / 停用 / 回滚 Agent（生命周期）'),
('agent', 'agent:manage_skill', 'agent', '技能治理',     '启停技能包（平台级，影响所有 Agent 运行时可用技能）');

-- ── 2) 升级 agent_admin：补入全部细粒度能力（保留 admin:manage_agent 向后兼容伞）──
-- 逐项 JSON_ARRAY_APPEND + NOT JSON_CONTAINS 守卫：幂等且不覆盖既有自定义。
UPDATE auth_permission_set SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'agent:view')
 WHERE code = 'agent_admin' AND NOT JSON_CONTAINS(capabilities, '"agent:view"');
UPDATE auth_permission_set SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'agent:create')
 WHERE code = 'agent_admin' AND NOT JSON_CONTAINS(capabilities, '"agent:create"');
UPDATE auth_permission_set SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'agent:edit')
 WHERE code = 'agent_admin' AND NOT JSON_CONTAINS(capabilities, '"agent:edit"');
UPDATE auth_permission_set SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'agent:publish')
 WHERE code = 'agent_admin' AND NOT JSON_CONTAINS(capabilities, '"agent:publish"');
UPDATE auth_permission_set SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'agent:manage_skill')
 WHERE code = 'agent_admin' AND NOT JSON_CONTAINS(capabilities, '"agent:manage_skill"');

-- ── 3) 新增 agent_builder / agent_viewer 两档权限集（system_code='agent'）────────
-- Builder：创作 + 上下线，不含平台级技能治理；Viewer：只读。
INSERT IGNORE INTO auth_permission_set (code, system_code, name, description, capabilities, is_system, sort_order) VALUES
('agent_builder', 'agent', 'Agent Builder', '创建 / 编辑 / 上下线 Agent（不含平台级技能治理）',
 '["agent:view","agent:create","agent:edit","agent:publish"]', 1, 11),
('agent_viewer',  'agent', 'Agent Viewer',  '只读查看 Agent 定义 / 审计 / 运行状态',
 '["agent:view"]', 1, 12);

-- ── 4) 系统注册表（管理台系统清单后端驱动，替代前端硬编码白名单）────────────────
CREATE TABLE IF NOT EXISTS auth_system (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    code       VARCHAR(50)  NOT NULL COMMENT '系统编码，与权限集/绑定的 system_code 对齐',
    name       VARCHAR(100) NOT NULL COMMENT '系统展示名（前端缺 i18n 时回退）',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT '管理台展示顺序',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='已接入平台/系统注册表';

INSERT IGNORE INTO auth_system (code, name, sort_order) VALUES
('bi',          'BI 平台',    10),
('agent',       'Agent 平台', 20),
('tracking',    '埋点平台',   30),
('auth_center', '认证中心',   40),
('global',      '全局',       99);
