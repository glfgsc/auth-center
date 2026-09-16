package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.controller.request.AssignPermissionSetRequest;
import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.service.IPermissionSetService;
import com.auth.center.service.IUserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理控制器 -- 提供用户 CRUD 和权限集分配的 RESTful 端点.
 *
 * 所有端点需要管理员权限。
 */
@RestController
@Validated
@RequestMapping("/api/auth/admin/users")
public class UserAdminController {

    private final IUserAdminService userAdminService;
    private final IPermissionSetService permissionSetService;

    /**
     * 构造函数，注入用户管理服务和权限集服务.
     *
     * @param userAdminService 用户管理服务
     * @param permissionSetService 权限集服务
     */
    public UserAdminController(
            IUserAdminService userAdminService, IPermissionSetService permissionSetService) {
        this.userAdminService = userAdminService;
        this.permissionSetService = permissionSetService;
    }

    /**
     * 查询所有用户列表（含权限集信息）.
     *
     * 每条记录包含用户基础字段和额外字段: {@code permissionSet}（权限集编码）、{@code permissionSetName}（权限集名称）。
     *
     * @return 带权限集信息的用户列表
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String systemCode) {
        return Result.ok(userAdminService.listWithPermissionInfo(systemCode));
    }

    /**
     * 根据 ID 查询单个用户.
     *
     * @param id 用户主键 ID
     * @return 用户实体
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @GetMapping("/{id}")
    public Result<AuthUser> getById(@PathVariable @Positive Long id) {
        AuthUser user = userAdminService.getById(id);
        if (user == null) {
            return Result.fail("用户不存在");
        }
        // 纵深防御：即使 @JsonProperty(WRITE_ONLY) 阻止序列化，仍主动清除密码哈希
        user.setPassword(null);
        return Result.ok(user);
    }

    /**
     * 创建新用户.
     *
     * @param user 用户实体（需包含 username、password）
     * @return 新创建用户的主键 ID
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody AuthUser user) {
        Long id = userAdminService.create(user);
        return Result.ok(id);
    }

    /**
     * 更新用户信息.
     *
     * @param id 用户主键 ID（路径参数）
     * @param user 用户实体（id 从路径参数取）
     * @return 操作结果
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive Long id, @Valid @RequestBody AuthUser user) {
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
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        userAdminService.delete(id);
        return Result.ok();
    }

    /**
     * 为用户分配权限集（通过编码）.
     *
     * 前端传递 {@code { "permissionSetCode": "admin" }} 格式的 JSON 请求体，后端通过编码解析权限集 ID 后执行分配。
     *
     * @param userId 用户 ID（路径参数）
     * @param request 包含 {@code permissionSetCode} 的请求体
     * @return 操作结果
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @PutMapping("/{userId}/permission-set")
    public Result<Void> assignPermissionSet(
            @PathVariable @Positive Long userId,
            @Valid @RequestBody AssignPermissionSetRequest request) {
        try {
            userAdminService.assignPermissionSetByCode(userId, request.getPermissionSetCode());
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 移除用户在指定系统的权限集关联.
     *
     * @param userId 用户 ID
     * @param psId 权限集 ID
     * @return 操作结果
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @DeleteMapping("/{userId}/permission-set/{psId}")
    public Result<Void> removePermissionSet(
            @PathVariable @Positive Long userId, @PathVariable @Positive Long psId) {
        userAdminService.removePermissionSet(userId, psId);
        return Result.ok();
    }

    /**
     * 查询可用权限集列表 -- 供编辑用户权限时下拉选择.
     *
     * 当传入 systemCode 时，返回该系统 + global 的权限集；不传时返回全部。
     *
     * @param systemCode 系统编码（可选，如 bi / tracking）
     * @return 权限集列表
     */
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    @GetMapping("/permission-sets")
    public Result<List<PermissionSet>> listPermissionSets(
            @RequestParam(required = false) String systemCode) {
        return Result.ok(permissionSetService.list(systemCode));
    }
}
