package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthSystem;
import com.auth.center.service.ISystemService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统注册表控制器 -- 提供已接入平台/系统清单的查询端点.
 *
 * 管理台「用户与权限」页据此动态渲染系统分组（BI 平台 / Agent 平台 / 全局等），替代前端硬编码系统白名单：新增平台只需在 {@code auth_system}
 * 登记即自动出现。
 */
@RestController
@Validated
@PreAuthorize("@authPerm.isAdmin()")
@RequestMapping("/api/auth/admin/systems")
public class SystemController {

    private final ISystemService systemService;

    /**
     * 构造函数，注入系统注册表服务.
     *
     * @param systemService 系统注册表服务
     */
    public SystemController(ISystemService systemService) {
        this.systemService = systemService;
    }

    /**
     * 查询全部已接入系统（按展示顺序升序）.
     *
     * @return 系统列表
     */
    @GetMapping
    public Result<List<AuthSystem>> list() {
        return Result.ok(systemService.list());
    }
}
