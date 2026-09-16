-- V7: 外部应用（Connected App）注册表 —— "外部应用与密钥"中心上移到认证中心
-- ----------------------------------------------------------------------------
-- 把嵌入信任的"连接应用注册 + 密钥/公钥/域名白名单/资产范围"从 BI(bi-core-workspace headless)
-- 上移到 auth-center,与已有的 embed 铸造(EmbedInternalController)+ JWKS + CAS SSO 统一到中心。
-- 结构对齐 BI 的 bi_connected_app(_secret)(V1 建表 + V16 补 assertion_public_key_pem),此处一次建全。
-- 全 CREATE TABLE IF NOT EXISTS,对既有库幂等。

CREATE TABLE IF NOT EXISTS auth_connected_app (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    name                      VARCHAR(128) NOT NULL COMMENT '应用显示名称，如"CRM 客户分析"',
    client_id                 VARCHAR(64)  NOT NULL COMMENT '自动生成的应用标识（UUID）',
    allowed_domains           TEXT         NULL COMMENT 'JSON 数组：允许嵌入的域名白名单',
    asset_scope               VARCHAR(16)  NOT NULL DEFAULT 'all' COMMENT '资产范围：all=全部 / workspace=限定工作区',
    workspace_id              BIGINT       NULL COMMENT 'asset_scope=workspace 时绑定的工作区 ID',
    status                    VARCHAR(16)  NOT NULL DEFAULT 'enabled' COMMENT 'enabled / disabled',
    assertion_public_key_pem  TEXT         NULL COMMENT 'Direct-Trust 断言验签公钥（PEM，RS256）；空=未启用直信任嵌入',
    created_by                BIGINT       NULL COMMENT '创建者用户 ID',
    created_at                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connected_app_client_id (client_id),
    KEY idx_connected_app_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='外部应用（Connected App）注册表';

CREATE TABLE IF NOT EXISTS auth_connected_app_secret (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    app_id       BIGINT       NOT NULL COMMENT '所属 Connected App',
    secret_id    VARCHAR(64)  NOT NULL COMMENT '密钥标识（公开，用于识别是哪个密钥）',
    secret_hash  VARCHAR(128) NOT NULL COMMENT 'SHA-256 hash（不存原文）',
    created_at   DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_connected_app_secret_id (secret_id),
    KEY idx_connected_app_secret_app (app_id),
    CONSTRAINT fk_connected_app_secret FOREIGN KEY (app_id)
        REFERENCES auth_connected_app (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Connected App 密钥（SHA-256 哈希，最多 2 个并行以支持无停机轮换）';
