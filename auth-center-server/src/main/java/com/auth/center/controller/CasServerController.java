package com.auth.center.controller;

import static com.auth.center.controller.CasResponseWriter.buildCasFailureXml;
import static com.auth.center.controller.CasResponseWriter.buildCasSuccessXml;
import static com.auth.center.controller.CasResponseWriter.buildJsonResponse;
import static com.auth.center.controller.CasResponseWriter.buildUserMap;
import static com.auth.center.controller.CasResponseWriter.errorMap;
import static com.auth.center.controller.CasResponseWriter.isJsonRequest;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.cas.TicketGrantingTicket;
import com.auth.center.cas.TicketRegistry;
import com.auth.center.common.Result;
import com.auth.center.controller.request.LoginRequest;
import com.auth.center.controller.request.TicketValidateRequest;
import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.entity.SsoConfig;
import com.auth.center.security.JwtService;
import com.auth.center.security.LoginRateLimiter;
import com.auth.center.service.IPermissionSetService;
import com.auth.center.service.ISsoConfigService;
import com.auth.center.service.IUserAdminService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * CAS 协议服务端控制器 -- 实现 CAS SSO 登录、验票和注销协议.
 *
 *   - {@code GET /cas/login} - 检查 TGT cookie; 浏览器访问返回 Thymeleaf 视图, API 返回 JSON
 *   - {@code POST /cas/login} - 验证凭据、创建 TGT、签发 ST、生成 JWT
 *   - {@code GET /cas/serviceValidate} - 服务端验票，返回 CAS XML
 *   - {@code GET /cas/p3/serviceValidate} - CAS 3.0 协议验票（同上）
 *   - {@code GET /cas/logout} - 销毁 TGT，清除 CASTGC cookie
 *   - {@code POST /api/auth/cas/ticket-validate} - SPA 便捷验票，返回 JSON
 *   - {@code GET /cas/external-callback} - 外部 CAS 回调,自动注册 + 签发本地票据
 *
 * 本控制器使用 {@code @Controller} 而非 {@code @RestController}：部分方法需要 302 重定向或 Thymeleaf 视图渲染能力，返回
 * JSON 的方法加 {@code @ResponseBody}。
 */
@Controller
public class CasServerController {

    private static final Logger log = LoggerFactory.getLogger(CasServerController.class);

    /** TGT Cookie 名称 */
    private static final String TGC_COOKIE_NAME = "CASTGC";

    /** TGT Cookie 路径 -- 限制在 /cas 路径下 */
    private static final String TGC_COOKIE_PATH = "/cas";

    /** Cookie 过期（立即删除）标记 */
    private static final int COOKIE_EXPIRED = 0;

    /** 外部 CAS XML 中提取用户名的正则 */
    private static final Pattern CAS_USER_PATTERN = Pattern.compile("<cas:user>([^<]+)</cas:user>");

    private final TicketRegistry ticketRegistry;
    private final JwtService jwtService;
    private final IPermissionSetService permissionSetService;
    private final com.auth.center.security.SystemPermissionResolver systemPermissionResolver;
    private final IUserAdminService userAdminService;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;
    private final ISsoConfigService ssoConfigService;
    private final com.auth.center.security.RefreshTokenService refreshTokenService;
    private final com.auth.center.service.IAuthService authService;

    /** 跳转地址的安全判定与拼装 —— 登录 / 登出 / 外部回调共用同一把尺。 */
    private final CasRedirectGuard redirectGuard;

