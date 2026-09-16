-- 通知渠道开关(站点级)—— 管理员在这里决定「这个产品允许用哪些渠道」。
--
-- 分两层是有意的:允不允许在这里(按产品分域、跨产品一处可见),用哪个凭据在各产品自己的凭据中心
-- (地址与加签密钥是秘密,需要可逆加密与掩码,不适合放进这张明文 KV 表)。终端用户只能在管理员
-- 已开启的渠道里选,自己碰不到任何出网地址。
--
-- 值为逗号分隔的渠道码;留空表示该产品不允许任何渠道(通知功能整体不暴露)。
INSERT INTO auth_platform_config
    (system_code, config_key, config_value, value_type, category, label, description, default_value, sort_order, created_at)
VALUES
    ('bi', 'notification.channels.allowed', 'email,wecom,dingtalk,lark', 'STRING', 'notification',
     '允许的通知渠道', '订阅摘要与指标告警可用的渠道;移除某项后用户界面不再出现该渠道',
     'email,wecom,dingtalk,lark', 2, NOW()),
    ('agent', 'notification.channels.allowed', 'wecom,dingtalk,lark,generic', 'STRING', 'notification',
     '允许的通知渠道', '流程「发送通知」步骤可用的渠道;移除某项后已配该厂商的渠道不再下发运行时',
     'wecom,dingtalk,lark,generic', 3, NOW());
