package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.ConnectedApp;
import com.auth.center.service.IConnectedAppService;
import jakarta.validation.Valid;
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
 * 外部应用（Connected App）管理端点 -- 注册、密钥轮换、状态与直信任公钥管理.
 *
 * 从 BI(bi-core-workspace)上移到认证中心，作为"外部应用与密钥"中心。所有操作需管理员权限；
 * 对齐行业嵌入式分析安全架构：密钥后端生成、双密钥轮换、域名白名单、Direct-Trust 公钥验签。
 */
@RestController
@Validated
@PreAuthorize("@authPerm.isAdmin()")
@RequestMapping("/api/auth/admin/connected-apps")
public class ConnectedAppController {

    private final IConnectedAppService connectedAppService;

    /**
     * 构造注入.
     *
     * @param connectedAppService Connected App 服务
     */
    public ConnectedAppController(IConnectedAppService connectedAppService) {
        this.connectedAppService = connectedAppService;
    }

    /**
     * 创建应用请求体.
     *
     * @param name 应用名称
     * @param allowedDomains 域名白名单 JSON 数组
     * @param targetSystem 目标系统（bi=洞察 / agent=知数 / tracking=循迹；空则默认 bi）
     * @param assertionPublicKeyPem Direct-Trust 断言验签公钥（PEM，可空）
     */
    public record CreateRequest(
            String name,
            String allowedDomains,
            String targetSystem,
            String assertionPublicKeyPem) {}

    /**
     * 更新应用请求体.
     *
     * @param name 应用名称
     * @param allowedDomains 域名白名单
     * @param targetSystem 目标系统（null 表示不改）
     * @param status 状态
     * @param assertionPublicKeyPem Direct-Trust 断言验签公钥（PEM，null 表示不改，空串清除）
     */
    public record UpdateRequest(
            String name,
            String allowedDomains,
            String targetSystem,
            String status,
            String assertionPublicKeyPem) {}

    /**
     * 列出 Connected App，可按目标系统收窄.
     *
     * @param targetSystem 目标系统编码；不传则返回全部
     * @return 应用列表（含密钥摘要）
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list(
            @RequestParam(required = false) String targetSystem) {
        List<Map<String, Object>> apps = connectedAppService.listAll();
        if (targetSystem == null || targetSystem.isBlank()) {
            return Result.ok(apps);
        }
        return Result.ok(
                apps.stream().filter(a -> targetSystem.equals(a.get("targetSystem"))).toList());
    }

    /**
     * 创建 Connected App -- 自动生成 clientId 和第一个 secret.
     *
     * 响应中的 {@code secretValue} 仅此一次可见，前端须提示用户立即复制保存。
     *
     * @param req 创建请求
     * @return {@code {app, clientId, secretId, secretValue}}
     */
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody CreateRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            return Result.fail("name is required");
        }
        try {
            Map<String, Object> result =
                    connectedAppService.create(
                            req.name(),
                            req.allowedDomains(),
                            req.targetSystem(),
                            req.assertionPublicKeyPem(),
                            getCurrentUserId());
            return Result.ok(result);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新 Connected App 基本信息.
     *
     * @param id 应用 ID
     * @param req 更新请求
     * @return 更新后的应用
     */
    @PutMapping("/{id}")
    public Result<ConnectedApp> update(
            @PathVariable Long id, @Valid @RequestBody UpdateRequest req) {
        try {
            ConnectedApp updated =
                    connectedAppService.update(
                            id,
                            req.name(),
                            req.allowedDomains(),
                            req.targetSystem(),
                            req.status(),
                            req.assertionPublicKeyPem());
            return Result.ok(updated);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 删除 Connected App（级联删除所有密钥）.
     *
     * @param id 应用 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            connectedAppService.delete(id);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 为指定应用生成新密钥（最多 2 个并行）.
     *
     * 响应中的 {@code secretValue} 仅此一次可见。
     *
     * @param id 应用 ID
     * @return {@code {secretId, secretValue}}
     */
    @PostMapping("/{id}/secrets")
    public Result<Map<String, String>> generateSecret(@PathVariable Long id) {
        try {
            return Result.ok(connectedAppService.generateSecret(id));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 撤销指定密钥 -- 使用该密钥签名的请求立即失效.
     *
     * @param id 应用 ID
     * @param secretId 密钥标识
     * @return 操作结果
     */
    @DeleteMapping("/{id}/secrets/{secretId}")
    public Result<Void> revokeSecret(@PathVariable Long id, @PathVariable String secretId) {
        try {
            connectedAppService.revokeSecret(id, secretId);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 从 SecurityContext 获取当前用户 ID.
     *
     * @return 用户 ID，未认证时 {@code null}
     */
    @SuppressWarnings("unchecked")
    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
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
