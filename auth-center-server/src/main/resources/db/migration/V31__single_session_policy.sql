-- 并发会话上限 —— 一个用户同时能有几条活跃会话,超出的在新登录时按最近活跃顺序被顶下线。
-- 1 = 单设备登录(默认);0 = 不限。认证中心登录收尾处读取(SessionPolicy),不加缓存,改完下一次登录即生效。
-- INSERT IGNORE 靠 uk_system_key 幂等收敛,重跑或已手工建过都不会覆盖现值。
INSERT IGNORE INTO auth_platform_config
    (system_code, config_key, config_value, value_type, category, label, description, default_value, sort_order, created_at)
VALUES
    ('auth_center', 'security.session.maxPerUser', '1', 'INTEGER', 'security', '并发会话上限',
     '同一账号可同时活跃的会话数,超出时新登录顶掉最早的;0 表示不限', '1', 1, NOW());
