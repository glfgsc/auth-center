package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthUser;
import com.auth.center.service.IUserAdminService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户目录（内部服务专用）—— 只回「id → 名字」这一层，供别的服务把用户 ID 显示成人。
 *
 * 为什么不复用 {@code /api/auth/admin/users}：那条要 {@code admin:manage_user}，而调用方（观测台的会话排查）持有的是
 * {@code admin:manage_config}。把用户管理权限发给只想把 ID 显示成名字的页面，是为了一个筛选框扩大了授权面。
 *
 * 只回三个字段：id / username / nickname。邮箱、手机、权限集都不出去 —— 调用方要的是称呼，多给一个字段就多一处
 * 可能被转手的个人信息。
 *
 * 认证同其余内部端点：{@code /api/auth} 前缀直连本服务、不经网关，网关会剥掉外部伪造的 {@code X-Internal-Service-Token}，
 * 故此处的令牌比对是唯一实质防线，且必须 fail-closed（令牌未配置时一律拒绝，而不是放行）。
 */
@RestController
@RequestMapping("/api/auth/users/internal")
public class UserDirectoryInternalController {

    /** 内部服务令牌请求头 —— 与网关剥除的同名头一致。 */
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    private final IUserAdminService userAdminService;

    /** 内部服务令牌期望值 —— 与各调用方的 {@code bi.internal-service-token} 共享。 */
    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入。
     *
     * @param userAdminService 用户管理服务（此处只用它的全量列表）
     */
    public UserDirectoryInternalController(IUserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 用户目录：id → 称呼。
     *
     * 不分页、不按关键字过滤：调用方（会话排查的用户筛选）要的是一份可本地搜索的下拉候选，一次取回比每次输入都往返更省，
     * 而用户数与会话数不是一个量级。真到需要分页的规模时，这里再加参数也不迟。
     *
     * @param token 内部服务令牌
     * @return 用户列表（id / username / nickname）；令牌不符时 403
     */
    @GetMapping("/directory")
    public Result<List<Map<String, Object>>> directory(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String token) {
        if (!validInternalToken(token)) {
            return Result.fail(403, "internal service token required");
        }
        List<AuthUser> users = userAdminService.list();
        List<Map<String, Object>> out =
                users.stream()
                        .filter(u -> u != null && u.getId() != null)
                        .map(
                                u -> {
                                    Map<String, Object> row = new LinkedHashMap<>();
                                    row.put("id", u.getId());
                                    row.put("username", u.getUsername());
                                    row.put("nickname", u.getNickname());
                                    return row;
                                })
                        .toList();
        return Result.ok(out);
    }

    /** 定长比对，避免按字符提前返回泄漏令牌前缀。 */
    private boolean validInternalToken(String provided) {
        if (provided == null || internalServiceToken == null || internalServiceToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                internalServiceToken.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
