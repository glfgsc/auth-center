package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.service.IPlatformConfigService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台配置内部读端点 —— 供各子系统(bi / agent / tracking)取自己系统的中心配置键值。
 *
 * 服务间调用(经 {@code auth.center.url} 直连,不走网关),端点内常量时间校验 {@code X-Internal-Service-Token}
 * fail-closed。注意:{@code /api/auth} 前缀经 {@code k8s/20-ingress.yaml} 直连 auth-center、不经网关,故网关剥头
 * 对本端点不生效 —— 本端点的令牌自校验是唯一的门,令牌强度即安全边界(见 {@code InternalTokenStartupValidator})。子系统按需轮询消费。
 */
@RestController
@RequestMapping("/api/auth/config/internal")
public class PlatformConfigInternalController {

    private static final Logger log =
            LoggerFactory.getLogger(PlatformConfigInternalController.class);

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final IPlatformConfigService configService;

    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入。
     *
     * @param configService 平台配置服务
     */
    public PlatformConfigInternalController(IPlatformConfigService configService) {
        this.configService = configService;
    }

    /**
     * 取某系统的配置键值映射。
     *
     * @param internalToken 内部服务令牌
     * @param systemCode 系统 code
     * @return {@code {key: value}};内部令牌无效 403
     */
    @GetMapping("/values")
    public Result<Map<String, String>> values(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @RequestParam String systemCode) {
        if (!validInternalToken(internalToken)) {
            log.warn("[PlatformConfig] internal read rejected — invalid service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }
        return Result.ok(configService.getValues(systemCode));
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
