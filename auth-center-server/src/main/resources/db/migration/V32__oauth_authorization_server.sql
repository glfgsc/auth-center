-- OAuth 2.1 客户端注册表。与 Connected App 分离：后者仍服务于嵌入 Direct-Trust，
-- 本表只服务授权码 + PKCE + MCP resource audience。
CREATE TABLE IF NOT EXISTS auth_oauth_client (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    client_id            VARCHAR(96)  NOT NULL,
    client_name          VARCHAR(128) NOT NULL,
    client_type          VARCHAR(16)  NOT NULL COMMENT 'public / confidential',
    client_secret_hash   VARCHAR(128) NULL COMMENT 'SHA-256；public client 为空',
    redirect_uris         TEXT         NOT NULL COMMENT 'JSON string array; exact match only',
    scopes               TEXT         NOT NULL COMMENT 'JSON string array',
    resource_audience    VARCHAR(500) NOT NULL,
    token_ttl_seconds    INT          NOT NULL DEFAULT 900,
    status               VARCHAR(16)  NOT NULL DEFAULT 'enabled',
    created_by           BIGINT       NULL,
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_client_id (client_id),
    KEY idx_oauth_client_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='OAuth 2.1 Authorization Server client registrations';

-- OAuth Authorization Server 的单例运行配置。默认值来自部署配置，保存后以本表为准。
CREATE TABLE IF NOT EXISTS auth_oauth_settings (
    id                              BIGINT       NOT NULL,
    issuer                          VARCHAR(500) NOT NULL,
    mcp_resource_url                VARCHAR(500) NOT NULL,
    scopes                          TEXT         NOT NULL COMMENT 'JSON string array',
    access_token_ttl_seconds       INT          NOT NULL DEFAULT 900,
    refresh_token_ttl_seconds      INT          NOT NULL DEFAULT 604800,
    authorization_code_ttl_seconds INT          NOT NULL DEFAULT 60,
    resource_name                   VARCHAR(200) NOT NULL,
    resource_documentation          VARCHAR(500) NULL,
    updated_by                      BIGINT       NULL,
    updated_at                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='OAuth 2.1 Authorization Server runtime configuration';