    /** 向上游 CAS 校验票据。 */
    private final CasUpstreamValidator upstreamValidator;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param ticketRegistry 票据注册中心
     * @param jwtService JWT 签发/解析服务
     * @param permissionSetService 权限集服务
     * @param systemPermissionResolver 按系统权限解析器
     * @param userAdminService 用户管理服务
     * @param passwordEncoder 密码编码器
     * @param loginRateLimiter 登录频率限制器
     * @param ssoConfigService SSO 配置服务
     * @param refreshTokenService Refresh Token 族谱管理服务
     * @param authService 认证服务(复用其 establishSession 登记 CAS 活跃会话)
     */
    public CasServerController(
            TicketRegistry ticketRegistry,
            JwtService jwtService,
            IPermissionSetService permissionSetService,
            com.auth.center.security.SystemPermissionResolver systemPermissionResolver,
            IUserAdminService userAdminService,
            PasswordEncoder passwordEncoder,
            LoginRateLimiter loginRateLimiter,
            ISsoConfigService ssoConfigService,
            com.auth.center.security.RefreshTokenService refreshTokenService,
            com.auth.center.service.IAuthService authService,
            CasRedirectGuard redirectGuard,
            CasUpstreamValidator upstreamValidator) {
        this.authService = authService;
        this.redirectGuard = redirectGuard;
        this.upstreamValidator = upstreamValidator;
        this.ticketRegistry = ticketRegistry;
        this.jwtService = jwtService;
        this.permissionSetService = permissionSetService;
        this.systemPermissionResolver = systemPermissionResolver;
        this.userAdminService = userAdminService;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
        this.ssoConfigService = ssoConfigService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * 启动时检查 {@code auth.cas.public-base-url} 是否已配置.
     *
     * 未配置时 CAS 重定向校验将回退到 {@code request.getServerName()}，这在无反向代理
     * 的开发环境中可正常工作，但生产环境（反代后端口/协议可能不一致）建议显式配置。
     */
    /**
     * CAS 登录页 -- 根据 Accept 头决定返回 Thymeleaf 视图或 JSON.
     *
     * 当已有有效 TGT + service 参数时，签发 ST 后 302 重定向（不渲染页面）。浏览器直接访问（不含 Accept: application/json）时返回
     * Thymeleaf 模板 {@code cas-login}。API 客户端（Accept: application/json）返回 JSON。
     *
     * @param service 目标服务回调 URL（可选）
     * @param system 发起登录的产品编码（可选）—— 决定用哪一档 SSO 配置，不传则取 {@code global} 兜底档
     * @param tgcCookie CASTGC cookie 值（可选）
     * @param request HTTP 请求（用于判断 Accept 头）
     * @param model Thymeleaf Model（用于传递视图属性）
     * @return 重定向响应、Thymeleaf 视图名或 JSON 响应
     */
    @GetMapping("/cas/login")
    public Object loginPage(
            @RequestParam(required = false) String service,
            @RequestParam(required = false) String system,
            @CookieValue(name = TGC_COOKIE_NAME, required = false) String tgcCookie,
            HttpServletRequest request,
            Model model) {

        // 检查是否存在有效 TGT
        if (tgcCookie != null && !tgcCookie.isBlank()) {
            TicketGrantingTicket tgt = ticketRegistry.getTgt(tgcCookie);
            if (tgt != null && !tgt.isExpired()) {
                // 有有效 TGT，且有 service 参数 -> 签发 ST 并重定向
                if (service != null && !service.isBlank()) {
                    if (!redirectGuard.isSafeServiceUrl(service, request)) {
                        return ResponseEntity.badRequest().body(errorMap("service URL 不在允许列表中"));
                    }
                    ServiceTicket st = ticketRegistry.createSt(tgt, service);
                    String redirectUrl = redirectGuard.buildRedirectUrl(service, st.getId());
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .header("Location", redirectUrl)
                            .build();
                }
                // 有效 TGT 但无 service -> 返回已登录状态
                if (isJsonRequest(request)) {
                    return buildJsonResponse(
                            Map.of("authenticated", true, "username", tgt.getUsername()));
                }
                model.addAttribute("authenticated", true);
                model.addAttribute("username", tgt.getUsername());
            }
        }

        // 获取 SSO 配置用于渲染登录页（按发起登录的产品解析，缺省回落 global 兜底档）
        Map<String, Object> ssoPublic = ssoConfigService.getPublicConfig(system);
        String ssoMode = (String) ssoPublic.getOrDefault("mode", "disabled");
        String ssoDisplayName = (String) ssoPublic.getOrDefault("displayName", "");
        String ssoServerUrl = (String) ssoPublic.getOrDefault("serverUrl", "");

        // 强制 CAS 模式的浏览器导航：服务端直接 302 委派上游 CAS，不渲染跳板页。
        // 跳板页响应带 Spring Security 默认 X-Frame-Options: DENY——iframe 嵌入
        // (embed-sso)首跳必无本地 TGT，一旦渲染即被浏览器拒绝、静默 SSO 链断裂；
        // 302 响应不受 XFO 约束。跳转构造与 cas-login.html 的 redirectToSso 逐字段
        // 同构（含两道回环防御）；?local=1 管理员逃生通道与 JSON 客户端保持原行为；
        // 防御命中时回退渲染登录页（页面 JS 同样防御并向用户展示配置错误）。
        if ("enforced".equals(ssoMode)
                && ssoServerUrl != null
                && !ssoServerUrl.isBlank()
                && !isJsonRequest(request)
                && request.getParameter("local") == null) {
            String upstream =
                    redirectGuard.buildUpstreamSsoRedirect(
                            ssoServerUrl,
                            service,
                            redirectGuard.resolvePublicBaseUrl(request),
                            redirectGuard.requestOrigin(request),
                            system);
            if (upstream != null) {
                return ResponseEntity.status(HttpStatus.FOUND).header("Location", upstream).build();
            }
        }

        // API 客户端 -> JSON
        if (isJsonRequest(request)) {
            Map<String, Object> data = new HashMap<>();
            data.put("needLogin", true);
            data.put("service", service);
            data.put("ssoMode", ssoMode);
            data.put("ssoDisplayName", ssoDisplayName);
            data.put("ssoServerUrl", ssoServerUrl);
            return buildJsonResponse(data);
        }

        // 浏览器 -> Thymeleaf 视图
        model.addAttribute("service", service);
        model.addAttribute("ssoMode", ssoMode);
        model.addAttribute("ssoDisplayName", ssoDisplayName);
        model.addAttribute("ssoServerUrl", ssoServerUrl);
        // 注入对外基地址：登录页 redirectToSso 用它构造回调，确保与验票阶段一致
        model.addAttribute("publicBaseUrl", redirectGuard.resolvePublicBaseUrl(request));
        // 注入产品编码：跳板页 redirectToSso 要把它挂进回调，与 buildUpstreamSsoRedirect 同构
        model.addAttribute("system", system);
        return "cas-login";
    }

    /**
     * CAS 登录提交 -- 验证凭据并签发票据和令牌.
     *
     * 流程:频率限制 -> 密码验证 -> 创建 TGT -> 设置 CASTGC cookie -> 若有 service 则签发 ST -> 签发 JWT -> 返回 JSON。
     *
     * @param loginRequest 登录请求（username + password + 可选 service）
     * @param request HTTP 请求
     * @param response HTTP 响应（用于设置 cookie）
     * @return 包含 token、ticket、用户信息和重定向 URL 的 JSON
     */
    @PostMapping("/cas/login")
    @ResponseBody
    public ResponseEntity<?> loginSubmit(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();
        String service = loginRequest.getService();

        // 频率限制检查
        if (loginRateLimiter.isLocked(username)) {
            long remaining = loginRateLimiter.remainingLockSeconds(username);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorMap("账户已被锁定，请 " + remaining + " 秒后重试"));
        }

        // 按用户名查询用户
        AuthUser user = userAdminService.findByUsername(username);
        if (user == null) {
            loginRateLimiter.recordFailure(username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMap("用户名或密码错误"));
        }

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginRateLimiter.recordFailure(username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMap("用户名或密码错误"));
        }

