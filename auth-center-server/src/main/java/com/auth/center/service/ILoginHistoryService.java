package com.auth.center.service;

import com.auth.center.entity.AuthLoginHistory;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录历史服务 —— 记录与查询每一次登录尝试(成功 + 失败),支撑登录安全监控.
 *
 * 写入来源:{@link com.auth.center.service.impl.AuthServiceImpl} 登录流程(成功与各类失败)。查询供管理员登录安全控制台按用户名 /
 * 结果 / 是否异常 / 时间分页检索。异常标记由 {@link com.auth.center.security.LoginAnomalyDetector} 在落库前算出。会话在线状态见
 * {@link com.auth.center.security.ISessionRegistryService}(Redis 活跃会话),与本表互补。
 */
public interface ILoginHistoryService {

    /**
     * 记录一条登录历史。
     *
     * @param history 登录历史记录
     */
    void record(AuthLoginHistory history);

    /**
     * 分页查询(按登录时间倒序)。
     *
     * @param username 用户名模糊过滤(可空)
     * @param systemCode 来源产品精确过滤(可空);未报来源产品的行不归入任何产品，按产品筛时不出现
     * @param status 结果精确过滤:{@code SUCCESS} / {@code FAILED}(可空)
     * @param anomalyOnly 仅看有异常标记的记录
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @param page 页码(从 1 起)
     * @param size 每页条数
     * @return 当前页记录
     */
    List<AuthLoginHistory> query(
            String username,
            String systemCode,
            String status,
            boolean anomalyOnly,
            LocalDateTime start,
            LocalDateTime end,
            int page,
            int size);

    /**
     * 统计符合过滤条件的总条数。
     *
     * @param username 用户名模糊过滤(可空)
     * @param systemCode 来源产品精确过滤(可空)
     * @param status 结果精确过滤(可空)
     * @param anomalyOnly 仅看有异常标记的记录
     * @param start 起始时间(可空)
     * @param end 截止时间(可空)
     * @return 总条数
     */
    long count(
            String username,
            String systemCode,
            String status,
            boolean anomalyOnly,
            LocalDateTime start,
            LocalDateTime end);
}
