package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthUser;
import com.auth.center.entity.ConnectedApp;
import com.auth.center.entity.PermissionSet;
import com.auth.center.security.JwtService;
import com.auth.center.service.IConnectedAppService;
import com.auth.center.service.IPermissionSetService;
import com.auth.center.service.IUserAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 嵌入会话内部铸造端点 —— 服务间调用,为终端用户签发短时 embed 会话 JWT.
 *
 * 两个入口:
 *
 *   - {@code POST /session} —— 上游(bi-core-workspace)已自行验签 Direct-Trust 断言,只请求为 subject 铸票;
 *   - {@code POST /session-from-assertion} —— 上游把断言原文交给认证中心,由中心用注册公钥验签 + 铸票合一;
 *       连接应用的公钥只存认证中心, BI 不持有本地副本.
 *
 * 调用方:仅 bi-core-workspace 的嵌入端点(经 {@code auth.center.url} 直连,不走网关).
 *
 * 内部令牌校验是唯一的门:本端点可为任意 subject 铸造带身份的 JWT(冒充能力)。{@code k8s/20-ingress.yaml} 把
 * {@code /api/auth} 前缀直连 auth-center、不经网关,故网关对 {@code X-Internal-Service-Token}
 * 的剥除对本端点不生效、该头可从公网携带,auth-center 亦无 {@code InternalServiceAuthFilter} 之类过滤器。因此方法体内对该令牌的常量时间比对(失败即
 * 403 fail-closed)是其唯一实质防线,令牌强度即安全边界(见 {@code InternalTokenStartupValidator} 的启动期 fail-fast)。
 *
 * 签发的 JWT 由本服务 RSA 私钥签名(与登录 JWT 同一密钥), 网关照常经 JWKS 校验,下游据其 {@code X-User-Id}/{@code
 * X-Capabilities} 施加 RLS/CLS —— 参见 {@link JwtService#generateEmbedToken}.
 */
@RestController
@Validated
@RequestMapping("/api/auth/embed/internal")
public class EmbedInternalController {

    private static final Logger log = LoggerFactory.getLogger(EmbedInternalController.class);

    /** 内部服务令牌请求头 —— 与网关剥除的 {@code X-Internal-Service-Token} 一致. */
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Service-Token";

    /** ttl 上限(毫秒, 8 小时) —— 防止调用方请求超长有效期的 embed 会话. */
    private static final long MAX_TTL_MILLIS = 8L * 60 * 60 * 1000;

    private final JwtService jwtService;
    private final IUserAdminService userAdminService;
    private final IPermissionSetService permissionSetService;
    private final IConnectedAppService connectedAppService;

    /**
     * 内部服务令牌期望值 —— 与各 bi 服务的 {@code bi.internal-service-token} 共享. 默认值仅供本地开发(与 bi 服务默认一致), 生产经
     * {@code BI_INTERNAL_SERVICE_TOKEN} 覆盖.
     */
    @Value("${auth.internal-service-token:}")
    private String internalServiceToken;

    /**
     * 构造注入.
     *
     * @param jwtService JWT 签发服务
     * @param userAdminService 用户管理服务(按 subject 查已注册本地用户, embed 不自动建号)
     * @param permissionSetService 权限集服务(解析真实能力供 RLS/CLS)
     * @param connectedAppService 连接应用服务(用注册公钥验签 Direct-Trust 断言)
     */
    public EmbedInternalController(
            JwtService jwtService,
            IUserAdminService userAdminService,
            IPermissionSetService permissionSetService,
            IConnectedAppService connectedAppService) {
        this.jwtService = jwtService;
        this.userAdminService = userAdminService;
        this.permissionSetService = permissionSetService;
        this.connectedAppService = connectedAppService;
    }

    /**
     * 嵌入会话铸造请求体(上游已验签).
     *
     * @param subject 断言中的终端用户标识(映射为 username, 须已在 BI 注册,不自动建号)
     * @param assetType 所嵌资产类型(DASHBOARD / CHART), 写入 scope claim
     * @param assetId 所嵌资产 ID, 写入 scope claim(下游据其收窄授权面)
     * @param workspaceId 所嵌资产所属工作区 ID, 写入 scope claim
     * @param ttlMillis 会话有效期(毫秒), 可空(用服务端默认); 服务端裁剪至 {@link #MAX_TTL_MILLIS}
     */
    public record EmbedSessionRequest(
            @NotBlank @Size(max = 255) String subject,
            @NotBlank @Size(max = 50) String assetType,
            @NotNull Long assetId,
            Long workspaceId,
            Long ttlMillis) {}

    /**
     * 验签 + 铸票合一请求体(上游把断言原文交给认证中心验签).
     *
     * @param clientId 连接应用 clientId
     * @param assertion 外部应用私钥签发的短时断言(携带终端用户 sub, aud=bi-embed)
     * @param assetType 所嵌资产类型(DASHBOARD / CHART)
     * @param assetId 所嵌资产 ID
     * @param workspaceId 所嵌资产所属工作区 ID(可空)
     * @param ttlMillis 会话有效期(毫秒,可空→用服务端默认)
     */
    public record AssertionSessionRequest(
            @NotBlank @Size(max = 100) String clientId,
            @NotBlank @Size(max = 4000) String assertion,
            @NotBlank @Size(max = 50) String assetType,
            @NotNull Long assetId,
            Long workspaceId,
            Long ttlMillis) {}

    /**
     * 铸造嵌入会话 JWT(上游已验签) —— 查已注册用户 + 解析真实能力 + 签发短时 embed JWT.
     *
     * @param internalToken 内部服务令牌(请求头 {@code X-Internal-Service-Token})
     * @param req 铸造请求
     * @return {@code {embedJwt, userId, username}}; 内部令牌无效或 subject 未注册时 403
     */
    @PostMapping("/session")
    public Result<Map<String, Object>> mintSession(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @Valid @RequestBody EmbedSessionRequest req) {

        if (!validInternalToken(internalToken)) {
            log.warn("[EmbedSession] rejected — invalid or missing internal service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }

        Map<String, Object> data =
                mintForSubject(
                        req.subject(),
                        req.assetType(),
                        req.assetId(),
                        req.workspaceId(),
                        req.ttlMillis());
        if (data == null) {
            log.warn(
                    "[EmbedSession] rejected — subject '{}' not provisioned in BI (auto-signup disabled for embed)",
                    req.subject());
            return Result.fail(HttpStatus.FORBIDDEN.value(), "embed subject not provisioned in BI");
        }

        log.info(
                "[EmbedSession] minted embed JWT for subject='{}' (userId={}) → {}:{}",
                req.subject(),
                data.get("userId"),
                req.assetType(),
                req.assetId());
        return Result.ok(data);
    }

    /**
     * 验签 Direct-Trust 断言 + 铸造嵌入会话 JWT(合一) —— 认证中心作为唯一信任源.
     *
     * 用连接应用注册的公钥(RS256)验签外部应用私钥签发的短时断言,提取其证明的终端用户身份,随即为该用户铸票. 相较 {@link #mintSession},
     * 验签这一步不再需要上游(BI)持有公钥副本.
     *
     * 内容作用域(可见哪些资产)不在此裁决 —— embed JWT 随真实用户身份,由目标系统按其工作区成员与 RLS/CLS 逐查询裁决;连接应用只证明「是谁」,
     * 不裁「看什么」.
     *
     * @param internalToken 内部服务令牌(请求头 {@code X-Internal-Service-Token})
     * @param req 验签 + 铸票请求
     * @return {@code {embedJwt, userId, username, subject, allowedDomains, appId, appName}}; 内部令牌无效
     *     403 / 断言验签失败 401 / subject 未注册 403
     */
    @PostMapping("/session-from-assertion")
    public Result<Map<String, Object>> sessionFromAssertion(
            @RequestHeader(value = HEADER_INTERNAL_TOKEN, required = false) String internalToken,
            @Valid @RequestBody AssertionSessionRequest req) {

        if (!validInternalToken(internalToken)) {
            log.warn("[EmbedSession] rejected — invalid or missing internal service token");
            return Result.fail(HttpStatus.FORBIDDEN.value(), "internal service token required");
        }

        // 1) 用注册公钥验签断言(证明终端用户身份)。
        IConnectedAppService.AssertionResult ar =
                connectedAppService.verifyAssertion(req.clientId(), req.assertion());
        if (ar == null) {
            log.warn(
                    "[EmbedSession] rejected — invalid assertion for clientId '{}'",
                    req.clientId());
            return Result.fail(HttpStatus.UNAUTHORIZED.value(), "invalid assertion");
        }

        // 2) 为验明身份的 subject 铸票(只查不建,未注册 403)。
        Map<String, Object> data =
                mintForSubject(
                        ar.subject(),
                        req.assetType(),
                        req.assetId(),
                        req.workspaceId(),
                        req.ttlMillis());
        if (data == null) {
            log.warn(
                    "[EmbedSession] rejected — subject '{}' not provisioned in BI (auto-signup disabled for embed)",
                    ar.subject());
            return Result.fail(HttpStatus.FORBIDDEN.value(), "embed subject not provisioned in BI");
        }

        // 3) 回传 BI 构造交接令牌所需(应用身份 + 域名白名单)——BI 不再持有本地连接应用副本。
        ConnectedApp app = ar.app();
        data.put("subject", ar.subject());
        data.put("appId", app.getId());
        data.put("appName", app.getName());
        data.put("allowedDomains", app.getAllowedDomains());

        log.info(
                "[EmbedSession] verified assertion + minted embed JWT for app '{}' subject='{}' → {}:{}",
                app.getName(),
                ar.subject(),
                req.assetType(),
                req.assetId());
        return Result.ok(data);
    }

    /**
     * 为已验明身份的 subject 铸造 embed JWT —— 查已注册用户 + 解析真实能力 + 签发短时 JWT.
     *
     * 只查不建: Direct-Trust 场景 subject 仅是外部应用「自证」的标识(用其私钥签任意 sub 即可), 与 CAS(企业上游认证过) 不同,不能据此自动建号
     * —— 否则持签名私钥者可让 BI 无限建号,污染用户体系 / 绕开准入审批 / 耗尽席位许可 / 治理失控。故 subject 须已由管理员预先在 BI 注册,未注册返回
     * {@code null}(调用方转 403 fail-closed)。
     *
     * @param subject 终端用户标识(映射为 username)
     * @param assetType 所嵌资产类型
     * @param assetId 所嵌资产 ID
     * @param workspaceId 所嵌资产所属工作区 ID(可空)
     * @param ttlMillis 会话有效期(毫秒,可空→服务端默认); 裁剪至 {@link #MAX_TTL_MILLIS}
     * @return {@code {embedJwt, userId, username}}; subject 未注册返回 {@code null}
     */
    private Map<String, Object> mintForSubject(
            String subject, String assetType, Long assetId, Long workspaceId, Long ttlMillis) {
        AuthUser user = userAdminService.findByUsername(subject);
        if (user == null) {
            return null;
        }

        // BI 侧解析该用户真实能力(承载角色/分组,供下游 RLS/CLS 逐查询评估)。
        PermissionSet ps = permissionSetService.resolveForUser(user.getId());

        // 作用域绑定 claim —— 下游 @EmbedGuard 据其把会话收窄到所嵌资产。
        Map<String, Object> scopeClaims = new LinkedHashMap<>();
        scopeClaims.put("assetType", assetType);
        scopeClaims.put("assetId", assetId);
        if (workspaceId != null) {
            scopeClaims.put("workspaceId", workspaceId);
        }

        long ttl = ttlMillis != null ? Math.min(ttlMillis, MAX_TTL_MILLIS) : 0L;

        // 签发: permissionSet 恒 null(禁 admin 旁路), capabilities 为真实值(RLS 按真实身份)。
        String embedJwt =
                jwtService.generateEmbedToken(
                        user.getId(), user.getUsername(), ps.getCapabilities(), scopeClaims, ttl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("embedJwt", embedJwt);
        data.put("userId", user.getId());
        data.put("username", user.getUsername());
        return data;
    }

    /**
     * 常量时间比对内部服务令牌,防时序侧信道.
     *
     * @param provided 请求头携带的令牌(可空)
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
