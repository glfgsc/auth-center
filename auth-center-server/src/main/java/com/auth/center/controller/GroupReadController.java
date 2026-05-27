package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthGroup;
import com.auth.center.service.IGroupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户组只读控制器 -- 面向所有已认证用户和内部服务调用.
 *
 * <p>不需要管理员权限。供内容权限引擎和选人组件使用。
 * 管理类操作（CRUD / 成员管理）在 {@link GroupAdminController}。</p>
 */
@RestController
@RequestMapping("/api/auth/groups")
public class GroupReadController {

    private final IGroupService groupService;

    /**
     * 构造函数.
     *
     * @param groupService 用户组服务
     */
    public GroupReadController(IGroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * GET /list -- 列出所有用户组（仅 id + name），供选择器使用.
     *
     * @return 用户组摘要列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listAll() {
        List<AuthGroup> groups = groupService.listAll();
        List<Map<String, Object>> result = new ArrayList<>(groups.size());
        for (AuthGroup g : groups) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("name", g.getName());
            result.add(m);
        }
        return Result.ok(result);
    }

    /**
     * GET /internal/user-groups/{userId} -- 内部调用：查询用户所属的所有组 ID.
     *
     * <p>供 bi-service-security 内容权限引擎 Layer 3 调用。</p>
     *
     * @param userId 用户 ID
     * @return 组 ID 列表
     */
    @GetMapping("/internal/user-groups/{userId}")
    public Result<List<Long>> getGroupIdsByUserId(@PathVariable Long userId) {
        return Result.ok(groupService.getGroupIdsByUserId(userId));
    }
}
