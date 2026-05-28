package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.entity.AuthUser;
import com.auth.center.mapper.AuthUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户搜索控制器 -- 轻量级用户目录查询，供 @mention 选人组件等场景使用.
 *
 * <p>仅返回公开安全字段（id / username / nickname / avatar / email），
 * 跳过密码、手机号等敏感字段。支持三种搜索模式：
 * <ul>
 *     <li>前缀模糊搜索（{@code q} 参数）</li>
 *     <li>按 ID 批量解析（{@code ids} 参数）</li>
 *     <li>按用户名批量解析（{@code usernames} 参数）</li>
 * </ul>
 *
 * <p>从 bi-service-security 的 UserDirectoryController 迁移而来，
 * auth-center 是用户数据的唯一权威源。</p>
 */
@RestController
@RequestMapping("/api/auth/users")
public class UserSearchController {

    /** 单次查询返回上限，防止调用方拉取全表。 */
    private static final int MAX_LIMIT = 50;

    private final AuthUserMapper authUserMapper;

    /**
     * 构造函数.
     *
     * @param authUserMapper 用户 Mapper
     */
    public UserSearchController(AuthUserMapper authUserMapper) {
        this.authUserMapper = authUserMapper;
    }

    /**
     * 用户搜索 -- 支持前缀模糊搜索、按 ID 批量解析、按用户名批量解析三种模式.
     *
     * <p>每次请求只执行一种模式，优先级：ids &gt; usernames &gt; q。
     * 混用会让调用方通过填充 ids 列表绕过数量上限。</p>
     *
     * @param q         模糊搜索关键词（按 username / nickname 前缀匹配）
     * @param ids       逗号分隔的用户 ID 列表（精确查询）
     * @param usernames 逗号分隔的用户名列表（精确查询）
     * @param limit     返回上限（默认 10，最大 50）
     * @return 用户公开信息列表
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(
            @RequestParam(required = false, defaultValue = "") String q,
            @RequestParam(required = false) String ids,
            @RequestParam(required = false) String usernames,
            @RequestParam(required = false, defaultValue = "10") Integer limit
    ) {
        int cap = Math.max(1, Math.min(limit == null ? 10 : limit, MAX_LIMIT));
        LambdaQueryWrapper<AuthUser> w = new LambdaQueryWrapper<AuthUser>().last("LIMIT " + cap);

        if (ids != null && !ids.isBlank()) {
            List<Long> idList = parseLongList(ids);
            if (idList.isEmpty()) {
                return Result.ok(new ArrayList<>());
            }
            w.in(AuthUser::getId, idList);
        } else if (usernames != null && !usernames.isBlank()) {
            List<String> nameList = parseStringList(usernames);
            if (nameList.isEmpty()) {
                return Result.ok(new ArrayList<>());
            }
            w.in(AuthUser::getUsername, nameList);
        } else {
            w.orderByDesc(AuthUser::getCreateTime);
            if (q != null && !q.isBlank()) {
                String trimmed = q.trim();
                w.and(x -> x.likeRight(AuthUser::getUsername, trimmed)
                        .or().likeRight(AuthUser::getNickname, trimmed));
            }
        }

        List<AuthUser> rows = authUserMapper.selectList(w);
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (AuthUser u : rows) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", u.getId());
            v.put("username", u.getUsername());
            v.put("nickname", u.getNickname());
            v.put("avatar", u.getAvatar());
            v.put("email", u.getEmail());
            out.add(v);
        }
        return Result.ok(out);
    }

    /**
     * 将逗号分隔的 ID 字符串解析为 Long 列表（跳过空值和非数字项）.
     *
     * @param csv 逗号分隔的 ID 字符串
     * @return Long 列表
     */
    private static List<Long> parseLongList(String csv) {
        List<Long> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            try {
                out.add(Long.parseLong(t));
            } catch (NumberFormatException e) {
                // 跳过非数字 token — 对单个错误值宽容降级
            }
        }
        return out;
    }

    /**
     * 将逗号分隔的字符串解析为列表（跳过空值）.
     *
     * @param csv 逗号分隔的字符串
     * @return 字符串列表
     */
    private static List<String> parseStringList(String csv) {
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }
}
