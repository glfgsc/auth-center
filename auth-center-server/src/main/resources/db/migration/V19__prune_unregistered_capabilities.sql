-- 从预置权限集里摘掉 12 个「注册表没有、代码里也没人校验」的能力码。
--
-- 背景:能力自注册落地后,各子系统启动时经 /api/auth/capabilities/register 上报自身能力目录,
-- auth_system_capability 成为能力码的唯一真源(bi 56 条 + agent 6 条)。但权限集的能力码是
-- V1/V2 时期的种子直接写进 auth_permission_set.capabilities 的,那批老码没跟着一起收敛 ——
-- 后来的审计把它们从能力目录里删了,权限集里的持有关系却留到了现在。
--
-- 现象:auth-center 管理台的权限集编辑器按「注册表 ∪ 权限集在用 ∪ 当前已选」取并集(自愈,
-- 防注册表不全时勾不到已授的能力),这些孤儿码因此照样列出来;又因为不在注册表里拿不到 label,
-- 落到 users.capAct.<动作> 兜底,动作名不在通用动作表里的就 humanize 成英文短语
-- (subscribe others / create metric / execute …),看起来像「i18n 漏翻」,实际是这些码不该存在。
--
-- 摘除不改变任何行为:这 12 个码在 bi / agent / auth-center 三侧源码里都没有强制点
-- (对照:同期的 dataset:edit 有 45 处引用、pulse:view 37 处),属于「授了但没人校验」的死授权。
-- 其中 dataset:publish 与 story:export 的动作名恰好撞上通用动作(发布 / 导出)被翻成了中文,
-- 看着正常但同样是死码,一并摘除。
--
--   story:export                admin, platform_analyst, self_service_analyst
--   dataset:publish             admin, platform_analyst, Editor
--   pulse:create_metric         admin, platform_analyst
--   pulse:subscribe_create      admin, platform_analyst, self_service_analyst
--   pulse:subscribe_receive     admin, platform_analyst, self_service_analyst, viewer
--   pulse:subscribe_others      admin, platform_analyst
--   pulse:admin_subscriptions   admin
--   governance:review           admin, platform_analyst
--   governance:apply            admin, platform_analyst, self_service_analyst
--   governance:export_audit     admin
--   flow:manage                 admin, platform_analyst
--   flow:execute                admin, platform_analyst
--
-- Pulse 订阅仍由注册表里的 pulse:subscribe(订阅指标告警)把守(SubscriptionController),
-- 审计导出仍由 governance:view_audit 把守 —— 摘掉的是同域里从未接线的那几个细分码,不是整块能力。
--
-- 若将来要恢复其中某个能力,正确做法是先在 bi-core 的 CapabilityRegistrar 声明(带中文 label)、
-- 再在对应控制器加 @PreAuthorize("@perm.hasCapability('...')"),最后才由管理员在权限集里勾选 ——
-- 而不是把码写回种子:注册表是唯一真源,种子只负责持有关系。

-- 1) 重建受影响权限集的能力数组,滤掉死码。
--    JSON_OVERLAPS 守卫让本迁移幂等且只碰真含死码的行(重复执行为空操作)。
UPDATE auth_permission_set ps
JOIN (
    SELECT p.id,
           JSON_ARRAYAGG(t.cap) AS kept
    FROM auth_permission_set p,
         JSON_TABLE(p.capabilities, '$[*]' COLUMNS (cap VARCHAR(128) PATH '$')) AS t
    WHERE t.cap NOT IN (
        'story:export',
        'dataset:publish',
        'pulse:create_metric',
        'pulse:subscribe_create',
        'pulse:subscribe_receive',
        'pulse:subscribe_others',
        'pulse:admin_subscriptions',
        'governance:review',
        'governance:apply',
        'governance:export_audit',
        'flow:manage',
        'flow:execute'
    )
    GROUP BY p.id
) k ON k.id = ps.id
SET ps.capabilities = k.kept
WHERE ps.capabilities IS NOT NULL
  AND JSON_OVERLAPS(
        ps.capabilities,
        CAST(
            '["story:export","dataset:publish","pulse:create_metric","pulse:subscribe_create",'
            '"pulse:subscribe_receive","pulse:subscribe_others","pulse:admin_subscriptions",'
            '"governance:review","governance:apply","governance:export_audit",'
            '"flow:manage","flow:execute"]' AS JSON
        )
      );

-- 2) 边界情况:若某权限集的能力码「全部」都是死码,上面的 GROUP BY 不会产出它的行(被 WHERE 全滤掉),
--    数组会原样留着。这里单独把这类集合清空,保证摘除彻底。当前预置集都不属于这种情况,
--    此语句是给自定义权限集兜底的。
UPDATE auth_permission_set ps
JOIN (
    SELECT p.id
    FROM auth_permission_set p,
         JSON_TABLE(p.capabilities, '$[*]' COLUMNS (cap VARCHAR(128) PATH '$')) AS t
    GROUP BY p.id
    HAVING SUM(
        t.cap NOT IN (
            'story:export',
            'dataset:publish',
            'pulse:create_metric',
            'pulse:subscribe_create',
            'pulse:subscribe_receive',
            'pulse:subscribe_others',
            'pulse:admin_subscriptions',
            'governance:review',
            'governance:apply',
            'governance:export_audit',
            'flow:manage',
            'flow:execute'
        )
    ) = 0
) z ON z.id = ps.id
SET ps.capabilities = JSON_ARRAY()
WHERE ps.capabilities IS NOT NULL;
