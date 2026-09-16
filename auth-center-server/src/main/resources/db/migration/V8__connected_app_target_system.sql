-- V8: 外部应用改造为跨系统统一模型 —— 去掉洞察特有的"资产范围/工作区",新增"目标系统"维度
-- ----------------------------------------------------------------------------
-- auth-center 是跨系统身份中心,"工作区/资产范围"是洞察(BI)特有概念,不应下沉到中心:
--   * 知数/循迹 并无"工作区"语义,把 workspace_id 摆在中心是抽象泄漏;
--   * 内容作用域改由目标系统按登录用户的真实权限(工作区成员 + RLS)裁决,应用只证明身份。
-- 因此:删除 asset_scope / workspace_id,新增 target_system 记录应用嵌入进哪个系统。
-- ALTER 依赖 V7 已建表(asset_scope / workspace_id 一定存在);Flyway 版本化保证只执行一次。

ALTER TABLE auth_connected_app
    ADD COLUMN target_system VARCHAR(32) NOT NULL DEFAULT 'bi'
        COMMENT '目标系统:应用嵌入进哪个系统(bi=洞察 / agent=知数 / tracking=循迹)'
        AFTER allowed_domains;

ALTER TABLE auth_connected_app DROP COLUMN asset_scope;
ALTER TABLE auth_connected_app DROP COLUMN workspace_id;
