package com.auth.center.cas.impl;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.cas.TicketGrantingTicket;
import com.auth.center.cas.TicketRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于内存的 CAS 票据注册中心实现.
 *
 * <p>使用 {@link ConcurrentHashMap} 存储 TGT 和 ST, 保证线程安全.
 * 后台定时任务每 5 分钟清理过期票据, 防止内存泄漏.
 */
@Component
public class InMemoryTicketRegistry implements TicketRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryTicketRegistry.class);

    /** 过期票据清理间隔 (毫秒): 5 分钟 */
    private static final long CLEANUP_INTERVAL_MS = 5L * 60 * 1000;

    /** TGT 存储: tgtId -> TicketGrantingTicket */
    private final ConcurrentHashMap<String, TicketGrantingTicket> tgtStore = new ConcurrentHashMap<>();

    /** ST 存储: stId -> ServiceTicket */
    private final ConcurrentHashMap<String, ServiceTicket> stStore = new ConcurrentHashMap<>();

    /** Service Ticket 有效期 (毫秒), 默认 30 秒 */
    @Value("${auth.cas.ticket-ttl:30000}")
    private long stTtl;

    /** TGT 有效期 (毫秒), 默认 8 小时 */
    @Value("${auth.cas.tgt-ttl:28800000}")
    private long tgtTtl;

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketGrantingTicket createTgt(Long userId, String username,
                                          String permissionSet, String capabilities) {
        TicketGrantingTicket tgt = new TicketGrantingTicket(userId, username,
                permissionSet, capabilities, tgtTtl);
        tgtStore.put(tgt.getId(), tgt);
        log.info("已创建 TGT: id={}, user={}", tgt.getId(), username);
        return tgt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketGrantingTicket getTgt(String tgtId) {
        TicketGrantingTicket tgt = tgtStore.get(tgtId);
        if (tgt == null) {
            return null;
        }
        // 已过期则移除并返回 null
        if (tgt.isExpired()) {
            tgtStore.remove(tgtId);
            log.info("TGT 已过期并移除: id={}", tgtId);
            return null;
        }
        return tgt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeTgt(String tgtId) {
        TicketGrantingTicket removed = tgtStore.remove(tgtId);
        if (removed == null) {
            return;
        }
        // 同时移除该 TGT 关联的所有 ST
        int stRemoved = 0;
        Iterator<Map.Entry<String, ServiceTicket>> it = stStore.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ServiceTicket> entry = it.next();
            if (tgtId.equals(entry.getValue().getTgtId())) {
                it.remove();
                stRemoved++;
            }
        }
        log.info("已销毁 TGT: id={}, 同时移除关联 ST {} 个", tgtId, stRemoved);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceTicket createSt(TicketGrantingTicket tgt, String serviceUrl) {
        ServiceTicket st = new ServiceTicket(
                tgt.getId(), serviceUrl,
                tgt.getUserId(), tgt.getUsername(),
                tgt.getPermissionSet(), tgt.getCapabilities(),
                stTtl
        );
        stStore.put(st.getId(), st);
        log.info("已签发 ST: id={}, service={}, user={}", st.getId(), serviceUrl, tgt.getUsername());
        return st;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceTicket validateSt(String ticketId, String serviceUrl) {
        ServiceTicket st = stStore.get(ticketId);
        if (st == null) {
            log.warn("ST 验证失败: 票据不存在, id={}", ticketId);
            return null;
        }
        // 检查是否已过期
        if (st.isExpired()) {
            stStore.remove(ticketId);
            log.warn("ST 验证失败: 票据已过期, id={}", ticketId);
            return null;
        }
        // 检查是否已使用 (一次性)
        if (st.isUsed()) {
            log.warn("ST 验证失败: 票据已被使用, id={}", ticketId);
            return null;
        }
        // 检查服务 URL 是否匹配
        if (!serviceUrl.equals(st.getServiceUrl())) {
            log.warn("ST 验证失败: 服务 URL 不匹配, id={}, 期望={}, 实际={}",
                    ticketId, st.getServiceUrl(), serviceUrl);
            return null;
        }
        // 验证通过, 标记为已使用
        st.setUsed(true);
        log.info("ST 验证成功: id={}, user={}", ticketId, st.getUsername());
        return st;
    }

    /**
     * 定时清理过期的 TGT 和 ST, 每 5 分钟执行一次.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    public void cleanup() {
        long now = System.currentTimeMillis();

        // 清理过期 TGT
        int tgtRemoved = 0;
        Iterator<Map.Entry<String, TicketGrantingTicket>> tgtIt = tgtStore.entrySet().iterator();
        while (tgtIt.hasNext()) {
            if (tgtIt.next().getValue().isExpired()) {
                tgtIt.remove();
                tgtRemoved++;
            }
        }

        // 清理过期或已使用的 ST
        int stRemoved = 0;
        Iterator<Map.Entry<String, ServiceTicket>> stIt = stStore.entrySet().iterator();
        while (stIt.hasNext()) {
            ServiceTicket st = stIt.next().getValue();
            if (st.isExpired() || st.isUsed()) {
                stIt.remove();
                stRemoved++;
            }
        }

        if (tgtRemoved > 0 || stRemoved > 0) {
            log.info("票据清理完成: 移除 TGT {} 个, ST {} 个; 剩余 TGT {} 个, ST {} 个",
                    tgtRemoved, stRemoved, tgtStore.size(), stStore.size());
        }
    }
}
