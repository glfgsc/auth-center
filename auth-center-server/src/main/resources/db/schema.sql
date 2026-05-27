-- =============================================
-- Auth Center 数据库初始化脚本
-- =============================================

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
