package com.auth.center.cas.impl;

import com.auth.center.cas.ServiceTicket;
import com.auth.center.cas.TicketGrantingTicket;
import com.auth.center.cas.TicketRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Redis 的 CAS 票据注册中心实现.
 *
 * <p>使用 {@link StringRedisTemplate} 将 TGT 和 ST 序列化为 JSON 存储到 Redis，
 * 通过 Redis TTL 自动清理过期票据，无需定时任务。</p>
 *
 * <p>Redis Key 规则:
 * <ul>
 *     <li>TGT: {@code cas:tgt:{tgtId}}, TTL = 8 小时</li>
 *     <li>ST: {@code cas:st:{stId}}, TTL = 30 秒</li>
 * </ul>
 *
 * <p>仅当 {@code spring.data.redis.host} 配置存在时才装配此实现，
 * 并通过 {@code @Primary} 覆盖 InMemory 实现。</p>
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedisTicketRegistry implements TicketRegistry {

    private static final Logger log = LoggerFactory.getLogger(RedisTicketRegistry.class);

    /** TGT Redis Key 前缀 */
    private static final String TGT_KEY_PREFIX = "cas:tgt:";

    /** ST Redis Key 前缀 */
    private static final String ST_KEY_PREFIX = "cas:st:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /** Service Ticket 有效期 (毫秒), 默认 30 秒 */
    @Value("${auth.cas.ticket-ttl:30000}")
    private long stTtl;

    /** TGT 有效期 (毫秒), 默认 8 小时 */
    @Value("${auth.cas.tgt-ttl:28800000}")
    private long tgtTtl;

    /**
     * 构造函数，注入 Redis 模板和 JSON 序列化器.
     *
     * @param redisTemplate StringRedisTemplate 实例
     * @param objectMapper  Jackson ObjectMapper 实例
     */
    public RedisTicketRegistry(StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        log.info("已启用 Redis 票据注册中心");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketGrantingTicket createTgt(Long userId, String username,
                                          String permissionSet, String capabilities) {
        TicketGrantingTicket tgt = new TicketGrantingTicket(userId, username,
                permissionSet, capabilities, tgtTtl);
        String key = TGT_KEY_PREFIX + tgt.getId();
        String json = serialize(tgt);
        redisTemplate.opsForValue().set(key, json, tgtTtl, TimeUnit.MILLISECONDS);
        log.info("已创建 TGT (Redis): id={}, user={}", tgt.getId(), username);
        return tgt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TicketGrantingTicket getTgt(String tgtId) {
        String key = TGT_KEY_PREFIX + tgtId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        TicketGrantingTicket tgt = deserialize(json, TicketGrantingTicket.class);
        if (tgt == null || tgt.isExpired()) {
            redisTemplate.delete(key);
            return null;
        }
        return tgt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeTgt(String tgtId) {
        String key = TGT_KEY_PREFIX + tgtId;
        Boolean deleted = redisTemplate.delete(key);
        if (Boolean.TRUE.equals(deleted)) {
            log.info("已销毁 TGT (Redis): id={}", tgtId);
        }
        // Redis 中 ST 关联清理依赖 TTL 自动过期，无需显式遍历删除
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
        String key = ST_KEY_PREFIX + st.getId();
        String json = serialize(st);
        redisTemplate.opsForValue().set(key, json, stTtl, TimeUnit.MILLISECONDS);
        log.info("已签发 ST (Redis): id={}, service={}, user={}", st.getId(), serviceUrl, tgt.getUsername());
        return st;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceTicket validateSt(String ticketId, String serviceUrl) {
        String key = ST_KEY_PREFIX + ticketId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            log.warn("ST 验证失败 (Redis): 票据不存在, id={}", ticketId);
            return null;
        }

        // 一次性使用: 验证后立即删除
        redisTemplate.delete(key);

        ServiceTicket st = deserialize(json, ServiceTicket.class);
        if (st == null) {
            log.warn("ST 验证失败 (Redis): 反序列化失败, id={}", ticketId);
            return null;
        }
        if (st.isExpired()) {
            log.warn("ST 验证失败 (Redis): 票据已过期, id={}", ticketId);
            return null;
        }
        if (st.isUsed()) {
            log.warn("ST 验证失败 (Redis): 票据已被使用, id={}", ticketId);
            return null;
        }
        if (!serviceUrl.equals(st.getServiceUrl())) {
            log.warn("ST 验证失败 (Redis): 服务 URL 不匹配, id={}, 期望={}, 实际={}",
                    ticketId, st.getServiceUrl(), serviceUrl);
            return null;
        }

        st.setUsed(true);
        log.info("ST 验证成功 (Redis): id={}, user={}", ticketId, st.getUsername());
        return st;
    }

    /**
     * 序列化对象为 JSON 字符串.
     *
     * @param obj 待序列化的对象
     * @return JSON 字符串
     */
    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("JSON 序列化失败: {}", e.getMessage());
            throw new IllegalStateException("票据序列化失败", e);
        }
    }

    /**
     * 反序列化 JSON 字符串为指定类型.
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   目标类型参数
     * @return 反序列化后的对象，失败时返回 {@code null}
     */
    private <T> T deserialize(String json, Class<T> clazz) {
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("JSON 反序列化失败: class={}, error={}", clazz.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
