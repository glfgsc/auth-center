package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.security.ISessionActivityService;
import com.auth.center.security.ISessionRegistryService;
import com.auth.center.security.SessionInfo;
import com.auth.center.security.SessionPage;
import com.auth.center.security.SessionRevokeReasons;
import com.auth.center.security.SessionRevoker;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话治理管理端点 —— 列出 / 吊销活跃会话.
 *
 * 会话在登录时登记(见 {@code AuthServiceImpl}),以 refresh 族 ID 为主键、跨 access 续期稳定。吊销动作本身收在 {@link
 * SessionRevoker} —— 与登录时按并发上限顶下线是同一件事,各写一份就会漏掉其中某一步。仅管理员可访问;仅追踪部署后新登录的会话。
 */
@RestController
@RequestMapping("/api/auth/admin/sessions")
@PreAuthorize("@authPerm.isAdmin()")
public class SessionAdminController {

    private static final Logger log = LoggerFactory.getLogger(SessionAdminController.class);

    /** 每页条数上限 —— 与登录历史同口径,挡住 size=99999 把分页绕过去。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final ISessionRegistryService sessionRegistry;
    private final ISessionActivityService sessionActivityService;
    private final SessionRevoker sessionRevoker;

    /**
     * 构造注入。
     *
     * @param sessionRegistry 活跃会话注册表
     * @param sessionActivityService 会话跨系统活跃度服务
     * @param sessionRevoker 会话吊销动作
     */
    public SessionAdminController(
            ISessionRegistryService sessionRegistry,
            ISessionActivityService sessionActivityService,
            SessionRevoker sessionRevoker) {
        this.sessionRegistry = sessionRegistry;
        this.sessionActivityService = sessionActivityService;
        this.sessionRevoker = sessionRevoker;
    }

    /**
     * 列出所有活跃会话(按最近活跃倒序),并合并各系统跨系统活跃度。
     *
     * @param username 可选:按用户名精确过滤(忽略大小写)
     * @param systemCode 可选:按产品过滤,判据是该会话的令牌在此产品活跃过(即 {@code systemActivity} 含该产品码), 与列表「活跃系统」列同源
     * @param page 页码,默认 1
     * @param size 每页条数,默认 20
     * @return {@code {records, total, page, size}}
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String systemCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        // 过滤与排序都在注册表侧按索引完成,这里拿到的已经只是本页 —— 故活跃度也只对本页合并,
        // 不再为了筛选而把全部会话的活跃度读一遍。
        SessionPage result =
                sessionRegistry.listPage(username, systemCode, (safePage - 1) * safeSize, safeSize);
        for (SessionInfo s : result.records()) {
            s.setSystemActivity(sessionActivityService.read(s.getSessionId()));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", result.records());
        data.put("total", result.total());
        data.put("page", safePage);
        data.put("size", safeSize);
        return Result.ok(data);
    }

    /**
     * 吊销一条会话(幂等)。
     *
     * @param sessionId 会话 ID(refresh 族 ID)
     * @return 成功
     */
    @DeleteMapping("/{sessionId}")
    public Result<Void> revoke(@PathVariable String sessionId) {
        SessionInfo session = sessionRegistry.get(sessionId);
        sessionRevoker.revoke(session, sessionId, SessionRevokeReasons.ADMIN_REVOKED);
        log.info(
                "[Session] 管理员吊销会话: sessionId={}, user={}",
                sessionId,
                session != null ? session.getUsername() : "?");
        return Result.ok();
    }

    /**
     * 踢下线:吊销某用户的全部活跃会话。
     *
     * @param userId 用户 ID
     * @return 成功
     */
    @DeleteMapping("/user/{userId}")
    public Result<Void> revokeUser(@PathVariable Long userId) {
        List<SessionInfo> userSessions = sessionRegistry.listByUser(userId);
        for (SessionInfo s : userSessions) {
            sessionRevoker.revoke(s, s.getSessionId(), SessionRevokeReasons.ADMIN_REVOKED);
        }
        log.info("[Session] 管理员吊销用户全部会话: userId={}, count={}", userId, userSessions.size());
        return Result.ok();
    }
}
