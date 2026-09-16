-- 把预置权限集 viewer 恢复成「只读」—— 摘掉线上漂移进去的三个数据集写能力。
--
-- 现状:viewer 是 is_system=1 的预置集,名称 Viewer、描述 "Read-only access",V2 种子给它的是
-- 12 个纯读能力(dashboard:view / story:view / dataset:view / semantic_model:view / datasource:view
-- / agent:* / pulse:view / pulse:subscribe)。而线上这一行是 16 项,多出来的正是
--   dataset:create、dataset:edit、dataset:delete
-- 三个写能力。种子的回填条件是 `WHERE code='viewer' AND capabilities IS NULL`(一次性),之后
-- 在管理台勾选的改动会一直留存 —— 所以这是运行期漂移,不是种子问题。
--
-- 为什么必须摘:
-- 1) 语义自相矛盾。一个叫 Viewer、写着 Read-only access 的预置集带着建/改/删数据集的能力,
--    管理员按名字授权时拿到的东西和名字不符 —— 这类「名实不符」是最容易被误授的一档。
-- 2) 它实打实放大了越权面。dataset:delete 是 Extract 实体级端点的门之一,viewer 持有它意味着
--    「只读用户」也能触达那些破坏性操作;对象级工作区门补上之后跨工作区已挡住,但同工作区内
--    仍不该由只读角色执行删除。
--
-- 只摘这三个,不重写整个数组:viewer 上另有 agent:use:inspector / agent:use:curation 等运行期
-- 加进来的能力,那些是智能体技能门(是否可用某技能),不构成数据写入,属于合理定制,予以保留。
--
-- 影响面(执行时点的持有者):qiqi、admin123、claude_test 三个账号将失去建/改/删数据集的能力。
-- 若其中某个账号确实需要做数据集建模,正确做法是改授与之相符的预置集 —— self_service_analyst
-- (含 dataset:create,自助分析语义)或 Editor(含 create/edit/delete)—— 而不是把写能力塞回
-- 只读集:预置集的语义是契约,按名字授权的人依赖它。
--
-- 幂等:JSON_OVERLAPS 守卫使本迁移只碰真含这三个码的行,重复执行为空操作。
-- 作用域限定 is_system=1 且 code='viewer',不影响任何自定义权限集。

UPDATE auth_permission_set ps
JOIN (
    SELECT p.id,
           JSON_ARRAYAGG(t.cap) AS kept
    FROM auth_permission_set p,
         JSON_TABLE(p.capabilities, '$[*]' COLUMNS (cap VARCHAR(128) PATH '$')) AS t
    WHERE p.code = 'viewer'
      AND p.is_system = 1
      AND t.cap NOT IN ('dataset:create', 'dataset:edit', 'dataset:delete')
    GROUP BY p.id
) k ON k.id = ps.id
SET ps.capabilities = k.kept
WHERE ps.capabilities IS NOT NULL
  AND JSON_OVERLAPS(
        ps.capabilities,
        CAST('["dataset:create","dataset:edit","dataset:delete"]' AS JSON)
      );
