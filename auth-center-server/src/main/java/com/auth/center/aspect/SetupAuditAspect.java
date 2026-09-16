package com.auth.center.aspect;

import com.auth.center.common.Result;
import com.auth.center.entity.AuditLog;
import com.auth.center.service.IAuditLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证中心自身审计切面 —— 自动记录中心内所有管理配置变更,写入统一活动审计({@code source=auth_center})。
 *
 * 拦截 controller 层,仅对管理配置面的写操作(用户 / 权限集 / 用户组 / 连接应用的 {@code /api/auth/admin/**},以及 SSO 的
 * {@code /api/auth/sso/admin/**})落审计,回答「谁、何时、对哪个模块的哪个目标、执行了什么、成功与否」。切面统一从 {@link
 * SecurityContextHolder} 取操作人,免去在各端点逐个埋点。BI 的活动审计经 ingest 端点汇入同一张表;AI 信任遥测是不同域。
 *
 * 不审计:GET 读取、服务自注册({@code /api/auth/capabilities/register})、非写探测({@code .../cas/test})、登录 / 刷新 /
 * CAS 会话等。写入 best-effort —— 失败只告警,绝不影响主流程。
 */
@Aspect
@Component
public class SetupAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(SetupAuditAspect.class);

    /** 来源系统标记 —— 认证中心自身。 */
    private static final String SOURCE_AUTH_CENTER = "auth_center";

    /** 成功状态码 —— 与 {@code Result.SUCCESS_CODE} 一致。 */
    private static final int STATUS_SUCCESS = 200;

    /** params 载荷最大字符数 —— 超出截断。 */
    private static final int DETAIL_MAX_CHARS = 20_000;

    /** 脱敏:请求体 JSON 里的敏感字段值替换为 ***(密码 / 密钥 / 令牌)。 */
    private static final Pattern SENSITIVE_JSON =
            Pattern.compile(
                    "\"(password|clientSecret|secret|token|assertion)\"\\s*:\\s*\"[^\"]*\"",
                    Pattern.CASE_INSENSITIVE);

    /** 路径前缀 → 模块。 */
    private static final String[][] MODULE_BY_PATH = {
        {"/api/auth/admin/users", "user"},
        {"/api/auth/admin/permission-sets", "permission_set"},
        {"/api/auth/admin/groups", "group"},
        {"/api/auth/admin/connected-apps", "connected_app"},
        {"/api/auth/sso", "sso"},
    };

    private final IAuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param auditLogService 统一活动审计服务
     * @param objectMapper JSON 序列化器(捕获请求参数)
     */
    public SetupAuditAspect(IAuditLogService auditLogService, ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知 —— 对管理配置写操作落审计。
     *
     * @param joinPoint 连接点
     * @return 原方法返回值
     * @throws Throwable 原方法抛出的异常(原样上抛,不吞)
     */
    @Around("execution(* com.auth.center.controller..*.*(..))")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null || !isAuditable(request)) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        AuditLog audit = new AuditLog();
        audit.setSourceSystem(SOURCE_AUTH_CENTER);
        audit.setCreatedAt(LocalDateTime.now());
        audit.setMethod(request.getMethod());
        audit.setPath(request.getRequestURI());
        audit.setIp(clientIp(request));
        audit.setUserAgent(cap(request.getHeader("User-Agent"), 512));
        audit.setActorUserId(currentUserId());
        audit.setActorUsername(currentUsername());
        audit.setModule(classifyModule(request.getRequestURI()));
        audit.setOperationType(
                classifyAction(joinPoint.getSignature().getName(), request.getMethod()));
        audit.setTargetId(resolveTargetId(joinPoint));
        audit.setParams(captureParams(joinPoint));

        try {
            Object result = joinPoint.proceed();
            audit.setStatus(resolveStatus(result));
            return result;
        } catch (Throwable e) {
            audit.setStatus(500);
            audit.setErrorMsg(cap(e.getMessage(), 1024));
            throw e;
        } finally {
            audit.setDurationMs(System.currentTimeMillis() - start);
            try {
                auditLogService.record(audit);
            } catch (Exception ex) {
                log.error(
                        "[SetupAudit] failed to persist audit (module={}, op={}, actor={}): {}",
                        audit.getModule(),
                        audit.getOperationType(),
                        audit.getActorUsername(),
                        ex.getMessage(),
                        ex);
            }
        }
    }

    // ── 审计面判定 ─────────────────────────────────────────────────────

    /**
     * 判断请求是否属于需审计的管理配置写操作。
     *
     * @param request 当前请求
     * @return 是则记审计
     */
    private boolean isAuditable(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equals(method)
                || "PUT".equals(method)
                || "DELETE".equals(method)
                || "PATCH".equals(method))) {
            return false;
        }
        String uri = request.getRequestURI();
        if (uri == null) {
            return false;
        }
        if (uri.endsWith("/cas/test")) {
            return false;
        }
        return uri.startsWith("/api/auth/admin/") || uri.startsWith("/api/auth/sso/admin/");
    }

    // ── 分类 ───────────────────────────────────────────────────────────

    private String classifyModule(String uri) {
        for (String[] pair : MODULE_BY_PATH) {
            if (uri.startsWith(pair[0])) {
                return pair[1];
            }
        }
        return "other";
    }

    /**
     * 推断操作类型 —— 特定方法名优先,其余按 HTTP 方法兜底。
     *
     * @param methodName controller 方法名
     * @param httpMethod HTTP 方法
     * @return 操作类型代号
     */
    private String classifyAction(String methodName, String httpMethod) {
        String m = methodName.toLowerCase();
        if (m.contains("assign") && (m.contains("remove") || m.contains("delete"))) {
            return "unassign";
        }
        if (m.contains("assign")) {
            return "assign";
        }
        if (m.contains("addmember")) {
            return "add_member";
        }
        if (m.contains("removemember")) {
            return "remove_member";
        }
        if (m.contains("generatesecret")) {
            return "generate_secret";
        }
        if (m.contains("revokesecret")) {
            return "revoke_secret";
        }
        if (m.contains("cas") || m.contains("config")) {
            return "DELETE".equalsIgnoreCase(httpMethod) ? "delete" : "update";
        }
        return switch (httpMethod.toUpperCase()) {
            case "DELETE" -> "delete";
            case "PUT", "PATCH" -> "update";
            default -> m.startsWith("update") ? "update" : "create";
        };
    }

    // ── 取值助手 ───────────────────────────────────────────────────────

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 当前操作人 userId —— 取自 {@code JwtAuthenticationFilter} 注入 details 的 userId。
     *
     * @return userId,取不到返回 null
     */
    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        if (auth.getDetails() instanceof Map<?, ?> details) {
            Object userId = details.get("userId");
            if (userId instanceof Number n) {
                return n.longValue();
            }
        }
        return null;
    }

    /**
     * 当前操作人用户名 —— principal 即 username。
     *
     * @return 用户名,未认证返回 null
     */
    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return null;
        }
        return auth.getName();
    }

    /**
     * 从方法入参解析目标 id —— 优先名为 {@code id} 的入参,其次名以 {@code Id} 结尾者。
     *
     * @param joinPoint 连接点
     * @return 目标 id 字符串,解析不到返回 null
     */
    private String resolveTargetId(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] names = sig.getParameterNames();
            if (args == null || names == null) {
                return null;
            }
            for (int i = 0; i < args.length && i < names.length; i++) {
                if ("id".equals(names[i]) && isScalar(args[i])) {
                    return String.valueOf(args[i]);
                }
            }
            for (int i = 0; i < args.length && i < names.length; i++) {
                if (names[i] != null
                        && names[i].toLowerCase().endsWith("id")
                        && isScalar(args[i])) {
                    return String.valueOf(args[i]);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isScalar(Object o) {
        return o instanceof Number || o instanceof String;
    }

    /**
     * 序列化非 servlet 入参为 JSON 并脱敏 —— 捕获"改了什么"。
     *
     * @param joinPoint 连接点
     * @return 脱敏后的参数 JSON(截断至上限),无可记录返回 null
     */
    private String captureParams(ProceedingJoinPoint joinPoint) {
        try {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return null;
            }
            MethodSignature sig = (MethodSignature) joinPoint.getSignature();
            String[] names = sig.getParameterNames();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg == null || isServletType(arg)) {
                    continue;
                }
                String name = names != null && i < names.length ? names[i] : "arg" + i;
                sb.append(name)
                        .append('=')
                        .append(objectMapper.writeValueAsString(arg))
                        .append(';');
                if (sb.length() > DETAIL_MAX_CHARS) {
                    break;
                }
            }
            if (sb.length() == 0) {
                return null;
            }
            String redacted = SENSITIVE_JSON.matcher(sb).replaceAll("\"$1\":\"***\"");
            return cap(redacted, DETAIL_MAX_CHARS);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isServletType(Object arg) {
        return arg instanceof jakarta.servlet.ServletRequest
                || arg instanceof jakarta.servlet.ServletResponse
                || arg instanceof org.springframework.web.multipart.MultipartFile;
    }

    /**
     * 从返回值解析状态码 —— {@code Result} 取其 code,其余默认 200。
     *
     * @param result 方法返回值
     * @return 状态码
     */
    private int resolveStatus(Object result) {
        if (result instanceof Result<?> r) {
            return r.getCode();
        }
        return STATUS_SUCCESS;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return cap((comma > 0 ? forwarded.substring(0, comma) : forwarded).trim(), 64);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return cap(realIp.trim(), 64);
        }
        return cap(request.getRemoteAddr(), 64);
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
