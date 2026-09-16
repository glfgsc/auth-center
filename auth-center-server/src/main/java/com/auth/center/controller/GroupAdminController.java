package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.controller.request.GroupMemberRequest;
import com.auth.center.entity.AuthGroup;
import com.auth.center.entity.AuthGroupMember;
import com.auth.center.service.IGroupService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * 用户组管理控制器 -- 组的 CRUD 与成员管理.
 *
 * 所有端点需要认证（SecurityConfig 中 {@code /api/auth/admin/**} 要求认证）。
 */
@RestController
@Validated
@RequestMapping("/api/auth/admin/groups")
public class GroupAdminController {

    private final IGroupService groupService;

    /**
     * 构造函数.
     *
     * @param groupService 用户组服务
     */
    public GroupAdminController(IGroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * GET / -- 列出用户组，可按产品收窄.
     *
     * @param systemCode 产品编码；不传则返回全部产品的组
     * @return 用户组列表
     */
    @GetMapping
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<List<AuthGroup>> listAll(@RequestParam(required = false) String systemCode) {
        return Result.ok(groupService.listAll(systemCode));
    }

    /**
     * GET /{id} -- 查询用户组详情.
     *
     * @param id 组 ID
     * @return 用户组
     */
    @GetMapping("/{id}")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<AuthGroup> getById(@PathVariable @Positive Long id) {
        AuthGroup group = groupService.getById(id);
        if (group == null) {
            return Result.fail("用户组不存在");
        }
        return Result.ok(group);
    }

    /**
     * POST / -- 创建用户组.
     *
     * @param group 组信息（code / name / description）
     * @return 创建后的组
     */
    @PostMapping
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<AuthGroup> create(@Valid @RequestBody AuthGroup group) {
        Long creatorId = getCurrentUserId();
        AuthGroup created = groupService.create(group, creatorId);
        return Result.ok(created);
    }

    /**
     * PUT /{id} -- 更新用户组信息.
     *
     * @param id 组 ID
     * @param patch 待更新字段（name / description）
     * @return 更新后的组
     */
    @PutMapping("/{id}")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<AuthGroup> update(
            @PathVariable @Positive Long id, @Valid @RequestBody AuthGroup patch) {
        AuthGroup updated = groupService.update(id, patch);
        return Result.ok(updated);
    }

    /**
     * DELETE /{id} -- 删除用户组（系统预置组不可删）.
     *
     * @param id 组 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<Void> delete(@PathVariable @Positive Long id) {
        try {
            groupService.delete(id);
            return Result.ok();
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * GET /{id}/members -- 查询组成员列表.
     *
     * @param id 组 ID
     * @return 带用户信息的成员列表
     */
    @GetMapping("/{id}/members")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<List<AuthGroupMember>> listMembers(@PathVariable @Positive Long id) {
        return Result.ok(groupService.listMembers(id));
    }

    /**
     * POST /{id}/members -- 批量添加成员.
     *
     * @param id 组 ID
     * @param request 成员操作请求（userIds 列表）
     * @return 操作结果
     */
    @PostMapping("/{id}/members")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<Void> addMembers(
            @PathVariable @Positive Long id, @Valid @RequestBody GroupMemberRequest request) {
        groupService.addMembers(id, request.getUserIds());
        return Result.ok();
    }

    /**
     * DELETE /{id}/members -- 批量移除成员.
     *
     * @param id 组 ID
     * @param request 成员操作请求（userIds 列表）
     * @return 操作结果
     */
    @DeleteMapping("/{id}/members")
    @PreAuthorize("@authPerm.hasCapability('admin:manage_user')")
    public Result<Void> removeMembers(
            @PathVariable @Positive Long id, @Valid @RequestBody GroupMemberRequest request) {
        groupService.removeMembers(id, request.getUserIds());
        return Result.ok();
    }

    /**
     * 从 SecurityContext 获取当前用户 ID.
     *
     * @return 用户 ID，未认证时返回 null
     */
    @SuppressWarnings("unchecked")
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (details instanceof Map) {
            Object userId = ((Map<String, Object>) details).get("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            }
            if (userId instanceof Number) {
                return ((Number) userId).longValue();
            }
        }
        return null;
    }
}
