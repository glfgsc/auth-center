package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthLoginHistory;
import com.auth.center.service.ILoginHistoryService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录历史查询端点 —— 管理员登录安全控制台的「登录历史」板块。
 *
 * 仅管理员可读({@code @authPerm.isAdmin()});写入由 {@link com.auth.center.service.impl.AuthServiceImpl}
 * 登录流程完成(成功与各类失败)。异常标记由 {@link com.auth.center.security.LoginAnomalyDetector} 落库前算出。活跃在线会话见 {@link
 * SessionAdminController}。
 */
@RestController
@RequestMapping("/api/auth/admin/login-history")
@PreAuthorize("@authPerm.isAdmin()")
public class LoginHistoryController {

    private final ILoginHistoryService loginHistoryService;

    /**
     * 构造注入。
     *
     * @param loginHistoryService 登录历史服务
     */
    public LoginHistoryController(ILoginHistoryService loginHistoryService) {
        this.loginHistoryService = loginHistoryService;
    }

    /**
     * 分页查询登录历史(按登录时间倒序)。
     *
     * @param username 用户名模糊过滤(可空)
     * @param systemCode 来源产品精确过滤(可空);登录时未报来源产品的行按产品筛时不出现
     * @param status 结果精确过滤:{@code SUCCESS} / {@code FAILED}(可空)
     * @param anomalyOnly 仅看有异常标记的记录,默认 false
     * @param startTime 起始时间(可空,格式 {@code yyyy-MM-dd HH:mm:ss})
     * @param endTime 截止时间(可空,格式 {@code yyyy-MM-dd HH:mm:ss})
     * @param page 页码,默认 1
     * @param size 每页条数,默认 20
     * @return {@code {records, total, page, size}}
     */
    @GetMapping
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "false") boolean anomalyOnly,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
                    LocalDateTime endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<AuthLoginHistory> records =
                loginHistoryService.query(
                        username, systemCode, status, anomalyOnly, startTime, endTime, page, size);
        long total =
                loginHistoryService.count(
                        username, systemCode, status, anomalyOnly, startTime, endTime);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return Result.ok(data);
    }
}
