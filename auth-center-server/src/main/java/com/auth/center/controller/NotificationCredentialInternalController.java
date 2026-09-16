package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.NotificationCredential;
import com.auth.center.notification.NotificationChannelType;
import com.auth.center.service.INotificationCredentialService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知渠道凭据的内部服务通道 -- 供洞察与知数在真要出网时解析出地址与密钥.
 *
 * 为何方法体内必须校验内部令牌(这是唯一的门): {@code k8s/20-ingress.yaml} 把 {@code /api/auth} 前缀直连
 * auth-center、不经网关,故网关对 {@code X-Internal-Service-Token} 的剥除对本端点不生效,该头可从公网携带;
 * auth-center 也没有 {@code InternalServiceAuthFilter} 之类的过滤器. 因此下面对令牌的常量时间比对(失败即 403
 * fail-closed)是其唯一实质防线,令牌强度即安全边界.
 *
 * {@code /resolve} 返回的是解密后的明文,是全系统里秘密唯一的出口 -- 比管理面还敏感,因为管理面拿到的是掩码. 建议另在 ingress 层拒绝外部访问
 * {@code /api/auth/internal/**} 作纵深防御.
 */
@RestController
@Validated
@RequestMapping("/api/auth/internal/notification-credentials")
public class NotificationCredentialInternalController {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationCredentialInternalController.class);

    /** 内部服务令牌请求头 -- 与网关剥除的头一致. */
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final INotificationCredentialService service;

    /** 内部服务令牌期望值 -- 与各产品服务共享,生产经环境变量覆盖. */
    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入.
     *
     * @param service 通知渠道凭据服务
     */
    public NotificationCredentialInternalController(INotificationCredentialService service) {
        this.service = service;
    }

    /**
     * 解析出秘密内容 -- 返回解密后的明文,调用方据此出网.
     *
     * @param internalToken 内部服务令牌
     * @param targetSystem 归属产品
     * @param credKey 引用名
     * @param expectedType 期望的渠道类型,可空
     * @return 秘密键值对;未找到 / 已停用 / 类型不匹配时返回失败
     */
    @GetMapping("/resolve")
    public ResponseEntity<Result<Map<String, Object>>> resolve(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @RequestParam String targetSystem,
            @RequestParam String credKey,
            @RequestParam(required = false) String expectedType) {
        if (!validInternalToken(internalToken)) {
            log.warn("[NotifCredInternal] resolve rejected: bad or missing internal token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail("forbidden"));
        }
        Map<String, Object> secret = service.resolve(targetSystem, credKey, expectedType);
        if (secret == null) {
            return ResponseEntity.ok(Result.fail("渠道不存在、已停用或类型不匹配"));
        }
        return ResponseEntity.ok(Result.ok(secret));
    }

    /**
     * 可选渠道列表 -- 给订阅者选渠道用,只回引用名与显示名,不含任何地址.
     *
     * @param internalToken 内部服务令牌
     * @param targetSystem 归属产品
     * @param scopeRefs 作用域引用,逗号分隔;省略表示只要该产品内全局可用的那些
     * @return 渠道选项列表
     */
    @GetMapping("/options")
    public ResponseEntity<Result<List<Map<String, Object>>>> options(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @RequestParam String targetSystem,
            @RequestParam(required = false) String scopeRefs) {
        if (!validInternalToken(internalToken)) {
            log.warn("[NotifCredInternal] options rejected: bad or missing internal token");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail("forbidden"));
        }
        List<String> refs =
                scopeRefs == null || scopeRefs.isBlank()
                        ? List.of()
                        : Arrays.stream(scopeRefs.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .toList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (NotificationCredential row : service.list(targetSystem, refs, null)) {
            if (row.getEnabled() != null && row.getEnabled() == 0) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("credKey", row.getCredKey());
            item.put("displayName", row.getDisplayName());
            item.put("credType", row.getCredType());
            item.put(
                    "channel",
                    NotificationChannelType.of(row.getCredType())
                            .map(NotificationChannelType::channel)
                            .orElse(null));
            item.put("scopeRef", row.getScopeRef());
            out.add(item);
        }
        return ResponseEntity.ok(Result.ok(out));
    }

    /**
     * 常量时间比对内部服务令牌,防时序侧信道.
     *
     * @param provided 请求头携带的令牌,可空
     * @return 与配置值逐字节相等时 {@code true}
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
