package com.auth.center.controller;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.cas.TicketGrantingTicket;
import com.auth.center.cas.TicketRegistry;
import com.auth.center.entity.AuthUser;
import com.auth.center.entity.PermissionSet;
import com.auth.center.mapper.AuthUserMapper;
import com.auth.center.mapper.PermissionSetMapper;
import com.auth.center.security.JwtService;
import com.auth.center.security.LoginRateLimiter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

/**
 * CAS 协议服务端控制器 -- 实现 CAS SSO 登录、验票和注销协议.
 *
 * <p>核心端点:
 * <ul>
 *     <li>{@code GET /cas/login} - 检查 TGT cookie，有效则签发 ST 并重定向，否则要求登录</li>
 *     <li>{@code POST /cas/login} - 验证凭据、创建 TGT、签发 ST、生成 JWT</li>
 *     <li>{@code GET /cas/serviceValidate} - 服务端验票，返回 CAS XML</li>
 *     <li>{@code GET /cas/p3/serviceValidate} - CAS 3.0 协议验票（同上）</li>
 *     <li>{@code GET /cas/logout} - 销毁 TGT，清除 CASTGC cookie</li>
 * </ul>
 *
 * <p>注意: 本控制器使用 {@code @Controller} 而非 {@code @RestController}，
 * 部分方法需要 302 重定向能力，返回 JSON 的方法加 {@code @ResponseBody}。</p>
 */
@Controller
@RequestMapping("/cas")
public class CasServerController {

    private static final Logger log = LoggerFactory.getLogger(CasServerController.class);

    /** TGT Cookie 名称 */
    private static final String TGC_COOKIE_NAME = "CASTGC";

    /** TGT Cookie 路径 -- 限制在 /cas 路径下 */
    private static final String TGC_COOKIE_PATH = "/cas";

    /** CAS XML 命名空间前缀 */
    private static final String CAS_NS = "http://www.yale.edu/tp/cas";

    /** 默认权限集编码 */
    private static final String DEFAULT_PERMISSION_SET_CODE = "viewer";

    /** Cookie 过期（立即删除）标记 */
    private static final int COOKIE_EXPIRED = 0;

    private final TicketRegistry ticketRegistry;
    private final JwtService jwtService;
    private final PermissionSetMapper permissionSetMapper;
    private final AuthUserMapper authUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginRateLimiter loginRateLimiter;

    /**
     * 构造函数，注入所有依赖.
     *
     * @param ticketRegistry      票据注册中心
     * @param jwtService          JWT 签发/解析服务
     * @param permissionSetMapper 权限集 Mapper
     * @param authUserMapper      用户 Mapper
     * @param passwordEncoder     密码编码器
     * @param loginRateLimiter    登录频率限制器
     */
    public CasServerController(TicketRegistry ticketRegistry,
                               JwtService jwtService,
                               PermissionSetMapper permissionSetMapper,
                               AuthUserMapper authUserMapper,
                               PasswordEncoder passwordEncoder,
                               LoginRateLimiter loginRateLimiter) {
        this.ticketRegistry = ticketRegistry;
        this.jwtService = jwtService;
        this.permissionSetMapper = permissionSetMapper;
        this.authUserMapper = authUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    /**
     * CAS 登录页 -- 检查 TGT cookie 状态.
     *
     * <p>如果存在有效 TGT 且提供了 service 参数，签发 ST 后 302 重定向到 service URL。
     * 否则返回 JSON 要求前端渲染登录表单。</p>
     *
     * @param service   目标服务回调 URL（可选）
     * @param tgcCookie CASTGC cookie 值（可选）
     * @return 重定向响应或 JSON 登录提示
     */
    @GetMapping("/login")
    @ResponseBody
    public ResponseEntity<?> loginPage(
            @RequestParam(required = false) String service,
            @CookieValue(name = TGC_COOKIE_NAME, required = false) String tgcCookie) {

        // 检查是否存在有效 TGT
        if (tgcCookie != null && !tgcCookie.isBlank()) {
            TicketGrantingTicket tgt = ticketRegistry.getTgt(tgcCookie);
            if (tgt != null && !tgt.isExpired()) {
                // 有有效 TGT，且有 service 参数 -> 签发 ST 并重定向
                if (service != null && !service.isBlank()) {
                    ServiceTicket st = ticketRegistry.createSt(tgt, service);
                    String redirectUrl = buildRedirectUrl(service, st.getId());
                    return ResponseEntity.status(HttpStatus.FOUND)
                            .header("Location", redirectUrl)
                            .build();
                }
                // 有效 TGT 但无 service -> 返回已登录状态
                Map<String, Object> data = new HashMap<>();
                data.put("authenticated", true);
                data.put("username", tgt.getUsername());
                return ResponseEntity.ok(data);
            }
        }

        // 无有效 TGT -> 返回需要登录的 JSON
        Map<String, Object> data = new HashMap<>();
        data.put("needLogin", true);
        data.put("service", service);
        return ResponseEntity.ok(data);
    }

    /**
     * CAS 登录提交 -- 验证凭据并签发票据和令牌.
     *
     * <p>流程: 频率限制 -> 密码验证 -> 创建 TGT -> 设置 CASTGC cookie
     * -> 若有 service 则签发 ST -> 签发 JWT -> 返回 JSON。</p>
     *
     * @param params   请求体，需包含 username、password，可选 service
     * @param response HTTP 响应（用于设置 cookie）
     * @return 包含 token、ticket、用户信息和重定向 URL 的 JSON
     */
    @PostMapping("/login")
    @ResponseBody
    public ResponseEntity<?> loginSubmit(
            @RequestBody Map<String, String> params,
            HttpServletResponse response) {

        String username = params.get("username");
        String password = params.get("password");
        String service = params.get("service");

        // 参数校验
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(errorMap("用户名不能为空"));
        }
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body(errorMap("密码不能为空"));
        }

