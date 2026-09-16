package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.NotificationCredential;
import com.auth.center.notification.NotificationChannelType;
import com.auth.center.service.INotificationCredentialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * 通知渠道凭据管理面 -- 平台管理员在认证中心统一维护各产品的渠道.
 *
 * 收敛的理由:同一个值班群机器人此前要在洞察和知数各配一遍,而知数那套还把 webhook 地址与加签密钥明文落库. 认证中心本就是两侧共同的上游(知数不依赖洞察,却依赖认证中心),
 * 凭据放这里既统一又不新增跨产品依赖.
 *
 * 秘密只进不出:列表与详情里 {@code encryptedSecretJson} 恒为掩码,编辑时留空即保持原值.
 */
@RestController
@Validated
@PreAuthorize("@authPerm.isAdmin()")
@RequestMapping("/api/auth/admin/notification-credentials")
public class NotificationCredentialController {

    private final INotificationCredentialService service;

    /**
     * 构造注入.
     *
     * @param service 通知渠道凭据服务
     */
    public NotificationCredentialController(INotificationCredentialService service) {
        this.service = service;
    }

    /**
     * 列表 -- 秘密已掩码.
     *
     * @param targetSystem 归属产品: bi / agent; 省略即不收窄(管理台顶栏的「全部产品」档)
     * @param credType 渠道类型,可空
     * @return 凭据列表
     */
    @GetMapping
    public Result<List<NotificationCredential>> list(
            @RequestParam(required = false) String targetSystem,
            @RequestParam(required = false) String credType) {
        // 管理员看全部作用域,故 scopeRefs 传 null。
        return Result.ok(service.list(targetSystem, null, credType));
    }

    /**
     * 详情 -- 秘密已掩码.
     *
     * @param id 凭据 ID
     * @return 凭据
     */
    @GetMapping("/{id}")
    public Result<NotificationCredential> get(@PathVariable Long id) {
        NotificationCredential row = service.getById(id);
        return row == null ? Result.fail("渠道不存在或已删除") : Result.ok(row);
    }

    /**
     * 新建.
     *
     * @param req 请求体
     * @return 新建的凭据 ID
     */
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SaveRequest req) {
        if (NotificationChannelType.of(req.credType()).isEmpty()) {
            return Result.fail("未知的渠道类型: " + req.credType());
        }
        try {
            return Result.ok(service.save(req.toEntity(null), getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新 -- 归属、引用名、类型、作用域建后不可改,请求体里带了也会被忽略.
     *
     * @param id 凭据 ID
     * @param req 请求体
     * @return 凭据 ID
     */
    @PutMapping("/{id}")
    public Result<Long> update(@PathVariable Long id, @RequestBody SaveRequest req) {
        try {
            return Result.ok(service.save(req.toEntity(id), getCurrentUserId()));
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 删除.
     *
     * @param id 凭据 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    /**
     * 连通性测试 -- 向上游真发一条探测消息,结果落库.
     *
     * @param id 凭据 ID
     * @return 测试结果
     */
    @PostMapping("/{id}/test")
    public Result<INotificationCredentialService.TestOutcome> test(@PathVariable Long id) {
        return Result.ok(service.test(id));
    }

    /**
     * 渠道类型元数据 -- 前端据此渲染新建表单,不硬编码任何厂商字段.
     *
     * @return 类型列表
     */
    @GetMapping("/types")
    public Result<List<Map<String, Object>>> types() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NotificationChannelType t : NotificationChannelType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", t.name());
            item.put("displayName", t.displayName());
            item.put("channel", t.channel());
            item.put("fields", t.allFields());
            out.add(item);
        }
        return Result.ok(out);
    }

    /**
     * 新建 / 更新请求体.
     *
     * @param targetSystem 归属产品,新建必填
     * @param credKey 引用名,新建必填
     * @param credType 渠道类型,新建必填
     * @param displayName 显示名
     * @param description 备注
     * @param encryptedSecretJson 秘密 JSON 明文;留空或为掩码时保持原值
     * @param scopeRef 作用域引用,由目标系统解释
     * @param enabled 是否启用
     */
    public record SaveRequest(
            @NotBlank String targetSystem,
            @NotBlank String credKey,
            @NotBlank String credType,
            String displayName,
            String description,
            String encryptedSecretJson,
            String scopeRef,
            Boolean enabled) {

        NotificationCredential toEntity(Long id) {
            NotificationCredential row = new NotificationCredential();
            row.setId(id);
            row.setTargetSystem(targetSystem);
            row.setCredKey(credKey);
            row.setCredType(credType);
            row.setDisplayName(
                    displayName == null || displayName.isBlank() ? credKey : displayName.trim());
            row.setDescription(description);
            row.setEncryptedSecretJson(encryptedSecretJson);
            row.setScopeRef(scopeRef == null || scopeRef.isBlank() ? null : scopeRef.trim());
            row.setEnabled(enabled == null || enabled ? 1 : 0);
            return row;
        }
    }

    /**
     * 从 SecurityContext 获取当前用户 ID.
     *
     * @return 用户 ID, 未认证时 {@code null}
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
            if (userId instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }
}
