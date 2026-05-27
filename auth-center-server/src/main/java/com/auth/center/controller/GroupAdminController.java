package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthGroup;
import com.auth.center.entity.AuthGroupMember;
import com.auth.center.service.IGroupService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户组管理控制器 -- 组的 CRUD 与成员管理.
 *
 * <p>所有端点需要认证（SecurityConfig 中 {@code /api/auth/admin/**} 要求认证）。</p>
 */
@RestController
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
     * GET / -- 列出所有用户组.
     *
     * @return 用户组列表
     */
    @GetMapping
    public Result<List<AuthGroup>> listAll() {
        return Result.ok(groupService.listAll());
    }

    /**
     * GET /{id} -- 查询用户组详情.
     *
     * @param id 组 ID
     * @return 用户组
     */
    @GetMapping("/{id}")
    public Result<AuthGroup> getById(@PathVariable Long id) {
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
    public Result<AuthGroup> create(@RequestBody AuthGroup group) {
        Long creatorId = getCurrentUserId();
        AuthGroup created = groupService.create(group, creatorId);
        return Result.ok(created);
    }

    /**
     * PUT /{id} -- 更新用户组信息.
     *
     * @param id    组 ID
     * @param patch 待更新字段（name / description）
     * @return 更新后的组
     */
    @PutMapping("/{id}")
    public Result<AuthGroup> update(@PathVariable Long id, @RequestBody AuthGroup patch) {
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
    public Result<Void> delete(@PathVariable Long id) {
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
    public Result<List<AuthGroupMember>> listMembers(@PathVariable Long id) {
        return Result.ok(groupService.listMembers(id));
    }

    /**
     * POST /{id}/members -- 批量添加成员.
     *
     * @param id      组 ID
     * @param request 请求体 {userIds: [...]}
     * @return 操作结果
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/{id}/members")
    public Result<Void> addMembers(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        List<Number> raw = (List<Number>) request.get("userIds");
        List<Long> userIds = raw.stream().map(Number::longValue).toList();
        groupService.addMembers(id, userIds);
        return Result.ok();
    }

    /**
     * DELETE /{id}/members -- 批量移除成员.
     *
     * @param id      组 ID
     * @param request 请求体 {userIds: [...]}
     * @return 操作结果
     */
    @SuppressWarnings("unchecked")
    @DeleteMapping("/{id}/members")
    public Result<Void> removeMembers(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        List<Number> raw = (List<Number>) request.get("userIds");
        List<Long> userIds = raw.stream().map(Number::longValue).toList();
        groupService.removeMembers(id, userIds);
        return Result.ok();
    }

    /**
     * 从 SecurityContext 获取当前用户 ID.
     *
     * @return 用户 ID，未认证时返回 null
     */
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        try {
            return Long.valueOf(auth.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
