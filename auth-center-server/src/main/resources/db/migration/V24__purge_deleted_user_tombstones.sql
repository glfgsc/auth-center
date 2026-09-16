-- 清理 auth_user 的软删墓碑,解封被占用的用户名。
--
-- auth_user.username 上的唯一索引不含 deleted 列,而软删只把行标成 deleted=1、用户名仍占着索引;
-- MyBatis-Plus 又给所有查询自动追加 deleted=0,于是按用户名查不到墓碑、径直 INSERT 撞唯一键 ——
-- 同名用户删除后既无法经 CAS 即时建号重新登录,也无法在管理台重建。删除路径已同步改为物理删除
-- (AuthUserMapper#physicalDeleteById),本迁移清掉此前遗留的墓碑,否则那些用户名仍然建不回来。
--
-- 先清子行再清主行:墓碑行的权限集绑定与组成员在软删时虽已随级联清掉,但历史数据未必干净,
-- 按外键方向兜一遍避免留下悬挂引用。幂等:无墓碑时三条语句均影响 0 行。

DELETE FROM auth_user_permission_set
WHERE user_id IN (SELECT id FROM auth_user WHERE deleted = 1);

DELETE FROM auth_group_member
WHERE user_id IN (SELECT id FROM auth_user WHERE deleted = 1);

DELETE FROM auth_user WHERE deleted = 1;
