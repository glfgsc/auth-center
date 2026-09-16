package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.service.ISsoConfigService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO 配置公开查询控制器 -- 登录页渲染所需的 SSO 摘要({@code GET /api/auth/sso/public-config?system=})。
 *
 * 配置按产品存放，{@code system} 参数缺省时取 {@code global} 兜底档。类上的 {@code @PreAuthorize} 是本类的默认拒绝
 * 闸，唯一的公开端点在方法上显式 {@code permitAll()} 放行。
 *
 * 管理面的 SSO 读写在 {@link SsoAdminController}（{@code /api/auth/sso/admin/**}）—— 那边收 DTO 而非实体，是
 * 管理台唯一在用的写入路径。
 */
@RestController
@Validated
@PreAuthorize("@authPerm.hasCapability('admin:manage_idp')")
@RequestMapping("/api/auth/sso")
public class SsoConfigController {

    private final ISsoConfigService ssoConfigService;

    /**
     * 构造函数，注入 SSO 配置服务.
     *
     * @param ssoConfigService SSO 配置服务
     */
    public SsoConfigController(ISsoConfigService ssoConfigService) {
        this.ssoConfigService = ssoConfigService;
    }

    /**
     * 获取公开 SSO 配置 -- 前端登录页渲染使用.
     *
     * 仅返回 mode、displayName、serverUrl、enabled 四个字段，不暴露内部配置细节。
     *
     * @param system 发起登录的产品编码；不传即取 {@code global} 兜底档
     * @return 公开 SSO 配置摘要
     */
    @PreAuthorize("permitAll()")
    @GetMapping("/public-config")
    public Result<Map<String, Object>> getPublicConfig(
            @RequestParam(required = false) String system) {
        return Result.ok(ssoConfigService.getPublicConfig(system));
    }
}
