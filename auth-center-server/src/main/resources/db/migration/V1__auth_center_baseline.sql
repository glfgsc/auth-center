-- auth_center 初始化 schema(Flyway 基线)
-- 后续 schema 变更新增 V2__*.sql、V3__*.sql ...
-- 全部 CREATE TABLE IF NOT EXISTS / INSERT IGNORE：对「引入 Flyway 时已存在数据」的
-- 老库以 baseline-on-migrate 从此版本重放时天然为空操作，不破坏既有数据。

-- 认证用户表
CREATE TABLE IF NOT EXISTS auth_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(100)  NOT NULL UNIQUE,
    password    VARCHAR(255)  NOT NULL,
    nickname    VARCHAR(100),
    avatar      VARCHAR(500),
    email       VARCHAR(200),
    phone       VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT  DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 权限集表
CREATE TABLE IF NOT EXISTS auth_permission_set (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(50)  NOT NULL UNIQUE,
    name         VARCHAR(100) NOT NULL,
    description  VARCHAR(500),
    capabilities TEXT,
    is_system    TINYINT DEFAULT 0,
    sort_order   INT     DEFAULT 0,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置 4 个系统权限集（capabilities 由子系统注册后填充）
INSERT IGNORE INTO auth_permission_set (code, name, description, is_system, sort_order) VALUES
('admin',                  'Admin',                  'Full platform access',            1, 1),
('platform_analyst',       'Platform Analyst',       'Create and manage analytics content', 1, 2),
('self_service_analyst',   'Self-Service Analyst',   'Create personal analytics',       1, 3),
('viewer',                 'Viewer',                 'View published content',          1, 4);

-- 用户-权限集关联表
CREATE TABLE IF NOT EXISTS auth_user_permission_set (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id           BIGINT   NOT NULL,
    permission_set_id BIGINT   NOT NULL,
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_ps (user_id, permission_set_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 跨系统能力注册表
CREATE TABLE IF NOT EXISTS auth_system_capability (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    system_code      VARCHAR(50)  NOT NULL,
    capability_code  VARCHAR(100) NOT NULL,
    category         VARCHAR(50),
    label            VARCHAR(200),
    description      VARCHAR(500),
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_system_cap (system_code, capability_code),
    INDEX idx_system (system_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- SSO 配置表
CREATE TABLE IF NOT EXISTS auth_sso_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    type         VARCHAR(50)  NOT NULL DEFAULT 'CAS',
    mode         VARCHAR(20)  NOT NULL DEFAULT 'disabled',
    server_url   VARCHAR(500),
    display_name VARCHAR(200),
    icon         VARCHAR(32)  DEFAULT '',
    config_json  TEXT,
    enabled      TINYINT(1)   NOT NULL DEFAULT 0,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 预置默认 SSO 配置（关闭状态）
INSERT IGNORE INTO auth_sso_config (type, mode, enabled)
VALUES ('CAS', 'disabled', 0);

-- 用户组表
CREATE TABLE IF NOT EXISTS auth_group (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(50)  NOT NULL COMMENT '唯一编码',
    name        VARCHAR(100) NOT NULL COMMENT '显示名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '描述',
    is_system   TINYINT      NOT NULL DEFAULT 0 COMMENT '系统预置不可删：1=是，0=否',
    created_by  BIGINT       DEFAULT NULL COMMENT '创建人 ID',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户组';

-- 用户组成员关联表
CREATE TABLE IF NOT EXISTS auth_group_member (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    group_id   BIGINT   NOT NULL COMMENT 'FK -> auth_group.id',
    user_id    BIGINT   NOT NULL COMMENT 'FK -> auth_user.id',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_member (group_id, user_id),
    KEY idx_gm_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户组成员';

-- 预置 all_users 系统组
INSERT IGNORE INTO auth_group (code, name, description, is_system) VALUES
('all_users', '全部用户', '包含所有注册用户的系统组', 1);

-- 将现有用户全部加入 all_users 组
INSERT IGNORE INTO auth_group_member (group_id, user_id)
SELECT g.id, u.id
FROM auth_group g, auth_user u
WHERE g.code = 'all_users'
  AND u.deleted = 0;
