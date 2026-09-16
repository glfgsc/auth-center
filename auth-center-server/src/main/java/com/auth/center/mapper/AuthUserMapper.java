package com.auth.center.mapper;

import com.auth.center.entity.AuthUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 认证用户 Mapper 接口.
 *
 * 继承 MyBatis-Plus {@link BaseMapper}，提供 {@link AuthUser} 的基础 CRUD 操作。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUser> {

    /**
     * 物理删除用户行 —— 绕开 {@link AuthUser} 上 {@code @TableLogic} 注入的软删。
     *
     * {@code auth_user.username} 上有唯一索引，且该索引不含 {@code deleted} 列；而软删只把行标成 {@code
     * deleted=1}、用户名仍占着索引。同时 MyBatis-Plus 会给所有查询自动追加 {@code deleted=0}，于是 {@code findByUsername}
     * 看不见那条墓碑、径直 {@code INSERT} —— 撞唯一键。表现为同名用户删除后既无法经 CAS 即时建号重新登录，也无法在管理台重建。
     *
     * 用户侧没有任何恢复入口（无 restore 端点、无已删除用户视图），墓碑纯属死数据；而 {@code delete} 的级联子行（权限
     * 集绑定、组成员）本就是硬删。故主行一并物理删除，与子行一致。
     *
     * @param id 用户主键
     * @return 影响行数
     */
    @Delete("DELETE FROM auth_user WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);
}
