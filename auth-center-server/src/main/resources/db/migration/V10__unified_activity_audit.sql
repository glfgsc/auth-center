-- 统一活动审计中枢 —— 认证中心汇集全平台「谁改了什么」的活动审计。
-- 字段为 BI sys_audit_log ∪ 认证中心 setup 审计 的超集,+ source_system 区分来源(auth_center / bi)。
-- 认证中心自身 setup 审计并入本表(source=auth_center),原 auth_setup_audit 迁数据后废弃。
CREATE TABLE IF NOT EXISTS auth_audit_log (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    source_system  VARCHAR(32)  NOT NULL COMMENT '来源系统: auth_center / bi',
    actor_user_id  BIGINT       DEFAULT NULL COMMENT '操作人用户 id(可空,BI 侧只带 username)',
    actor_username VARCHAR(128) DEFAULT NULL COMMENT '操作人用户名',
    module         VARCHAR(64)  DEFAULT NULL COMMENT '模块',
    operation      VARCHAR(255) DEFAULT NULL COMMENT '操作描述',
    operation_type VARCHAR(32)  DEFAULT NULL COMMENT '操作类型: create/update/delete/query/export/...',
    method         VARCHAR(8)   DEFAULT NULL COMMENT 'HTTP 方法',
    path           VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    params         MEDIUMTEXT   DEFAULT NULL COMMENT '请求参数/变更载荷(脱敏后)',
    result         MEDIUMTEXT   DEFAULT NULL COMMENT '响应结果(可能截断)',
    old_value      MEDIUMTEXT   DEFAULT NULL COMMENT '变更前旧值',
    new_value      MEDIUMTEXT   DEFAULT NULL COMMENT '变更后新值',
    diff_result    VARCHAR(512) DEFAULT NULL COMMENT '变更差异描述',
    target_type    VARCHAR(64)  DEFAULT NULL COMMENT '目标资产类型',
    target_id      VARCHAR(128) DEFAULT NULL COMMENT '目标资产 id',
    target_name    VARCHAR(255) DEFAULT NULL COMMENT '目标名称',
    tenant_id      BIGINT       DEFAULT NULL COMMENT '租户 id(BI 隔离用)',
    workspace_id   BIGINT       DEFAULT NULL COMMENT '工作区 id(BI)',
    sensitive_op   TINYINT      DEFAULT NULL COMMENT '敏感操作标记(1=敏感)',
    ip             VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    user_agent     VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
    status         INT          DEFAULT NULL COMMENT '结果状态(200 成功)',
    error_msg      VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    duration_ms    BIGINT       DEFAULT NULL COMMENT '耗时(毫秒)',
    created_at     DATETIME     NOT NULL COMMENT '发生时间',
    PRIMARY KEY (id),
    KEY idx_source (source_system),
    KEY idx_actor (actor_username),
    KEY idx_module (module),
    KEY idx_created (created_at),
    KEY idx_target (target_type, target_id),
    KEY idx_sensitive (sensitive_op)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心统一活动审计';

-- 并入认证中心自身的 setup 审计(action → operation_type,detail → params)。
INSERT INTO auth_audit_log
    (source_system, actor_user_id, actor_username, module, operation_type, method, path,
     params, target_type, target_id, target_name, ip, user_agent, status, error_msg,
     duration_ms, created_at)
SELECT 'auth_center', actor_user_id, actor_username, module, action, http_method, path,
       detail, target_type, target_id, target_name, ip, user_agent, status, error_msg,
       duration_ms, created_at
FROM auth_setup_audit;

-- setup 审计已并入统一表,原表废弃。
DROP TABLE IF EXISTS auth_setup_audit;