        // 登录成功
        loginRateLimiter.recordSuccess(username);

        // 解析权限集
        PermissionSet ps = permissionSetService.resolveForUser(user.getId());

        // 创建 TGT
        TicketGrantingTicket tgt =
                ticketRegistry.createTgt(
                        user.getId(), user.getUsername(),
                        ps.getCode(), ps.getCapabilities());

        // 设置 CASTGC cookie (HttpOnly, Secure, Path=/cas)
        Cookie tgcCookie = new Cookie(TGC_COOKIE_NAME, tgt.getId());
        tgcCookie.setHttpOnly(true);
        tgcCookie.setSecure(true);
        tgcCookie.setPath(TGC_COOKIE_PATH);
        tgcCookie.setMaxAge(-1); // 会话级 cookie
        response.addCookie(tgcCookie);

        // 签发 JWT（携带按系统权限）
        String jwt =
                jwtService.generateAccessToken(
                        user.getId(),
                        user.getUsername(),
                        ps.getCode(),
                        ps.getCapabilities(),
                        systemPermissionResolver.resolve(user.getId()).getSystemPermissions());

        // 构建响应
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwt);
        data.put("user", buildUserMap(user));
        data.put("permissionSet", ps.getCode());
        data.put("capabilities", ps.getCapabilities());

        // 如果有 service URL，校验后签发 ST 并附加重定向信息
        if (service != null && !service.isBlank()) {
            if (!redirectGuard.isSafeServiceUrl(service, request)) {
                return ResponseEntity.badRequest().body(errorMap("service URL 不在允许列表中"));
            }
            ServiceTicket st = ticketRegistry.createSt(tgt, service);
            data.put("ticket", st.getId());
            data.put("redirectUrl", redirectGuard.buildRedirectUrl(service, st.getId()));
        }

        log.info("CAS 登录成功: username={}", username);
        return ResponseEntity.ok(data);
    }

    /**
     * CAS 服务验票端点 -- 验证 Service Ticket 并返回 CAS XML.
     *
     * 同时支持 CAS 2.0 ({@code /cas/serviceValidate}) 和 CAS 3.0 ({@code /cas/p3/serviceValidate})
     * 协议。验票成功时在 CAS XML attributes 中扩展返回 JWT，客户端可直接提取 token 用于后续 API 调用。
     *
     * @param ticket Service Ticket ID
     * @param service 请求方的服务 URL（需与签发时一致）
     * @return CAS XML 格式的验票响应
     */
    @GetMapping({"/cas/serviceValidate", "/cas/p3/serviceValidate"})
    @ResponseBody
    public ResponseEntity<String> serviceValidate(
            @RequestParam String ticket, @RequestParam String service) {

        ServiceTicket st = ticketRegistry.validateSt(ticket, service);
        if (st == null) {
            log.warn("CAS 验票失败: ticket={}, service={}", ticket, service);
            String failureXml = buildCasFailureXml("INVALID_TICKET", "Ticket not recognized");
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(failureXml);
        }

        // 签发 JWT 作为扩展属性返回（携带按系统权限）
        String jwt =
                jwtService.generateAccessToken(
                        st.getUserId(),
                        st.getUsername(),
                        st.getPermissionSet(),
                        st.getCapabilities(),
                        systemPermissionResolver.resolve(st.getUserId()).getSystemPermissions());

        String successXml = buildCasSuccessXml(st, jwt);
        log.info("CAS 验票成功: username={}, service={}", st.getUsername(), service);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(successXml);
    }

    /**
     * SPA 便捷验票端点 -- 验证 ST 并返回 JSON（避免前端解析 CAS XML）.
     *
     * @param request 验票请求（ticket + service）
     * @return JSON 响应，包含 token、userId、username、permissionSet、capabilities
     */
    @PostMapping("/api/auth/cas/ticket-validate")
    @ResponseBody
    public ResponseEntity<?> ticketValidate(
            @Valid @RequestBody TicketValidateRequest request, HttpServletRequest httpRequest) {
        String ticket = request.getTicket();
        String service = request.getService();

        ServiceTicket st = ticketRegistry.validateSt(ticket, service);
        if (st == null) {
            log.warn("SPA 验票失败: ticket={}, service={}", ticket, service);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorMap("无效的票据"));
        }

        // 签发 JWT（携带按系统权限）
        String jwt =
                jwtService.generateAccessToken(
                        st.getUserId(),
                        st.getUsername(),
                        st.getPermissionSet(),
                        st.getCapabilities(),
                        systemPermissionResolver.resolve(st.getUserId()).getSystemPermissions());

        // 签发 refresh token 并创建 token family
        String refreshToken = jwtService.generateRefreshToken(st.getUserId(), st.getUsername());
        String refreshJti = jwtService.parseToken(refreshToken).getId();
        String sessionId =
                refreshTokenService.createFamily(
                        refreshJti, jwtService.getRefreshTokenExpiration());

        // 登记活跃会话(带真实客户端 IP)—— 否则 CAS 单点登录用户在会话治理列表整个缺席。
        authService.establishSession(
                sessionId,
                st.getUserId(),
                st.getUsername(),
                jwt,
                null,
                upstreamValidator.clientIp(httpRequest),
                httpRequest.getHeader("User-Agent"));

        Map<String, Object> data = new HashMap<>();
        data.put("token", jwt);
        data.put("refreshToken", refreshToken);
        data.put("userId", st.getUserId());
        data.put("username", st.getUsername());
        data.put("permissionSet", st.getPermissionSet());
        data.put("capabilities", st.getCapabilities());

        log.info("SPA 验票成功: username={}, service={}", st.getUsername(), service);
        return ResponseEntity.ok(data);
    }

    /**
     * 外部 CAS 回调端点 -- 接收外部 CAS 服务器验票结果并创建本地会话.
     *
     * 流程:调外部 CAS 的 /serviceValidate 验票 → 从 CAS XML 取 username → 本地查找或自动注册用户（默认权限集 = viewer）→ 创建
     * TGT + 设置 CASTGC cookie + 签发 ST → 302 重定向到 originalService?ticket=ST-xxx.
     *
     * @param ticket 外部 CAS 服务器签发的 ticket
     * @param originalService 最终要跳转回的业务系统 URL
     * @param system 发起登录的产品编码（可选）—— 由登录阶段挂在本回调 URL 上原样带回，决定用哪一档 SSO 配置验票
     * @param response HTTP 响应（用于设置 cookie 和重定向）
     * @throws IOException 重定向失败时抛出
     */
    @GetMapping("/cas/external-callback")
    public void externalCallback(
            @RequestParam String ticket,
            @RequestParam String originalService,
            @RequestParam(required = false) String system,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {

        // 回环防御：originalService 不得指向本端 external-callback 自身。正常流程下
        // 它是业务系统回调（如 /sso-callback）；若它本身就是一个 external-callback
        // 地址，说明上游 CAS 地址被误配为指向认证中心自身，会形成
        // /cas/login ↔ /cas/external-callback 无限重定向（service 每跳多包一层 URL
        // 编码，最终撑爆请求行返回 400）。此处直接 fail fast。
        if (originalService.contains("/cas/external-callback")) {
            log.warn("外部 CAS 回调拒绝：originalService 自引用，疑似上游 CAS 地址指向认证中心自身: {}", originalService);
            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST, "originalService 指向了 CAS 回调端点自身，检测到重定向回环");
            return;
        }

        // 校验 originalService URL 安全性 -- 防开放重定向
        if (!redirectGuard.isSafeServiceUrl(originalService, request)) {
            log.warn("外部 CAS 回调拒绝：originalService 不在允许列表中: {}", originalService);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "originalService URL 不在允许列表中");
            return;
        }

        // 必须与登录阶段解析到的是同一档 —— 否则拿 A 产品的上游地址去验 B 产品签发的 ticket
        Map<String, Object> ssoPublic = ssoConfigService.getPublicConfig(system);
        String ssoServerUrl = (String) ssoPublic.get("serverUrl");

        if (ssoServerUrl == null || ssoServerUrl.isBlank()) {
            log.warn("外部 CAS 回调失败: SSO 服务器地址未配置");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "SSO 服务器地址未配置");
            return;
        }
        String normalizedServerUrl = ssoServerUrl.replaceAll("/+$", "");
        // 验票端点按管理台配的协议版本走。configJson 只在实体上，公开配置那份不带（它是登录页用的摘要，
        // 不该把内部配置细节暴露给未认证请求），故这里另取一次实体 —— 单行查询，与上面同一档。
        SsoConfig ssoEntity = ssoConfigService.resolveConfig(system);
        String validatePath =
                redirectGuard.resolveValidatePath(
                        ssoEntity == null ? null : ssoEntity.getConfigJson());

        // 重建本端回调的对外绝对地址 —— 必须与登录阶段（cas-login.html redirectToSso）
        // 提交给上游 CAS 的 service 逐字符一致，否则上游 CAS serviceValidate 会因
        // service 不匹配而验票失败。做法：对外基地址 + 本次请求 path + 浏览器回传的
        // 原始 query（剔除上游追加的 ticket，保留与登录时同样编码的 originalService）。
        String callbackUrl = redirectGuard.rebuildSelfCallbackUrl(request);
        String validateUrl =
                normalizedServerUrl
                        + validatePath
                        + "?ticket="
                        + URLEncoder.encode(ticket, StandardCharsets.UTF_8)
                        + "&service="
                        + URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8);

        // 调用外部 CAS 服务器验票(可经 SOCKS5 代理出站,见 fetchCasValidation)
        String casXml;
        try {
            casXml = upstreamValidator.fetchCasValidation(validateUrl);
        } catch (IOException e) {
            log.warn("外部 CAS 验票请求失败: ticket={}, error={}", ticket, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "外部 CAS 服务器连接失败");
            return;
        }

        // 从 CAS XML 中提取 username
        Matcher matcher = CAS_USER_PATTERN.matcher(casXml);
        if (!matcher.find()) {
            log.warn("外部 CAS 验票失败: 无法从响应中提取用户名, ticket={}", ticket);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "外部 CAS 验票失败");
            return;
        }
        String username = matcher.group(1).trim();

        // 本地查找或自动注册用户
        AuthUser user = userAdminService.findOrCreateExternalCasUser(username);

        // 解析权限集
        PermissionSet ps = permissionSetService.resolveForUser(user.getId());

        // 创建 TGT
        TicketGrantingTicket tgt =
                ticketRegistry.createTgt(
                        user.getId(), user.getUsername(),
                        ps.getCode(), ps.getCapabilities());

        // 设置 CASTGC cookie (HttpOnly, Secure)
        Cookie tgcCookie2 = new Cookie(TGC_COOKIE_NAME, tgt.getId());
        tgcCookie2.setHttpOnly(true);
        tgcCookie2.setSecure(true);
        tgcCookie2.setPath(TGC_COOKIE_PATH);
        tgcCookie2.setMaxAge(-1);
        response.addCookie(tgcCookie2);

        // 签发 ST
        ServiceTicket st = ticketRegistry.createSt(tgt, originalService);

        // 302 重定向到 originalService?ticket=ST-xxx
        String redirectUrl = redirectGuard.buildRedirectUrl(originalService, st.getId());
        log.info("外部 CAS 回调成功: username={}, redirectTo={}", username, originalService);
        response.sendRedirect(redirectUrl);
    }

    /**
     * CAS 注销 -- 销毁 TGT 并清除 CASTGC cookie.
     *
     * 带可信 {@code service} 参数时返回 302 跳转（用于单点登出链式注销：先销毁本端 TGT，再跳上游 CAS {@code /logout}
     * 注销上游会话，或跳回登录页）；无 service 或 service 不可信时返回 JSON（API 调用方）。{@code service} 仅允许指向本系统对外基地址或已配置的上游
     * CAS 地址，防开放重定向。
     *
     * @param tgcCookie CASTGC cookie 值（可选）
     * @param service 注销后重定向的目标 URL（可选，仅可信时生效）
     * @param request HTTP 请求（用于推断对外基地址做 service 可信校验）
     * @param response HTTP 响应（用于清除 cookie）
     * @return 可信 service 时 302 跳转，否则注销结果 JSON
     */
    @GetMapping("/cas/logout")
    @ResponseBody
    public ResponseEntity<?> logout(
            @CookieValue(name = TGC_COOKIE_NAME, required = false) String tgcCookie,
            @RequestParam(required = false) String service,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 销毁 TGT
        if (tgcCookie != null && !tgcCookie.isBlank()) {
            ticketRegistry.removeTgt(tgcCookie);
            log.info("CAS 注销: 已销毁 TGT {}", tgcCookie);
        }

        // 清除 CASTGC cookie —— 与写入端(setSecure/setHttpOnly)属性保持一致,
        // 否则浏览器视为不同 cookie 而清除失败;Secure 亦满足敏感 cookie 传输要求
        // (CWE-614 HTTPS 会话敏感 cookie 缺 Secure / CWE-319 明文传输)。
        Cookie clearCookie = new Cookie(TGC_COOKIE_NAME, "");
        clearCookie.setHttpOnly(true);
        clearCookie.setSecure(true);
        clearCookie.setPath(TGC_COOKIE_PATH);
        clearCookie.setMaxAge(COOKIE_EXPIRED);
        response.addCookie(clearCookie);

        // 浏览器流程：带可信 service 时 302 跳转（单点登出链式注销）
        if (service != null
                && !service.isBlank()
                && redirectGuard.isSafeLogoutRedirect(service, request)) {
            return ResponseEntity.status(HttpStatus.FOUND).header("Location", service).build();
        }

        // API 流程：返回 JSON
        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        return ResponseEntity.ok(data);
    }

    /**
     * 计算登出时浏览器应跳转的 URL -- 实现完整单点登出.
     *
     * 前端清掉本地 JWT 后调用本端点拿到一个跳转 URL，再 {@code window.location.assign} 过去。返回的 URL 始终先经本端 {@code
     * /cas/logout} 销毁 auth-center 的 TGT（CASTGC cookie）—— 否则 enforced 模式下访问首页会被本端有效 TGT
     * 立即签票登回，表现为「点了退出又回到首页」。开启 SLO（且配置了上游地址）时再链式跳转上游 CAS 的 {@code /logout} 注销上游会话。SSO 未启用时返回 {@code
     * redirect:null}，前端退回本地登出。
     *
     * @param request HTTP 请求（用于推断对外基地址）
     * @param system 发起登出的产品编码（可选）—— 决定按哪一档的 SLO 设置与上游地址跳转
     * @return {@code Result}，data 为 {@code {redirect: String|null}}
     */
    @PostMapping("/api/auth/sso/cas/logout-redirect")
    @ResponseBody
    public Result<Map<String, Object>> logoutRedirect(
            HttpServletRequest request, @RequestParam(required = false) String system) {
        Map<String, Object> data = new HashMap<>();

        SsoConfig config = ssoConfigService.resolveConfig(system);
        boolean ssoActive =
                config != null
                        && Boolean.TRUE.equals(config.getEnabled())
                        && config.getMode() != null
                        && !"disabled".equals(config.getMode());
        if (!ssoActive) {
            data.put("redirect", null);
            return Result.ok(data);
        }

        String base = redirectGuard.resolvePublicBaseUrl(request);
        String appLogin = base + "/login";

        // 始终先经本端 /cas/logout 销毁 TGT；其 service 决定销毁后的下一跳。
        String afterLocalLogout = appLogin;
        String serverUrl = config.getServerUrl();
        if (redirectGuard.isSingleLogoutEnabled(config.getConfigJson())
                && serverUrl != null
                && !serverUrl.isBlank()) {
            // SLO：本端 TGT 销毁后再跳上游 CAS /logout 注销上游会话
            afterLocalLogout =
                    serverUrl.replaceAll("/+$", "")
                            + "/logout"
                            + "?service="
                            + URLEncoder.encode(appLogin, StandardCharsets.UTF_8);
        }

        String redirect =
                base
                        + "/cas/logout"
                        + "?service="
                        + URLEncoder.encode(afterLocalLogout, StandardCharsets.UTF_8);
        data.put("redirect", redirect);
        return Result.ok(data);
    }

    /* ---------- 私有辅助方法 ---------- */

}