        // 频率限制检查
        if (loginRateLimiter.isLocked(username)) {
            long remaining = loginRateLimiter.remainingLockSeconds(username);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(errorMap("账户已被锁定，请 " + remaining + " 秒后重试"));
        }

        // 按用户名查询用户
        LambdaQueryWrapper<AuthUser> query = new LambdaQueryWrapper<>();
        query.eq(AuthUser::getUsername, username);
        AuthUser user = authUserMapper.selectOne(query);
        if (user == null) {
            loginRateLimiter.recordFailure(username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorMap("用户名或密码错误"));
        }

        // BCrypt 密码验证
        if (!passwordEncoder.matches(password, user.getPassword())) {
            loginRateLimiter.recordFailure(username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(errorMap("用户名或密码错误"));
        }

        // 登录成功
        loginRateLimiter.recordSuccess(username);

        // 解析权限集
        PermissionSet ps = resolvePermissionSet(user.getId());

        // 创建 TGT
        TicketGrantingTicket tgt = ticketRegistry.createTgt(
                user.getId(), user.getUsername(),
                ps.getCode(), ps.getCapabilities());

        // 设置 CASTGC cookie (HttpOnly, Path=/cas)
        Cookie tgcCookie = new Cookie(TGC_COOKIE_NAME, tgt.getId());
        tgcCookie.setHttpOnly(true);
        tgcCookie.setPath(TGC_COOKIE_PATH);
        tgcCookie.setMaxAge(-1); // 会话级 cookie
        response.addCookie(tgcCookie);

        // 签发 JWT
        String jwt = jwtService.generateAccessToken(
                user.getId(), user.getUsername(),
                ps.getCode(), ps.getCapabilities());

        // 构建响应
        Map<String, Object> data = new HashMap<>();
        data.put("token", jwt);
        data.put("user", buildUserMap(user));
        data.put("permissionSet", ps.getCode());
        data.put("capabilities", ps.getCapabilities());

        // 如果有 service URL，签发 ST 并附加重定向信息
        if (service != null && !service.isBlank()) {
            ServiceTicket st = ticketRegistry.createSt(tgt, service);
            data.put("ticket", st.getId());
            data.put("redirectUrl", buildRedirectUrl(service, st.getId()));
        }

        log.info("CAS 登录成功: username={}", username);
        return ResponseEntity.ok(data);
    }

    /**
     * CAS 服务验票端点 -- 验证 Service Ticket 并返回 CAS XML.
     *
     * <p>同时支持 CAS 2.0 ({@code /cas/serviceValidate}) 和
     * CAS 3.0 ({@code /cas/p3/serviceValidate}) 协议。</p>
     *
     * <p>验票成功时在 CAS XML attributes 中扩展返回 JWT，
     * 客户端可直接提取 token 用于后续 API 调用。</p>
     *
     * @param ticket  Service Ticket ID
     * @param service 请求方的服务 URL（需与签发时一致）
     * @return CAS XML 格式的验票响应
     */
    @GetMapping({"/serviceValidate", "/p3/serviceValidate"})
    @ResponseBody
    public ResponseEntity<String> serviceValidate(
            @RequestParam String ticket,
            @RequestParam String service) {

        ServiceTicket st = ticketRegistry.validateSt(ticket, service);
        if (st == null) {
            log.warn("CAS 验票失败: ticket={}, service={}", ticket, service);
            String failureXml = buildCasFailureXml("INVALID_TICKET", "Ticket not recognized");
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(failureXml);
        }

        // 签发 JWT 作为扩展属性返回
        String jwt = jwtService.generateAccessToken(
                st.getUserId(), st.getUsername(),
                st.getPermissionSet(), st.getCapabilities());

        String successXml = buildCasSuccessXml(st, jwt);
        log.info("CAS 验票成功: username={}, service={}", st.getUsername(), service);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(successXml);
    }

