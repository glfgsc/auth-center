-- 按系统分区的平台配置中心 —— 认证中心统一管理各系统的特性开关与平台级配置。
-- 键值模型 + system_code 维度(global 跨系统 / bi 洞察 / agent 知数 / tracking 循迹 / auth_center 中心自身)。
-- 各子系统经读端点(/api/auth/config/values?systemCode=X)取自己的中心配置;BI 的运行时调优配置仍留 BI 本地 sys_config。
CREATE TABLE IF NOT EXISTS auth_platform_config (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    system_code   VARCHAR(32)  NOT NULL COMMENT '系统: global / bi / agent / tracking / auth_center',
    config_key    VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value  TEXT         DEFAULT NULL COMMENT '配置值(按 value_type 解释)',
    value_type    VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '类型: STRING/INTEGER/DOUBLE/BOOLEAN/JSON',
    category      VARCHAR(64)  DEFAULT NULL COMMENT '分类(admin 页分组)',
    label         VARCHAR(128) DEFAULT NULL COMMENT '显示名',
    description    VARCHAR(512) DEFAULT NULL COMMENT '说明',
    default_value TEXT         DEFAULT NULL COMMENT '默认值(重置用)',
    sort_order    INT          DEFAULT 0 COMMENT '排序',
    updated_by    BIGINT       DEFAULT NULL COMMENT '最后修改人 userId',
    updated_at    DATETIME     DEFAULT NULL COMMENT '最后修改时间',
    created_at    DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_system_key (system_code, config_key),
    KEY idx_system (system_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心平台配置(按系统分区)';

-- 种子:各系统的代表性特性开关与平台级配置。子系统按需读取;认证中心自身消费 audit.* / platform.*。
INSERT INTO auth_platform_config
    (system_code, config_key, config_value, value_type, category, label, description, default_value, sort_order, created_at)
VALUES
    ('global', 'platform.displayName', '数织', 'STRING', 'platform', '平台名称', '各产品顶栏品牌名前缀', '数织', 1, NOW()),
    ('global', 'platform.session.idleTimeoutMinutes', '30', 'INTEGER', 'platform', '会话空闲超时(分钟)', '无操作多久后要求重新登录', '30', 2, NOW()),
    ('global', 'audit.retentionDays', '90', 'INTEGER', 'audit', '审计保留天数', '统一审计中枢记录保留时长', '90', 3, NOW()),
    ('bi', 'feature.marketplace.enabled', 'true', 'BOOLEAN', 'feature', '资产市场', '是否开放跨工作区资产市场', 'true', 1, NOW()),
    ('agent', 'feature.multiAgent.enabled', 'true', 'BOOLEAN', 'feature', '多智能体编排', '是否允许智能体间转接(handoff)', 'true', 1, NOW()),
    ('agent', 'agent.trust.enabledByDefault', 'false', 'BOOLEAN', 'feature', '新智能体默认开信任层', '新建智能体是否默认启用信任层护栏', 'false', 2, NOW()),
    ('tracking', 'feature.tracking.enabled', 'true', 'BOOLEAN', 'feature', '循迹总开关', '是否启用埋点采集', 'true', 1, NOW());
