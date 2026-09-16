-- V26: 通知渠道凭据 —— 「用哪个凭据」这一层上移认证中心,与 V25 的「允不允许」合到一处
-- ----------------------------------------------------------------------------
-- V25 只把开关放了上来,凭据仍留在各产品自己那儿,结果是同一个值班群要在洞察和知数各配一遍,
-- 且知数那套(agent_webhook)把 webhook 地址与加签密钥<b>明文</b>落库 —— 而 webhook 地址本身
-- 就是凭据(企微 ?key= / 飞书 /hook/<token> / 钉钉 ?access_token=)。
--
-- 照 V7 连接应用的成例上移:认证中心管身份与秘密,目标系统管内容作用域。
--
-- scope_ref 刻意做成<b>不透明</b>:V8 的教训是把 BI 的 workspace_id 逐字搬进认证中心属抽象泄漏
-- (「工作区」是洞察特有的概念,知数没有)。这里认证中心只把它当字符串存取与做 IN 匹配,
-- 语义完全由 target_system 自己定义 —— 洞察写 'workspace:0'(全站)或 'workspace:5',
-- 知数暂时写 NULL(全局可用)。认证中心永远不去解析这个串。

CREATE TABLE IF NOT EXISTS auth_notification_credential (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    target_system         VARCHAR(32)  NOT NULL COMMENT '归属产品:bi=洞察 / agent=知数;取自 /admin/systems 注册表',
    cred_key              VARCHAR(64)  NOT NULL COMMENT '引用名,告警与订阅以它引用;同产品内唯一',
    cred_type             VARCHAR(32)  NOT NULL COMMENT '渠道类型:DINGTALK_BOT / LARK_BOT / WECOM_BOT / WECOM_APP / SMTP / HTTP_BEARER / HTTP_BASIC',
    display_name          VARCHAR(128) NOT NULL COMMENT '显示名',
    description           VARCHAR(512) NULL COMMENT '备注,如这个群是干什么的',
    encrypted_secret_json TEXT         NOT NULL COMMENT 'AES-256-GCM 密文(ENC: 前缀);地址、加签密钥、口令全在里面,读回一律掩码',
    scope_ref             VARCHAR(128) NULL COMMENT '作用域引用,由 target_system 自行解释;认证中心只做字符串匹配,不解析语义',
    enabled               TINYINT      NOT NULL DEFAULT 1 COMMENT '停用后不下发,引用它的告警与订阅会投递失败',
    last_test_status      VARCHAR(16)  NULL COMMENT '最近一次连通性测试:OK / FAILED',
    last_test_at          DATETIME     NULL,
    last_test_message     VARCHAR(512) NULL COMMENT '测试结果说明;不得写入完整 URL',
    last_rotated_at       DATETIME     NULL COMMENT '密钥最近一次被整体替换的时间',
    created_by            BIGINT       NULL COMMENT '创建者用户 ID',
    created_at            DATETIME     NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME     NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted               TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 逻辑删除下唯一键要带 deleted,否则删掉再建同名会撞唯一键(本仓踩过的老坑)
    UNIQUE KEY uk_notif_cred_key (target_system, cred_key, deleted),
    KEY idx_notif_cred_scope (target_system, scope_ref, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='通知渠道凭据(跨产品统一;秘密经 AES-256-GCM 加密)';
