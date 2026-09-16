package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.PermissionSet;
import com.auth.center.security.SystemPermissionResolver;
import com.auth.center.service.IPermissionSetService;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 权限集只读控制器 —— 面向所有已认证用户，供主体选择器使用。
 *
 * 存在的理由：数据安全管理员（持 {@code security:rls} / {@code security:cls}）需要按「权限集」授予数据访问，而运行期的 ROLE 型主体键是
 * {@code PS_<权限集code>} 这类内部串，不是自然语言角色名。没有候选列表，配置面只能给自由文本框，管理员按直觉填「分析师」会保存成功但运行期永不命中 —— 同族的
 * CLS 配置面正是栽在这一点上。
 *
 * 管理类操作（CRUD / 授权码绑定）在 {@link PermissionSetController}，整类由 {@code @authPerm.isAdmin()}
 * 把守。本控制器只返回 code + name，不外露权限集包含哪些能力码 —— 那属于权限矩阵本身，不是选择器需要的信息。
 */
@RestController
@Validated
@RequestMapping("/api/auth/permission-sets")
public class PermissionSetReadController {

    private static final Logger log = LoggerFactory.getLogger(PermissionSetReadController.class);

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final IPermissionSetService permissionSetService;

    /** 按系统解析用户的全部权限集绑定 —— 一个用户可绑多个(如 admin + agent_admin)。 */
    private final SystemPermissionResolver systemPermissionResolver;

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    public PermissionSetReadController(
            IPermissionSetService permissionSetService,
            SystemPermissionResolver systemPermissionResolver) {
        this.permissionSetService = permissionSetService;
        this.systemPermissionResolver = systemPermissionResolver;
    }

    /**
     * GET /list —— 列出全部权限集（仅 code + name），供主体选择器的 ROLE 分支使用。
     *
     * @return 权限集摘要列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listAll() {
        List<PermissionSet> sets = permissionSetService.list();
        List<Map<String, Object>> result = new ArrayList<>(sets.size());
        for (PermissionSet ps : sets) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", ps.getCode());
            m.put("name", ps.getName());
            result.add(m);
        }
        return Result.ok(result);
    }

    /**
     * GET /internal/user-permission-set/{userId} —— 内部调用：按用户 ID 权威解析其权限集。
     *
     * 数据访问授权的裁决在 bi-core，而裁决输入里的「这个人是不是管理员」若取自调用方在请求体里自报的 authorities，凡持内部令牌者自报一个 {@code
     * PS_admin} 即可让裁决全放行。身份判据必须由签发身份的服务回答，故在此开一个只读端点。
     *
     * 路径落 {@code /internal/} 段，在 {@code SecurityConfig} 里按服务间端点 {@code permitAll}，故须在端点内常量时间校验
     * {@code X-Internal-Service-Token} fail-closed —— 否则任意匿名调用方可枚举全站用户的权限集。网关剥除外部伪造头，故公网不可达。
     *
     * @param internalToken 内部服务令牌
     * @param userId 用户 ID
     * @return {@code {code, name}}；用户无显式权限集时按 {@code resolveForUser} 回落默认集；内部令牌无效 403
     */
    @GetMapping("/internal/user-permission-set/{userId}")
    public Result<Map<String, Object>> getUserPermissionSet(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @PathVariable @Positive Long userId) {
        if (!validInternalToken(internalToken)) {
            log.warn("[PermissionSetRead] internal user-permission-set rejected — invalid token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }
        // 必须回全部绑定:一个用户可同时绑多个权限集,只回一条会让「取到哪条」取决于表顺序 ——
        // 站点管理员会因此被判成非管理员。
        Set<String> codes = new LinkedHashSet<>();
        for (Object entry :
                systemPermissionResolver.resolve(userId).getSystemPermissions().values()) {
            if (entry instanceof Map<?, ?> m && m.get("ps") != null) {
                codes.add(String.valueOf(m.get("ps")));
            }
        }
        PermissionSet primary = permissionSetService.resolveForUser(userId);
        if (primary != null && primary.getCode() != null) {
            codes.add(primary.getCode());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("codes", codes);
        return Result.ok(out);
    }

    /**
     * 常量时间比对内部服务令牌 —— 与 {@code GroupReadController} 同口径。
     *
     * @param provided 请求头携带的令牌(可空)
     * @return 相等返回 true
     */
    private boolean validInternalToken(String provided) {
        if (provided == null || internalServiceToken == null || internalServiceToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
