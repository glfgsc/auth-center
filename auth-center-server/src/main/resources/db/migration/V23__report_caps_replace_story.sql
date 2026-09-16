-- 分析报告下沉为工作区资产、故事模块下线:权限集里的 story:* 逐位换成 report:*。
--
-- 映射为 1:1(view/edit/delete/share 四位,story:export 已在 V19 摘除),原本持有 story:X 的
-- 权限集换成持有 report:X,其余能力原样保留。JSON_TABLE 重建 + 去重,幂等:重复执行时无
-- story:* 命中,不改任何行。能力目录本身由 bi-core 启动时经 /api/auth/capabilities/register
-- 全量替换(story:* 随代码下线自然消失,report:* 随注册出现),这里只处理持有关系。

UPDATE auth_permission_set ps
JOIN (
    SELECT p.id,
           JSON_ARRAYAGG(c.cap) AS rebuilt
    FROM (
        SELECT DISTINCT p2.id,
               CASE WHEN t.cap LIKE 'story:%'
                    THEN CONCAT('report:', SUBSTRING(t.cap, 7))
                    ELSE t.cap
               END AS cap
        FROM auth_permission_set p2,
             JSON_TABLE(p2.capabilities, '$[*]' COLUMNS (cap VARCHAR(128) PATH '$')) AS t
    ) c
    JOIN auth_permission_set p ON p.id = c.id
    GROUP BY p.id
) x ON x.id = ps.id
SET ps.capabilities = x.rebuilt
WHERE JSON_OVERLAPS(ps.capabilities,
      JSON_ARRAY('story:view', 'story:edit', 'story:delete', 'story:share'));
