package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.SsoConfig;
import com.auth.center.service.ISsoConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * SSO 配置管理控制器 -- 提供 SSO 配置的查询和更新端点.
 *
 * <p>端点分为公开和受保护两类:
 * <ul>
 *     <li>{@code GET /api/auth/sso/public-config} - 公开端点，前端登录页获取 SSO 模式</li>
 *     <li>{@code GET /api/auth/sso/config} - 需认证，管理员查看完整配置</li>
 *     <li>{@code PUT /api/auth/sso/config} - 需认证，管理员更新配置</li>
 * </ul>
 */
@RestController
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
     * <p>仅返回 mode、displayName、serverUrl、enabled 四个字段，
     * 不暴露内部配置细节。</p>
     *
     * @return 公开 SSO 配置摘要
     */
    @GetMapping("/public-config")
    public Result<Map<String, Object>> getPublicConfig() {
        return Result.ok(ssoConfigService.getPublicConfig());
    }

    /**
     * 获取完整 SSO 配置 -- 管理员查看使用.
     *
     * <p>需要认证，返回 SSO 配置实体的所有字段。</p>
     *
     * @return 完整 SSO 配置实体
     */
    @GetMapping("/config")
    public Result<SsoConfig> getConfig() {
        SsoConfig config = ssoConfigService.getConfig();
        return Result.ok(config);
    }

    /**
     * 更新 SSO 配置 -- 管理员操作.
     *
     * <p>需要认证。若配置不存在则新增，否则更新已有记录。</p>
     *
     * @param config 待保存的 SSO 配置实体
     * @return 保存后的 SSO 配置实体
     */
    @PutMapping("/config")
    public Result<SsoConfig> updateConfig(@RequestBody SsoConfig config) {
        SsoConfig saved = ssoConfigService.updateConfig(config);
        return Result.ok(saved);
    }
}
