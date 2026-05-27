package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.PermissionSet;
import com.auth.center.service.IPermissionSetService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 权限集管理控制器 -- 提供权限集 CRUD 的 RESTful 端点.
 *
 * <p>系统预设权限集（isSystem=1）不允许删除。
 * 所有端点面向管理后台使用。</p>
 */
@RestController
@RequestMapping("/api/admin/permission-sets")
public class PermissionSetController {

    private final IPermissionSetService permissionSetService;

    /**
     * 构造函数，注入权限集服务.
     *
     * @param permissionSetService 权限集服务
     */
    public PermissionSetController(IPermissionSetService permissionSetService) {
        this.permissionSetService = permissionSetService;
    }

    /**
     * 查询所有权限集列表.
     *
     * @return 权限集列表
     */
    @GetMapping
    public Result<List<PermissionSet>> list() {
        return Result.ok(permissionSetService.list());
    }

    /**
     * 根据 ID 查询权限集.
     *
     * @param id 权限集主键 ID
     * @return 权限集实体
     */
    @GetMapping("/{id}")
    public Result<PermissionSet> getById(@PathVariable Long id) {
        PermissionSet ps = permissionSetService.getById(id);
        if (ps == null) {
            return Result.fail("权限集不存在");
        }
        return Result.ok(ps);
    }

    /**
     * 创建或更新权限集（upsert）.
     *
     * <p>若请求体中 id 为 {@code null} 则新增，否则按 id 更新已有记录。</p>
     *
     * @param ps 权限集实体
     * @return 保存后的权限集主键 ID
     */
    @PostMapping
    public Result<Long> save(@RequestBody PermissionSet ps) {
        if (ps.getCode() == null || ps.getCode().isBlank()) {
            return Result.fail("权限集编码不能为空");
        }
        Long id = permissionSetService.save(ps);
        return Result.ok(id);
    }

    /**
     * 删除权限集.
     *
     * <p>系统预设权限集不允许删除，尝试删除时返回错误信息。</p>
     *
     * @param id 权限集主键 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            permissionSetService.delete(id);
            return Result.ok();
        } catch (IllegalStateException e) {
            return Result.fail(e.getMessage());
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
