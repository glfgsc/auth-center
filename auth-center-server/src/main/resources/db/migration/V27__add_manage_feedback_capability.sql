-- 注册「用户反馈管理」能力 admin:manage_feedback，并补授给 admin 权限集。
-- 与 bi-core CapabilityRegistrar 的声明一致 —— 那里是 bi 系统能力的唯一真源，本迁移只负责
-- 让默认 admin 预置集在升级后立刻可用，不必人工去权限集里补勾。
--
-- 刻意与 admin:manage_config 分开：分诊用户反馈的常常是产品或支持人员，他们该看得到用户提了
-- 什么，但不该顺带获得改平台运行参数的权限。要给这类角色开权限时，单独勾这一个即可。

-- ① 注册 bi 系统能力码（幂等 INSERT IGNORE）
INSERT IGNORE INTO auth_system_capability (system_code, capability_code, category, label, description) VALUES
('bi', 'admin:manage_feedback', 'admin', '用户反馈管理', '查看用户提交的意见反馈并流转处理状态');

-- ② 补授 admin 权限集（幂等：NOT JSON_CONTAINS 守卫）
UPDATE auth_permission_set
   SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'admin:manage_feedback')
 WHERE code = 'admin'
   AND capabilities IS NOT NULL
   AND NOT JSON_CONTAINS(capabilities, '"admin:manage_feedback"');
