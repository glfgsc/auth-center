package com.auth.center.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.zset.Aggregate;
import org.springframework.data.redis.connection.zset.Weights;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的活跃会话注册表 —— 多实例共享,支撑会话治理控制台.
 *
 * 会话本体是 {@code auth:session:{sessionId}} 的 JSON,TTL 为会话剩余存活时间,到期由 Redis 自行删除。
 *
 * 为什么另建索引:管理台列表要「按最近活跃倒序、按产品收窄、分页」。这三件事都无法在一堆散键上完成 —— 扫全表、逐键读、再在内存里排序切片,每次列表约 2N 次 Redis
 * 往返(N = 全部活跃会话数),且不论翻到第几页都要把全表读一遍。故按 {@link SessionKeys#INDEX_KEY} 维护有序索引,「取第 N 页」只读该页那几条;产品维度与
 * {@link SessionKeys#productKey} 的成员集取交集,交集在 Redis 内完成,不把候选集拉回本地。
 *
 * 索引与本体的生命周期不一致:本体到期即消失,而 ZSET 成员不会随之消失。故另有 {@link SessionKeys#EXPIRY_KEY} 按到期时间打分,{@link
 * #sweepExpired} 据它精确摘除墓碑 —— 不摘则墓碑会让分页总数虚高,列表看着有几百条、翻过去全是空的。
 *
 * 仅当 {@code spring.data.redis.host} 存在时装配并 {@code @Primary} 覆盖内存实现。
 */
@Component
@Primary
@ConditionalOnProperty(name = "spring.data.redis.host", matchIfMissing = false)
public class RedisSessionRegistryService implements ISessionRegistryService {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionRegistryService.class);

    /** SCAN 单批游标大小(仅用于启动补建索引)。 */
    private static final long SCAN_COUNT = 200L;

    /** 单次清理最多摘除的墓碑数 —— 分摊到多次请求,避免一次列表被清理拖住。 */
    private static final long SWEEP_LIMIT = 500L;

    /** 无索引可用的过滤(按用户名)逐窗遍历时的窗口大小。 */
    private static final int WALK_BATCH = 200;

    /** 交集临时键存活时间 —— 正常路径用完即删,本 TTL 只兜底进程中途崩溃。 */
    private static final Duration QUERY_TEMP_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入。
     *
     * @param redisTemplate Redis 模板
     * @param objectMapper JSON 序列化器
     */
    public RedisSessionRegistryService(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        log.info("已启用 Redis 会话注册表");
    }

    /**
     * 启动时把既有会话补进索引。
     *
     * 索引是后加的,存量会话只有本体没有索引条目 —— 不补则升级后列表直接空。扫一遍现有本体与活跃度重建索引,{@code ZADD}/{@code SADD}
     * 幂等,多副本同时跑也无妨。之后的增量由 {@link #record}/{@link #remove} 与网关的活跃度上报维护。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillIndexes() {
        try {
            long sessions = backfillSessionIndex();
            long activities = backfillProductSets();
            log.info("[Session] 索引补建完成: 会话 {} 条, 活跃度 {} 条", sessions, activities);
        } catch (Exception e) {
            log.warn("[Session] 索引补建失败,列表可能不全: {}", e.getMessage());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void record(SessionInfo session) {
        long ttlMs = session.getExpiresAt() - System.currentTimeMillis();
        if (ttlMs <= 0) {
            return;
        }
        String json = serialize(session);
        if (json == null) {
            return;
        }
        String sid = session.getSessionId();
        redisTemplate
                .opsForValue()
                .set(SessionKeys.SESSION_PREFIX + sid, json, ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate.opsForZSet().add(SessionKeys.INDEX_KEY, sid, session.getLastActiveAt());
        redisTemplate.opsForZSet().add(SessionKeys.EXPIRY_KEY, sid, session.getExpiresAt());
        if (session.getUserId() != null) {
            // SET 没有成员级 TTL,成员靠 remove / listByUser 读时自愈摘除;整键 TTL 只兜底「该用户所有会话都过期后不留空集」,
            // 故每次登记都按最新会话的存活上限续一次。
            String userKey = SessionKeys.userKey(session.getUserId());
            redisTemplate.opsForSet().add(userKey, sid);
            redisTemplate.expire(userKey, ttlMs, TimeUnit.MILLISECONDS);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void touch(String sessionId, String accessJti, long accessExpiresAt, long lastActiveAt) {
        SessionInfo session = get(sessionId);
        if (session == null) {
            return;
        }
        session.setAccessJti(accessJti);
        session.setAccessExpiresAt(accessExpiresAt);
        session.setLastActiveAt(lastActiveAt);
        // 保留原会话过期时间(TTL 不因续期延长),按剩余时间重写;record 同时把索引分数刷成新的活跃时间。
        record(session);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(String sessionId) {
        // 先读本体取 userId —— 本体一删就再也认不出这条会话属于谁,用户成员集里会留下永不收敛的残留。
        SessionInfo session = get(sessionId);
        redisTemplate.delete(SessionKeys.SESSION_PREFIX + sessionId);
        redisTemplate.opsForZSet().remove(SessionKeys.INDEX_KEY, sessionId);
        redisTemplate.opsForZSet().remove(SessionKeys.EXPIRY_KEY, sessionId);
        if (session != null && session.getUserId() != null) {
            redisTemplate.opsForSet().remove(SessionKeys.userKey(session.getUserId()), sessionId);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<SessionInfo> listByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        String userKey = SessionKeys.userKey(userId);
        Set<String> ids = redisTemplate.opsForSet().members(userKey);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> ordered = new ArrayList<>(ids);
        List<String> keys = ordered.stream().map(id -> SessionKeys.SESSION_PREFIX + id).toList();
        List<String> raw = redisTemplate.opsForValue().multiGet(keys);
        List<SessionInfo> alive = new ArrayList<>(ordered.size());
        List<String> dead = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            SessionInfo session = deserialize(raw == null ? null : raw.get(i));
            if (session == null) {
                dead.add(ordered.get(i));
            } else {
                alive.add(session);
            }
        }
        if (!dead.isEmpty()) {
            // 本体到期而成员集还留着 —— 就地摘除,与 loadAlive 同一套自愈,否则并发上限会把早已消失的会话算进头寸。
            redisTemplate.opsForSet().remove(userKey, dead.toArray());
            dropFromIndexes(dead);
        }
        alive.sort(Comparator.comparingLong(SessionInfo::getLastActiveAt).reversed());
        return alive;
    }

    /** {@inheritDoc} */
    @Override
    public List<SessionInfo> listAll() {
        sweepExpired();
        List<String> ids =
                toList(redisTemplate.opsForZSet().reverseRange(SessionKeys.INDEX_KEY, 0, -1));
        return loadAlive(ids);
    }

    /** {@inheritDoc} */
    @Override
    public SessionInfo get(String sessionId) {
        return deserialize(redisTemplate.opsForValue().get(SessionKeys.SESSION_PREFIX + sessionId));
    }

    /** {@inheritDoc} */
    @Override
    public SessionPage listPage(String username, String systemCode, int offset, int limit) {
        if (limit <= 0) {
            return SessionPage.EMPTY;
        }
        sweepExpired();
        String tempKey = null;
        try {
            // 产品维度有成员集可用,交集在 Redis 内完成;交集结果仍按 lastActiveAt 打分(成员集权重取 0)。
            String source = SessionKeys.INDEX_KEY;
            if (isPresent(systemCode)) {
                tempKey = SessionKeys.QUERY_TEMP_PREFIX + UUID.randomUUID();
                redisTemplate
                        .opsForZSet()
                        .intersectAndStore(
                                SessionKeys.INDEX_KEY,
                                List.of(SessionKeys.productKey(systemCode)),
                                tempKey,
                                Aggregate.SUM,
                                Weights.of(1, 0));
                redisTemplate.expire(tempKey, QUERY_TEMP_TTL);
                source = tempKey;
            }
            // 用户名没有索引(是人手输入的检索条件,不值当为它维护一份成员集),只能逐窗遍历过滤;
            // 其余情形直接按下标取该页,不碰其它会话。
            return isPresent(username)
                    ? walkFiltered(source, username.trim(), offset, limit)
                    : slicePage(source, offset, limit);
        } catch (Exception e) {
            log.warn("[Session] 分页查询失败: {}", e.getMessage());
            return SessionPage.EMPTY;
        } finally {
            if (tempKey != null) {
                redisTemplate.delete(tempKey);
            }
        }
    }

    /**
     * 无额外过滤时:总数取 {@code ZCARD},本页按下标区间取。
     *
     * @param source 源有序集键
     * @param offset 起始偏移
     * @param limit 本页条数
     * @return 本页
     */
    private SessionPage slicePage(String source, int offset, int limit) {
        Long total = redisTemplate.opsForZSet().size(source);
        List<String> ids =
                toList(
                        redisTemplate
                                .opsForZSet()
                                .reverseRange(source, offset, (long) offset + limit - 1));
        return new SessionPage(loadAlive(ids), total == null ? 0L : total);
    }

    /**
     * 按用户名过滤时:逐窗遍历源集合,边过滤边定位本页。
     *
     * 要给出准确总数就必须走完整个源集合 —— 这是「过滤条件没有索引」的固有代价。读取按窗口批量进行,是 O(N/窗口) 次往返而非 O(N) 次,且只在有人真的输了用户名时才发生。
     *
     * @param source 源有序集键
     * @param username 用户名(精确、忽略大小写)
     * @param offset 起始偏移
     * @param limit 本页条数
     * @return 本页
     */
    private SessionPage walkFiltered(String source, String username, int offset, int limit) {
        List<SessionInfo> page = new ArrayList<>();
        long matched = 0;
        for (long start = 0; ; start += WALK_BATCH) {
            List<String> ids =
                    toList(
                            redisTemplate
                                    .opsForZSet()
                                    .reverseRange(source, start, start + WALK_BATCH - 1));
            if (ids.isEmpty()) {
                break;
            }
            for (SessionInfo s : loadAlive(ids)) {
                if (!username.equalsIgnoreCase(s.getUsername())) {
                    continue;
                }
                if (matched >= offset && page.size() < limit) {
                    page.add(s);
                }
                matched++;
            }
        }
        return new SessionPage(page, matched);
    }

    /**
     * 按 ID 批量取会话本体,顺带摘掉本体已消失的索引条目。
     *
     * 一次 {@code MGET} 取回整页,而不是逐个 {@code GET}。取回为空说明本体已到期而索引还留着 (清理未及时跑到),就地摘除,自愈。
     *
     * @param ids 会话 ID(已按需要排好序)
     * @return 仍存活的会话,顺序与入参一致
     */
    private List<SessionInfo> loadAlive(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<String> keys = ids.stream().map(id -> SessionKeys.SESSION_PREFIX + id).toList();
        List<String> raw = redisTemplate.opsForValue().multiGet(keys);
        List<SessionInfo> alive = new ArrayList<>(ids.size());
        List<String> dead = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String json = raw == null ? null : raw.get(i);
            SessionInfo session = deserialize(json);
            if (session == null) {
                dead.add(ids.get(i));
            } else {
                alive.add(session);
            }
        }
        if (!dead.isEmpty()) {
            dropFromIndexes(dead);
        }
        return alive;
    }

    /**
     * 摘除已到期会话在各索引里的残留。
     *
     * 产品成员集是按产品分开的少数几个键,扫一遍前缀即可全部覆盖 —— 不能只清「本次查询的那个产品」,否则其它产品的集合会一直涨。
     *
     * @param ids 已到期的会话 ID
     */
    private void dropFromIndexes(Collection<String> ids) {
        Object[] members = ids.toArray();
        redisTemplate.opsForZSet().remove(SessionKeys.INDEX_KEY, members);
        redisTemplate.opsForZSet().remove(SessionKeys.EXPIRY_KEY, members);
        for (String productKey : scanKeys(SessionKeys.PRODUCT_PREFIX + "*")) {
            redisTemplate.opsForSet().remove(productKey, members);
        }
    }

    /**
     * 摘除已到期会话 —— 由到期索引精确给出,不必探活全表。
     *
     * 单次上限 {@link #SWEEP_LIMIT},剩余的留给下次:清理是列表请求顺带做的,不该让某一次请求承担全部积压。
     */
    private void sweepExpired() {
        try {
            Set<String> expired =
                    redisTemplate
                            .opsForZSet()
                            .rangeByScore(
                                    SessionKeys.EXPIRY_KEY,
                                    Double.NEGATIVE_INFINITY,
                                    System.currentTimeMillis(),
                                    0,
                                    SWEEP_LIMIT);
            if (expired != null && !expired.isEmpty()) {
                dropFromIndexes(expired);
                log.debug("[Session] 摘除到期会话索引 {} 条", expired.size());
            }
        } catch (Exception e) {
            log.warn("[Session] 清理到期索引失败: {}", e.getMessage());
        }
    }

    /**
     * 扫描既有会话本体,补建有序索引、到期索引与用户维度成员集。
     *
     * 用户成员集必须一并补:并发会话限制按它判定该用户还有哪些会话,不补则升级前就已登录的会话在集合里缺席,下次登录顶不掉它们 —— 且 refresh 族存活上限长达数日,这个窗口不短。
     *
     * @return 补进索引的会话数
     */
    private long backfillSessionIndex() {
        long count = 0;
        for (String key : scanKeys(SessionKeys.SESSION_PREFIX + "*")) {
            SessionInfo session = deserialize(redisTemplate.opsForValue().get(key));
            if (session == null || session.getSessionId() == null) {
                continue;
            }
            redisTemplate
                    .opsForZSet()
                    .add(SessionKeys.INDEX_KEY, session.getSessionId(), session.getLastActiveAt());
            redisTemplate
                    .opsForZSet()
                    .add(SessionKeys.EXPIRY_KEY, session.getSessionId(), session.getExpiresAt());
            if (session.getUserId() != null) {
                String userKey = SessionKeys.userKey(session.getUserId());
                redisTemplate.opsForSet().add(userKey, session.getSessionId());
                long ttlMs = session.getExpiresAt() - System.currentTimeMillis();
                if (ttlMs > 0) {
                    redisTemplate.expire(userKey, ttlMs, TimeUnit.MILLISECONDS);
                }
            }
            count++;
        }
        return count;
    }

    /**
     * 扫描既有活跃度记录,补建各产品的成员集。
     *
     * @return 补进成员集的会话数
     */
    private long backfillProductSets() {
        long count = 0;
        for (String key : scanKeys(SessionKeys.ACTIVITY_PREFIX + "*")) {
            String sid = key.substring(SessionKeys.ACTIVITY_PREFIX.length());
            Set<Object> systems = redisTemplate.opsForHash().keys(key);
            if (systems == null || systems.isEmpty()) {
                continue;
            }
            for (Object system : systems) {
                redisTemplate.opsForSet().add(SessionKeys.productKey(String.valueOf(system)), sid);
            }
            count++;
        }
        return count;
    }

    /**
     * 按 glob 扫描键名(SCAN,非 KEYS,不阻塞)。
     *
     * @param pattern 键名匹配式
     * @return 匹配的键名
     */
    private List<String> scanKeys(String pattern) {
        List<String> keys = new ArrayList<>();
        try (Cursor<String> cursor =
                redisTemplate.scan(
                        ScanOptions.scanOptions().match(pattern).count(SCAN_COUNT).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.warn("[Session] 扫描键失败 {}: {}", pattern, e.getMessage());
        }
        return keys;
    }

    /**
     * 判断过滤条件是否给了值。
     *
     * @param value 条件值
     * @return 非空且非空白为 {@code true}
     */
    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 有序集查询结果转列表(保留顺序;{@code null} 视作空)。
     *
     * @param members 查询结果
     * @return 列表
     */
    private static List<String> toList(Set<String> members) {
        return members == null ? List.of() : new ArrayList<>(members);
    }

    /**
     * SessionInfo → JSON。
     *
     * @param session 会话
     * @return JSON;失败返回 {@code null}
     */
    private String serialize(SessionInfo session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            log.warn("[Session] 序列化会话失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * JSON → SessionInfo。
     *
     * @param json JSON(可空)
     * @return 会话;空或失败返回 {@code null}
     */
    private SessionInfo deserialize(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, SessionInfo.class);
        } catch (Exception e) {
            log.warn("[Session] 反序列化会话失败: {}", e.getMessage());
            return null;
        }
    }
}
