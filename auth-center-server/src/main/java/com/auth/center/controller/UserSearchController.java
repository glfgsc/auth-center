package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.service.IUserAdminService;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户搜索控制器 -- 轻量级用户目录查询，供 @mention 选人组件等场景使用.
 *
 * 仅返回公开安全字段（id / username / nickname / avatar / email），跳过密码、手机号等敏感字段。查询逻辑（模式优先级、数量上限、安全字段投影）收口在
 * {@link IUserAdminService#search}，本控制器不直连 Mapper。
 *
 * auth-center 是用户数据的唯一权威源。
 */
@RestController
@Validated
@RequestMapping("/api/auth/users")
public class UserSearchController {

    private final IUserAdminService userAdminService;

    /**
     * 构造函数.
     *
     * @param userAdminService 用户管理服务
     */
    public UserSearchController(IUserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    /**
     * 用户搜索 -- 支持前缀模糊搜索、按 ID 批量解析、按用户名批量解析三种模式.
     *
     * 每次请求只执行一种模式，优先级：ids &gt; usernames &gt; q。混用会让调用方通过填充 ids 列表绕过数量上限。
     *
     * @param q 模糊搜索关键词（按 username / nickname 前缀匹配）
     * @param ids 逗号分隔的用户 ID 列表（精确查询）
     * @param usernames 逗号分隔的用户名列表（精确查询）
     * @param limit 返回上限（默认 10，最大 50）
     * @return 用户公开信息列表
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String ids,
            @RequestParam(required = false) String usernames,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return Result.ok(userAdminService.search(q, ids, usernames, limit));
    }
}
