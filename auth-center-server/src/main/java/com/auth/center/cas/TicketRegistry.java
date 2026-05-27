package com.auth.center.cas;

/**
 * CAS 票据注册中心接口 -- 管理 TGT 和 ST 的生命周期.
 *
 * <p>提供创建、查询、销毁 TGT 以及签发和验证 ST 的能力.
 * 实现类需保证线程安全.
 */
public interface TicketRegistry {

    /**
     * 创建新的 TGT (Ticket Granting Ticket).
     *
     * @param userId        用户 ID
     * @param username      用户名
     * @param permissionSet 权限集名称
     * @param capabilities  逗号分隔的能力列表
     * @return 创建的 TGT 实例
     */
    TicketGrantingTicket createTgt(Long userId, String username,
                                   String permissionSet, String capabilities);

    /**
     * 根据 ID 获取 TGT.
     *
     * @param tgtId TGT 的唯一标识
     * @return TGT 实例, 不存在或已过期时返回 {@code null}
     */
    TicketGrantingTicket getTgt(String tgtId);

    /**
     * 销毁指定的 TGT 及其关联的所有 ST.
     *
     * @param tgtId TGT 的唯一标识
     */
    void removeTgt(String tgtId);

    /**
     * 基于已有 TGT 签发 Service Ticket.
     *
     * @param tgt        有效的 TGT 实例
     * @param serviceUrl 请求服务的回调 URL
     * @return 创建的 ST 实例
     */
    ServiceTicket createSt(TicketGrantingTicket tgt, String serviceUrl);

    /**
     * 验证 Service Ticket 的有效性.
     *
     * <p>验证逻辑:
     * <ul>
     *     <li>票据存在</li>
     *     <li>票据未过期</li>
     *     <li>票据未被使用过 (一次性)</li>
     *     <li>服务 URL 匹配</li>
     * </ul>
     * 验证成功后票据标记为已使用.
     *
     * @param ticketId   ST 的唯一标识
     * @param serviceUrl 请求服务的回调 URL (必须与签发时一致)
     * @return 验证通过返回 ST 实例, 验证失败返回 {@code null}
     */
    ServiceTicket validateSt(String ticketId, String serviceUrl);
}
