-- 登录历史 —— 记录每一次登录尝试(成功 + 失败),对齐业界身份安全:可追溯 IP / 设备 / 地点 / 状态 / 异常。
-- 会话管理(Redis auth:session:{familyId})记「谁在线」;本表记「谁尝试登录过、成功与否、是否异常」。
CREATE TABLE IF NOT EXISTS auth_login_history (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       DEFAULT NULL COMMENT '用户 id(用户不存在的失败尝试为空)',
    username     VARCHAR(64)  NOT NULL COMMENT '登录用户名(尝试值)',
    login_time   DATETIME     NOT NULL COMMENT '登录时间',
    ip           VARCHAR(64)  DEFAULT NULL COMMENT '客户端 IP',
    ip_class     VARCHAR(16)  DEFAULT NULL COMMENT 'IP 归类: INTERNAL / PUBLIC',
    location     VARCHAR(128) DEFAULT NULL COMMENT '精确地理位置(公网经 GeoIP 解析,可空)',
    user_agent   VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent 原串(前端解析设备/浏览器)',
    status       VARCHAR(16)  NOT NULL COMMENT '结果: SUCCESS / FAILED',
    fail_reason  VARCHAR(128) DEFAULT NULL COMMENT '失败原因(可空)',
    session_id   VARCHAR(64)  DEFAULT NULL COMMENT '成功时的会话 id(= refresh 族 id)',
    anomalies    VARCHAR(255) DEFAULT NULL COMMENT '异常标记,逗号分隔: NEW_IP / NEW_DEVICE / FAILED_BURST / CONCURRENT_LOCATION',
    tenant_id    BIGINT       DEFAULT NULL COMMENT '租户 id',
    created_at   DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_user_time (user_id, login_time),
    KEY idx_username_time (username, login_time),
    KEY idx_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '认证中心登录历史';
