-- Setup 审计轨迹 —— 记录认证中心内所有管理配置变更(用户/权限集/用户组/SSO/连接应用的增删改)。
-- 配置面审计轨迹(Setup Audit Trail):只记人工管理员对"配置面"的写操作,回答「谁在何时改了什么」。
-- 由 SetupAuditAspect 切面自动写入;服务自注册(capabilities/register)与非写探测(cas/test)不记。
CREATE TABLE IF NOT EXISTS auth_setup_audit (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    actor_user_id  BIGINT       DEFAULT NULL COMMENT '操作管理员用户 id',
    actor_username VARCHAR(128) DEFAULT NULL COMMENT '操作管理员用户名',
    module         VARCHAR(64)  NOT NULL COMMENT '模块: user/permission_set/group/sso/connected_app',
    action         VARCHAR(32)  NOT NULL COMMENT '操作: create/update/delete/assign/add_member/remove_member/generate_secret/revoke_secret',
    target_type    VARCHAR(64)  DEFAULT NULL COMMENT '目标类型',
    target_id      VARCHAR(128) DEFAULT NULL COMMENT '目标 id(路径变量)',
    target_name    VARCHAR(255) DEFAULT NULL COMMENT '目标名称(尽力从请求体提取)',
    detail         MEDIUMTEXT   DEFAULT NULL COMMENT '请求参数/变更载荷(脱敏后 JSON)',
    http_method    VARCHAR(8)   DEFAULT NULL COMMENT 'HTTP 方法',
    path           VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    ip             VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    user_agent     VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    status         INT          DEFAULT NULL COMMENT '结果状态(200 成功, 其余为失败码)',
    error_msg      VARCHAR(1024) DEFAULT NULL COMMENT '错误信息(失败时)',
    duration_ms    BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
    created_at     DATETIME     NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_actor (actor_user_id),
    KEY idx_module (module),
    KEY idx_action (action),
    KEY idx_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心 Setup 审计轨迹';
