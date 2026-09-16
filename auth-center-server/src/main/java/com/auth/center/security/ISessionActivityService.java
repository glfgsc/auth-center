package com.auth.center.security;

import java.util.Map;

/**
 * 会话跨系统活跃度服务 —— 记录/读取「某会话的令牌在各业务系统的最近使用时间」.
 *
 * 与 {@link ISessionRegistryService}(记「谁在线」)互补,本服务记「这次登录的令牌正在哪些系统活跃」。下游系统(洞察 / 知数 / 循迹)在 JWT
 * 校验通过后按会话防抖上报,中心据此在会话列表展示跨系统活跃。单会话多系统并发上报,故按「系统码」逐字段更新(避免整对象读改写互相覆盖)。两种实现: {@link
 * RedisSessionActivityService}(多实例共享)/ 内存实现(单实例开发)。
 */
public interface ISessionActivityService {

    /**
     * 记录某会话在某系统的最近使用时间(逐字段更新,幂等)。
     *
     * @param sessionId 会话 ID(= refresh 族 ID,JWT 的 sid claim)
     * @param system 系统码(如 bi / agent / tracking / auth_center)
     * @param lastSeenMs 最近使用时间(epoch ms)
     */
    void touch(String sessionId, String system, long lastSeenMs);

    /**
     * 读取某会话的各系统最近使用时间。
     *
     * @param sessionId 会话 ID
     * @return 系统码 → 最近使用时间(epoch ms);无则空 Map
     */
    Map<String, Long> read(String sessionId);

    /**
     * 移除某会话的活跃度记录(会话吊销/登出时清理)。
     *
     * @param sessionId 会话 ID
     */
    void remove(String sessionId);
}
