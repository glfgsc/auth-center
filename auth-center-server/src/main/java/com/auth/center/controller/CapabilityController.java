package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.controller.request.CapabilityRegisterRequest;
import com.auth.center.entity.SystemCapability;
import com.auth.center.service.ISystemCapabilityService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨系统能力注册控制器 -- 各子系统向认证中心注册和查询能力码.
 *
 * 能力码是权限模型的最小粒度单元，子系统启动时通过 {@code /api/auth/capabilities/register}
 * 端点批量注册自身提供的能力，认证中心统一管理和分发。数据访问与事务边界收口在 {@link ISystemCapabilityService}，本控制器仅做入参校验（白名单 + 数量上限）与
 * DTO→实体映射。
 */
@RestController
@Validated
@RequestMapping("/api/auth/capabilities")
public class CapabilityController {

    private static final Logger log = LoggerFactory.getLogger(CapabilityController.class);

    /** 允许注册能力码的子系统编码白名单。防止未授权调用方注入 admin/global 等高权限系统的能力码。 */
    private static final Set<String> ALLOWED_SYSTEM_CODES =
            Set.of(
                    "bi",
                    "agent",
                    "tracking",
                    "knowledge",
                    "platform",
                    "query",
                    "conversation",
                    "security",
                    "flow");

    /** 单次注册的最大能力码数量上限 */
    private static final int MAX_CAPABILITIES_PER_REGISTER = 500;

    private final ISystemCapabilityService systemCapabilityService;

    /**
     * 构造函数，注入系统能力服务.
     *
     * @param systemCapabilityService 系统能力服务
     */
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    public CapabilityController(ISystemCapabilityService systemCapabilityService) {
        this.systemCapabilityService = systemCapabilityService;
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

    /**
     * 批量注册子系统能力码.
     *
     * 先删除该系统编码下所有旧能力码，再批量插入新的，相当于每次注册都是全量替换。删除 + 插入的事务边界在 service 层。
     *
     * @param request 能力注册请求（systemCode + capabilities 列表）
     * @return 注册结果，包含注册的能力数量
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @Valid @RequestBody CapabilityRegisterRequest request) {
        // 本端点在 SecurityConfig 里 permitAll(子系统启动期调用,此刻无用户上下文),故用户级认证不生效。
        // 它是一次「先删后插」的全量替换,没有这道门任意匿名调用方即可清空/改写某系统的能力码目录。
        // systemCode 白名单挡不住:那是调用方自己填的值,填 "bi" 即可。口径与同仓其他服务间端点一致。
        if (!validInternalToken(internalToken)) {
            log.warn("[SECURITY] 拒绝无有效内部服务令牌的能力注册");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }
        String systemCode = request.getSystemCode();
        if (!ALLOWED_SYSTEM_CODES.contains(systemCode)) {
            log.warn("[SECURITY] 拒绝未知 systemCode 的能力注册: {}", systemCode);
            return Result.fail("systemCode 不在允许列表中");
        }

        List<CapabilityRegisterRequest.CapabilityItem> capabilities = request.getCapabilities();
        if (capabilities.size() > MAX_CAPABILITIES_PER_REGISTER) {
            return Result.fail("单次注册能力码数量不能超过 " + MAX_CAPABILITIES_PER_REGISTER);
        }

        // DTO → 实体映射（systemCode 由 service 统一回填）
        List<SystemCapability> entities = new ArrayList<>(capabilities.size());
        for (CapabilityRegisterRequest.CapabilityItem cap : capabilities) {
            SystemCapability entity = new SystemCapability();
            entity.setCapabilityCode(cap.getCode());
            entity.setCategory(cap.getCategory());
            entity.setLabel(cap.getLabel());
            entity.setDescription(cap.getDescription());
            entities.add(entity);
        }

        int count = systemCapabilityService.replaceForSystem(systemCode, entities);
        return Result.ok(Map.of("systemCode", systemCode, "registered", count));
    }

    /**
     * 查询已注册的能力码列表.
     *
     * 可通过 systemCode 参数筛选特定子系统的能力码。不传参数则返回所有已注册能力码。
     *
     * @param systemCode 子系统编码（可选），如 "bi"、"flow"
     * @return 能力码列表
     */
    @GetMapping
    public Result<List<SystemCapability>> list(@RequestParam(required = false) String systemCode) {
        return Result.ok(systemCapabilityService.list(systemCode));
    }
}
