-- V22: 登录设置与身份治理按产品(system_code)隔离
-- ----------------------------------------------------------------------------
-- 管理台的每个模块都按「当前选中的产品」展示,而不是把所有产品的数据摆在一起。
-- 审计 / 模型用量 / 平台配置 / 权限集 早已带 system_code,本迁移补齐剩下三张:
--
--   auth_sso_config     登录设置 —— 原本全表只有一行,所有产品共用,改一处全线生效
--   auth_group          用户组   —— 原本无产品维度
--   auth_login_history  登录历史 —— 原本无来源产品
--
-- system_code 一律与 auth_system.code 对齐(bi / agent / tracking / auth_center / global)。

-- ── 1. 登录设置:一产品一档,global 为兜底档 ──────────────────────────────────
-- 登录期解析顺序是「先按产品查,查不到回落 global」,所以 global 这一档必须能长期存在:
-- 存量行落到兜底位 —— DEFAULT 'global' 直接完成,现有各产品登录行为不变。
-- 产品要单独配置时新增一行覆盖,不动兜底档。

-- 1.0 先按 type 去重 —— 否则下面的唯一键建不起来,整个迁移中断。
--
-- 本迁移原假设「auth_sso_config 全表只有一行」,实况不成立:已删除的 /sso/config 写端点
-- 每次保存都是插入而非更新,于是攒出大量同 type 的重复行(实测一个环境 137 行、全 enabled=0)。
-- 加上 DEFAULT 'global' 后它们全落成 (global, <同一个 type>),唯一键 uk_sso_system_type
-- 必然冲突 —— Flyway 会在此中断,并在 schema_history 留下 success=0 的记录,导致 auth-center
-- 此后每次启动都直接拒绝(contains a failed migration),不是自愈的。
--
-- 保留哪一行:优先 enabled=1(真正在用的那份配置绝不能被删),其次 updated_at 最新、id 最大。
-- 全部 disabled 时即保留最近一次保存的那行,与「兜底档」的语义一致。
-- 内层派生表会被物化,故可在 DELETE 中引用同一张表。
DELETE c FROM auth_sso_config c
JOIN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY type
                   ORDER BY enabled DESC, updated_at DESC, id DESC
               ) AS rn
        FROM auth_sso_config
    ) ranked
    WHERE ranked.rn > 1
) dup ON dup.id = c.id;

ALTER TABLE auth_sso_config
    ADD COLUMN system_code VARCHAR(32) NOT NULL DEFAULT 'global'
        COMMENT '所属产品,对齐 auth_system.code;global=未单独配置的产品共用的兜底档'
        AFTER id;

ALTER TABLE auth_sso_config
    ADD UNIQUE KEY uk_sso_system_type (system_code, type);

-- 回填已由 DEFAULT 完成,随即摘掉默认值:此后不带产品的插入当场失败,
-- 而不是静默多出一条 global 兜底档把所有产品的登录方式一起改掉。
ALTER TABLE auth_sso_config
    ALTER COLUMN system_code DROP DEFAULT;

-- ── 2. 用户组:按产品隔离,唯一键随之带上产品 ────────────────────────────────
-- 组编码此前全平台唯一,不同产品无法各有一个同名组(如各自的 analysts)。
-- 唯一键改为 (system_code, code) 后即可;预置的 all_users 落在 global,
-- 语义正是「全部用户」,不属于任何单一产品。
ALTER TABLE auth_group
    ADD COLUMN system_code VARCHAR(32) NOT NULL DEFAULT 'global'
        COMMENT '所属产品,对齐 auth_system.code;global=不属于任何单一产品的全平台组'
        AFTER id;

ALTER TABLE auth_group DROP INDEX uk_group_code;
ALTER TABLE auth_group ADD UNIQUE KEY uk_group_system_code (system_code, code);

-- 这里保留 DEFAULT:不指明产品的组就是全平台组,'global' 是它真实的语义而非兜底猜测。

-- ── 3. 登录历史:记录这次登录来自哪个产品 ───────────────────────────────────
-- 可空,且不回填存量行:一次登录来自哪个产品只有调用方知道,存量行没这个信息,
-- 把它们统一写成某个产品或 global 都是编造。NULL 的含义是「调用方未报来源产品」,
-- 按产品筛选时这些行不出现 —— 这是如实反映,不是丢数据。
ALTER TABLE auth_login_history
    ADD COLUMN system_code VARCHAR(32) DEFAULT NULL
        COMMENT '来源产品,对齐 auth_system.code;NULL=调用方登录时未报来源产品'
        AFTER username;

ALTER TABLE auth_login_history
    ADD KEY idx_system_time (system_code, login_time);
