-- 拆分 datasource:edit —— 把「对该数据源跑任意 SQL」从「管连接配置」里分出来。
--
-- 背景:裸 SQL / 裸联邦端点(/query/execute、/query/execute-direct、/federated/execute 等)由调用方自带
-- SQL,没有可锚的表集,因此绕过 RLS/CLS 与表级授权 —— 持有它等价于「该数据源全表全列可见」。
-- 它此前与「改连接串、改数据源配置」共用 datasource:edit,意味着能维护数据源的人必然能看全库,
-- 这个等式无法单独收回。
--
-- 本迁移把裸 SQL 侧收到新能力码 datasource:raw_query,并据此重划预置权限集:
--
--   admin            —— 补授 raw_query(它本就持全部能力,行为不变)
--   platform_analyst —— **不补**。它同时持 security:rls / security:cls(配数据策略)与裸 SQL 能力,
--                       构成自我豁免:自己配的行列策略自己能绕过去。拆开后它保留配策略的能力,
--                       失去绕过策略的能力 —— 这正是拆分要达成的效果。
--   其余预置集       —— 本就不持 datasource:edit,不受影响。
--
-- 破坏性影响(必须知晓):platform_analyst 及任何自定义权限集里只有 datasource:edit 的用户,
-- 从此调裸 SQL 端点得 403。受影响的真实功能:数据集画布的跨源联接预览、联邦查询的执行/刷新、
-- SQL 探列。若这些角色确需保留,由管理员在权限集里显式补授 datasource:raw_query ——
-- 显式补授与「自动继承」的区别正是本次拆分的意义:授出去的是一个写明了后果的能力。

-- 能力码本身不在此注册:它由 bi-core 启动时经 /api/auth/capabilities/register 自注册进
-- auth_system_capability(见 CapabilityRegistrar),迁移只负责重划预置权限集的持有关系。
--
-- admin 补授 —— 幂等:仅在尚未包含时追加,避免重复执行把同一能力码塞进去两次。
UPDATE auth_permission_set
SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'datasource:raw_query')
WHERE code = 'admin'
  AND capabilities IS NOT NULL
  AND JSON_SEARCH(capabilities, 'one', 'datasource:raw_query') IS NULL;

-- 3) platform_analyst 刻意不补 —— 见文件头说明。此处不写 UPDATE,是有意为之而非遗漏。
