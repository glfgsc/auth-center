package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthGroup;
import com.auth.center.service.IGroupService;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户组只读控制器 -- 面向所有已认证用户和内部服务调用.
 *
 * 不需要管理员权限。供内容权限引擎和选人组件使用。管理类操作（CRUD / 成员管理）在 {@link GroupAdminController}。
 */
@RestController
@Validated
@RequestMapping("/api/auth/groups")
public class GroupReadController {

    private static final Logger log = LoggerFactory.getLogger(GroupReadController.class);

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final IGroupService groupService;

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造函数.
     *
     * @param groupService 用户组服务
     */
    public GroupReadController(IGroupService groupService) {
        this.groupService = groupService;
    }

    /**
     * GET /list -- 列出用户组（仅 id + name），供选择器使用.
     *
     * @param systemCode 产品编码；不传则返回全部产品的组（保持既有调用方行为不变）
     * @return 用户组摘要列表
     */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> listAll(
            @RequestParam(required = false) String systemCode) {
        List<AuthGroup> groups = groupService.listAll(systemCode);
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
     * 供 bi-core 内容权限引擎调用。路径落 {@code /internal/} 段，在 {@code SecurityConfig} 里按服务间端点 {@code
     * permitAll}，故用户级认证不生效 —— 须在端点内常量时间校验 {@code X-Internal-Service-Token} fail-closed，否则任意
     * 匿名调用方可用任意 {@code userId} 枚举全站用户的组归属（组归属是内容权限引擎 Layer 3 的授权输入，泄露它等于泄露授权拓扑）。网关剥除外部伪造头，故公网不可达。
     *
     * 调用方无需改动：bi 侧走 Feign（{@code AuthCenterGroupApi}），{@code FeignRequestInterceptor} 已对每个请求
     * 注入该头。
     *
     * @param internalToken 内部服务令牌
     * @param userId 用户 ID
     * @return 组 ID 列表；内部令牌无效 403
     */
    @GetMapping("/internal/user-groups/{userId}")
    public Result<List<Long>> getGroupIdsByUserId(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @PathVariable @Positive Long userId) {
        if (!validInternalToken(internalToken)) {
            log.warn("[GroupRead] internal user-groups rejected — invalid service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }
        return Result.ok(groupService.getGroupIdsByUserId(userId));
    }

    /**
     * 常量时间比对内部服务令牌。
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
