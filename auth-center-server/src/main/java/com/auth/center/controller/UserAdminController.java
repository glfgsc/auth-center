package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthUser;
import com.auth.center.service.IUserAdminService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理控制器 -- 提供用户 CRUD 和权限集分配的 RESTful 端点.
 *
 * <p>所有端点需要管理员权限。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {

    private final IUserAdminService userAdminService;

    /**
     * 构造函数，注入用户管理服务.
     *
     * @param userAdminService 用户管理服务
     */
    public UserAdminController(IUserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 查询所有用户列表.
     *
     * @return 用户列表
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @GetMapping
    public Result<List<AuthUser>> list() {
        return Result.ok(userAdminService.list());
    }

    /**
     * 根据 ID 查询单个用户.
     *
     * @param id 用户主键 ID
     * @return 用户实体
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @GetMapping("/{id}")
    public Result<AuthUser> getById(@PathVariable Long id) {
        AuthUser user = userAdminService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        return Result.ok(user);
    }

    /**
     * 创建新用户.
     *
     * @param user 用户实体（需包含 username、password）
     * @return 新创建用户的主键 ID
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @PostMapping
    public Result<Long> create(@RequestBody AuthUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            return Result.fail("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            return Result.fail("密码不能为空");
        }
        Long id = userAdminService.create(user);
        return Result.ok(id);
    }

    /**
     * 更新用户信息.
     *
     * @param id   用户主键 ID（路径参数）
     * @param user 用户实体（id 从路径参数取）
     * @return 操作结果
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody AuthUser user) {
        user.setId(id);
        userAdminService.update(user);
        return Result.ok();
    }

    /**
     * 删除用户.
     *
     * @param id 用户主键 ID
     * @return 操作结果
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userAdminService.delete(id);
        return Result.ok();
    }

    /**
     * 为用户分配权限集.
     *
     * @param userId 用户 ID
     * @param psId   权限集 ID
     * @return 操作结果
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @PostMapping("/{userId}/permission-set/{psId}")
    public Result<Void> assignPermissionSet(
            @PathVariable Long userId,
            @PathVariable Long psId) {
        userAdminService.assignPermissionSet(userId, psId);
        return Result.ok();
    }

    /**
     * 移除用户的权限集关联.
     *
     * @param userId 用户 ID
     * @param psId   权限集 ID
     * @return 操作结果
     */
    // TODO: @PreAuthorize("@authPerm.isAdmin()")
    @DeleteMapping("/{userId}/permission-set/{psId}")
    public Result<Void> removePermissionSet(
            @PathVariable Long userId,
            @PathVariable Long psId) {
        userAdminService.removePermissionSet(userId, psId);
        return Result.ok();
    }
}
