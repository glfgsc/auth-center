package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.PlatformConfig;
import com.auth.center.service.IPlatformConfigService;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台配置管理端点 —— 认证中心「系统设置」按系统分区管理各系统的特性开关与平台级配置。
 *
 * 仅管理员可读写({@code @authPerm.isAdmin()})。子系统消费自己的配置走内部读端点 {@link
 * PlatformConfigInternalController}。BI 的运行时调优配置仍在 BI 本地 {@code sys_config},不在此。
 */
@RestController
@RequestMapping("/api/auth/admin/config")
@Validated
@PreAuthorize("@authPerm.isAdmin()")
public class PlatformConfigController {

    private final IPlatformConfigService configService;

    /**
     * 构造注入。
     *
     * @param configService 平台配置服务
     */
    public PlatformConfigController(IPlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 列出配置(按系统 / 分类过滤)。
     *
     * @param systemCode 系统精确过滤(可空 = 全部)
     * @param category 分类精确过滤(可空)
     * @return 配置项列表
     */
    @GetMapping
    public Result<List<PlatformConfig>> list(
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String category) {
        return Result.ok(configService.list(systemCode, category));
    }

    /**
     * 列出所有有配置的系统 code(admin 页分组用)。
     *
     * @return 系统 code 列表
     */
    @GetMapping("/systems")
    public Result<List<String>> systems() {
        return Result.ok(configService.systems());
    }

    /** 更新值请求体。 */
    public record UpdateValueRequest(@Size(max = 65535) String value) {}

    /**
     * 更新一条配置的值。
     *
     * @param id 配置 id
     * @param req 新值
     * @return 更新后的配置项;值不符类型 400
     */
    @PutMapping("/{id}")
    public Result<PlatformConfig> update(
            @PathVariable Long id, @RequestBody UpdateValueRequest req) {
        try {
            return Result.ok(configService.updateValue(id, req.value(), currentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 重置一条配置为默认值。
     *
     * @param id 配置 id
     * @return 重置后的配置项
     */
    @PostMapping("/{id}/reset")
    public Result<PlatformConfig> reset(@PathVariable Long id) {
        try {
            return Result.ok(configService.resetToDefault(id, currentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.fail(400, e.getMessage());
        }
    }

    /**
     * 当前操作人 userId —— 取自 {@code JwtAuthenticationFilter} 注入 details 的 userId。
     *
     * @return userId,取不到返回 null
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId instanceof Number n) {
                return n.longValue();
            }
        }
        return null;
    }
}