    /**
     * CAS 注销 -- 销毁 TGT 并清除 CASTGC cookie.
     *
     * @param tgcCookie CASTGC cookie 值（可选）
     * @param service   注销后重定向的目标 URL（可选）
     * @param response  HTTP 响应（用于清除 cookie）
     * @return 注销结果 JSON，包含可选的重定向 URL
     */
    @GetMapping("/logout")
    @ResponseBody
    public ResponseEntity<?> logout(
            @CookieValue(name = TGC_COOKIE_NAME, required = false) String tgcCookie,
            @RequestParam(required = false) String service,
            HttpServletResponse response) {

        // 销毁 TGT
        if (tgcCookie != null && !tgcCookie.isBlank()) {
            ticketRegistry.removeTgt(tgcCookie);
            log.info("CAS 注销: 已销毁 TGT {}", tgcCookie);
        }

        // 清除 CASTGC cookie
        Cookie clearCookie = new Cookie(TGC_COOKIE_NAME, "");
        clearCookie.setHttpOnly(true);
        clearCookie.setPath(TGC_COOKIE_PATH);
        clearCookie.setMaxAge(COOKIE_EXPIRED);
        response.addCookie(clearCookie);

        Map<String, Object> data = new HashMap<>();
        data.put("success", true);
        if (service != null && !service.isBlank()) {
            data.put("redirectUrl", service);
        }
        return ResponseEntity.ok(data);
    }

    /* ---------- 私有辅助方法 ---------- */

    /**
     * 构建 CAS 验票成功的 XML 响应.
     *
     * @param st  已验证的 Service Ticket
     * @param jwt 签发的 JWT 令牌
     * @return CAS XML 字符串
     */
    private String buildCasSuccessXml(ServiceTicket st, String jwt) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cas:serviceResponse xmlns:cas=\"").append(CAS_NS).append("\">\n");
        sb.append("  <cas:authenticationSuccess>\n");
        sb.append("    <cas:user>").append(escapeXml(st.getUsername())).append("</cas:user>\n");
        sb.append("    <cas:attributes>\n");
        sb.append("      <cas:userId>").append(st.getUserId()).append("</cas:userId>\n");
        sb.append("      <cas:permissionSet>").append(escapeXml(st.getPermissionSet())).append("</cas:permissionSet>\n");
        sb.append("      <cas:capabilities>").append(escapeXml(st.getCapabilities())).append("</cas:capabilities>\n");
        sb.append("      <cas:token>").append(jwt).append("</cas:token>\n");
        sb.append("    </cas:attributes>\n");
        sb.append("  </cas:authenticationSuccess>\n");
        sb.append("</cas:serviceResponse>");
        return sb.toString();
    }

    /**
     * 构建 CAS 验票失败的 XML 响应.
     *
     * @param code    错误码，如 "INVALID_TICKET"
     * @param message 错误描述
     * @return CAS XML 字符串
     */
    private String buildCasFailureXml(String code, String message) {
        StringBuilder sb = new StringBuilder();
        sb.append("<cas:serviceResponse xmlns:cas=\"").append(CAS_NS).append("\">\n");
        sb.append("  <cas:authenticationFailure code=\"").append(escapeXml(code)).append("\">\n");
        sb.append("    ").append(escapeXml(message)).append("\n");
        sb.append("  </cas:authenticationFailure>\n");
        sb.append("</cas:serviceResponse>");
        return sb.toString();
    }

    /**
     * 解析用户的权限集（同 AuthServiceImpl 逻辑）.
     *
     * @param userId 用户 ID
     * @return 权限集实体，保证非 null
     */
    private PermissionSet resolvePermissionSet(Long userId) {
        PermissionSet ps = permissionSetMapper.selectByUserId(userId);
        if (ps != null) {
            return ps;
        }
        ps = permissionSetMapper.selectByCode(DEFAULT_PERMISSION_SET_CODE);
        if (ps != null) {
            return ps;
        }
        log.warn("未找到默认权限集 {}，为用户 {} 返回空 viewer", DEFAULT_PERMISSION_SET_CODE, userId);
        PermissionSet empty = new PermissionSet();
        empty.setCode(DEFAULT_PERMISSION_SET_CODE);
        empty.setName("查看者");
        empty.setCapabilities("[]");
        return empty;
    }

    /**
     * 构建重定向 URL -- 将 ticket 参数附加到 service URL.
     *
     * @param serviceUrl 原始服务 URL
     * @param ticketId   Service Ticket ID
     * @return 带 ticket 参数的完整 URL
     */
    private String buildRedirectUrl(String serviceUrl, String ticketId) {
        String separator = serviceUrl.contains("?") ? "&" : "?";
        return serviceUrl + separator + "ticket=" + ticketId;
    }

    /**
     * 将用户实体转换为安全的 Map（排除密码）.
     *
     * @param user 用户实体
     * @return 包含 id、username、nickname、avatar 的 Map
     */
    private Map<String, Object> buildUserMap(AuthUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        return map;
    }

    /**
     * 构建错误响应 Map.
     *
     * @param message 错误消息
     * @return 包含 error 字段的 Map
     */
    private Map<String, Object> errorMap(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("error", message);
        return map;
    }

    /**
     * 转义 XML 特殊字符.
     *
     * @param input 原始字符串
     * @return 转义后的字符串，null 安全
     */
    private String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
