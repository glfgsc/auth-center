package com.auth.center.service.impl;

import com.auth.center.entity.AuthLoginHistory;
import com.auth.center.mapper.AuthLoginHistoryMapper;
import com.auth.center.service.ILoginHistoryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/** {@link ILoginHistoryService} 默认实现。 */
@Service
public class LoginHistoryServiceImpl implements ILoginHistoryService {

    /** 每页条数上限 —— 防超大分页拉垮查询。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final AuthLoginHistoryMapper loginHistoryMapper;

    /**
     * 构造注入。
     *
     * @param loginHistoryMapper 登录历史 Mapper
     */
    public LoginHistoryServiceImpl(AuthLoginHistoryMapper loginHistoryMapper) {
        this.loginHistoryMapper = loginHistoryMapper;
    }

    @Override
    public void record(AuthLoginHistory history) {
        loginHistoryMapper.insert(history);
    }

    @Override
    public List<AuthLoginHistory> query(
            String username,
            String systemCode,
            String status,
            boolean anomalyOnly,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * safeSize;

        LambdaQueryWrapper<AuthLoginHistory> wrapper =
                buildWrapper(username, systemCode, status, anomalyOnly, start, end)
                        .orderByDesc(AuthLoginHistory::getLoginTime)
                        .last("LIMIT " + offset + ", " + safeSize);
        return loginHistoryMapper.selectList(wrapper);
    }

    @Override
    public long count(
            String username,
            String systemCode,
            String status,
            boolean anomalyOnly,
            LocalDateTime start,
            LocalDateTime end) {
        return loginHistoryMapper.selectCount(
                buildWrapper(username, systemCode, status, anomalyOnly, start, end));
    }

    /**
     * 构造过滤条件(query 与 count 共用,保证一致)。
     *
     * @param username 用户名模糊过滤(可空)
     * @param systemCode 来源产品精确过滤(可空)
     * @param status 结果精确过滤(可空)
     * @param anomalyOnly 仅看有异常标记的记录
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @return 组装好的查询包装器
     */
    private LambdaQueryWrapper<AuthLoginHistory> buildWrapper(
            String username,
            String systemCode,
            String status,
            boolean anomalyOnly,
            LocalDateTime start,
            LocalDateTime end) {
        LambdaQueryWrapper<AuthLoginHistory> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isBlank()) {
            wrapper.like(AuthLoginHistory::getUsername, username.trim());
        }
        // 精确匹配:未报来源产品的行(system_code IS NULL)不归入任何产品，按产品筛时不出现。
        if (systemCode != null && !systemCode.isBlank()) {
            wrapper.eq(AuthLoginHistory::getSystemCode, systemCode.trim());
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(AuthLoginHistory::getStatus, status.trim());
        }
        if (anomalyOnly) {
            wrapper.isNotNull(AuthLoginHistory::getAnomalies)
                    .ne(AuthLoginHistory::getAnomalies, "");
        }
        if (start != null) {
            wrapper.ge(AuthLoginHistory::getLoginTime, start);
        }
        if (end != null) {
            wrapper.le(AuthLoginHistory::getLoginTime, end);
        }
        return wrapper;
    }
}
