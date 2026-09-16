package com.auth.center.security;

import java.util.List;

/**
 * 活跃会话注册表 —— 记录/列出/移除登录会话,支撑会话治理控制台.
 *
 * 会话以 refresh token 族 ID 为主键(见 {@link SessionInfo}),登录时 {@link #record} 写入,每次 access 续期时 {@link
 * #touch} 更新当前令牌,登出/吊销时 {@link #remove}。列出仅供管理台读取,频率低。两种实现:{@link
 * RedisSessionRegistryService}(多实例共享)/ 内存实现(单实例开发)。
 */
public interface ISessionRegistryService {

    /**
     * 登录时记录一条活跃会话。
     *
     * @param session 会话信息
     */
    void record(SessionInfo session);

    /**
     * access 续期时更新会话的当前令牌与活跃时间(会话不存在则忽略)。
     *
     * @param sessionId 会话 ID(familyId)
     * @param accessJti 续期后新的 access token JTI
     * @param accessExpiresAt 续期后新 access token 过期时间(epoch ms)
     * @param lastActiveAt 最近活跃时间(epoch ms)
     */
    void touch(String sessionId, String accessJti, long accessExpiresAt, long lastActiveAt);

    /**
     * 移除一条会话(登出/吊销后)。
     *
     * @param sessionId 会话 ID(familyId)
     */
    void remove(String sessionId);

    /**
     * 列出所有活跃会话。
     *
     * 会把全部会话读进内存,只用于按用户吊销这类必须遍历全集的场合;管理台列表走 {@link #listPage}。
     *
     * @return 活跃会话列表(可能为空)
     */
    List<SessionInfo> listAll();

    /**
     * 取活跃会话的一页(按最近活跃倒序),并给出该过滤条件下的命中总数。
     *
     * 与 {@link #listAll} 的区别不只是切片:实现须只取本页所需的会话,不得先读全表再切。
     *
     * @param username 可选:按用户名精确过滤(忽略大小写),空表示不筛
     * @param systemCode 可选:按产品过滤,判据是该会话在此产品有活跃记录,空表示不筛
     * @param offset 起始偏移(从 0 起)
     * @param limit 本页条数(须为正)
     * @return 本页会话与命中总数
     */
    SessionPage listPage(String username, String systemCode, int offset, int limit);

    /**
     * 列出某用户的全部活跃会话(按最近活跃倒序)。
     *
     * 并发会话限制每次登录都要问一次「这个用户还有哪些会话」,故不能走 {@link #listAll} 过滤 —— 那让登录耗时随全站在线人数线性上涨。实现须按用户维度索引直接取。
     *
     * @param userId 用户 ID
     * @return 该用户的活跃会话(可能为空)
     */
    List<SessionInfo> listByUser(Long userId);

    /**
     * 按会话 ID 取一条会话。
     *
     * @param sessionId 会话 ID(familyId)
     * @return 会话信息;不存在返回 {@code null}
     */
    SessionInfo get(String sessionId);
}
