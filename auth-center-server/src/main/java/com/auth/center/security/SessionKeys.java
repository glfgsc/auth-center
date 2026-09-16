package com.auth.center.security;

/**
 * 会话治理的共享 Redis 键约定 —— 集中一处,避免各写各的拼串.
 *
 * 命名空间纪律:除会话本体外,其余键一律用连字符另起命名空间(如 {@code auth:session-index}),绝不落进 {@code
 * auth:session:} 之下。会话本体是字符串键,历史上按 glob {@code auth:session:*} 扫描过,任何落进该 glob 的非字符串键(ZSET /
 * SET / Hash)都会在按字符串读取时抛 {@code WRONGTYPE}。
 *
 * 跨服务写入方:{@link #productKey} 指向的集合不止 auth-center 写 —— bi-gateway 的 {@code
 * JwtGlobalFilter} 在上报会话活跃度时同步 {@code SADD},两边的键拼法必须逐字一致。改这里的任何常量都要同步改网关那一份,否则表现为管理台按产品筛静默漏数据。
 *
 * 跨服务读取方:{@link #revokedKey} 由 bi-gateway 每请求读取,同样要求两边拼法逐字一致 —— 不一致时表现为会话已吊销而令牌仍被放行。
 */
public final class SessionKeys {

    /** 会话本体:{@code auth:session:{sessionId}} = SessionInfo 的 JSON,TTL = 会话剩余存活时间。 */
    public static final String SESSION_PREFIX = "auth:session:";

    /** 会话跨系统活跃度:{@code auth:session-activity:{sessionId}} = Hash(系统码 → 最近使用时间 ms)。 */
    public static final String ACTIVITY_PREFIX = "auth:session-activity:";

    /**
     * 活跃会话有序索引:ZSET,member = sessionId,score = {@code lastActiveAt}(epoch ms)。
     *
     * 列表按最近活跃倒序分页即取自这里,使「取第 N 页」只读该页所需的会话,而非把全表读进内存再切片。由 auth-center 在 {@code record}/{@code
     * touch}/{@code remove} 三处维护。
     */
    public static final String INDEX_KEY = "auth:session-index";

    /**
     * 会话到期索引:ZSET,member = sessionId,score = {@code expiresAt}(epoch ms)。
     *
     * 专为清理而立:会话本体到期由 Redis 按 TTL 自行删除,但 TTL 不会传染给索引条目,不清理则 {@link #INDEX_KEY}
     * 里留下墓碑,分页总数随之虚高。有了本索引,「哪些已到期」是一次 {@code ZRANGEBYSCORE -inf now} 的精确回答,不必去猜、也不必全表探活。
     */
    public static final String EXPIRY_KEY = "auth:session-expiry";

    /** 产品维度成员集前缀 —— 见 {@link #productKey}。 */
    public static final String PRODUCT_PREFIX = "auth:session-product:";

    /** 分页查询的临时交集键前缀 —— 每次查询一个,带短 TTL 兜底防泄漏。 */
    public static final String QUERY_TEMP_PREFIX = "auth:session-q:";

    /** 用户维度成员集前缀 —— 见 {@link #userKey}。 */
    public static final String USER_PREFIX = "auth:session-user:";

    /** 会话级吊销标记前缀 —— 见 {@link #revokedKey}。 */
    public static final String REVOKED_PREFIX = "auth:session-revoked:";

    /**
     * 某用户的活跃会话集合:SET,member = sessionId,TTL = 最近一次登录的会话存活上限(兜底,正常靠 {@code SREM} 收敛)。
     *
     * 立此集合是为了「该用户还有哪些会话」能一次答出:并发会话限制在每次登录时都要问一遍这个问题,走全表扫描则登录耗时随在线人数线性上涨。
     *
     * @param userId 用户 ID
     * @return 该用户的会话成员集键
     */
    public static String userKey(Long userId) {
        return USER_PREFIX + userId;
    }

    /**
     * 会话级吊销标记:{@code auth:session-revoked:{sessionId}} = 吊销原因码,TTL = 会话剩余存活时间。
     *
     * 与按 JTI 拉黑是两道闸,缺一不可:JTI 黑名单只钉住「注册表记着的那一枚」access 令牌,被吊销设备手上若还攥着上一轮续期前的令牌,在其自然寿命内仍然通行。会话级标记按
     * {@code sid} claim 判定,一次吊销覆盖该会话签发过的全部令牌。值存原因码而非 {@code "1"},使网关能把「被顶下线」与「登出 / 过期」区分着回给前端。
     *
     * @param sessionId 会话 ID(refresh 族 ID,即 access token 的 {@code sid} claim)
     * @return 该会话的吊销标记键
     */
    public static String revokedKey(String sessionId) {
        return REVOKED_PREFIX + sessionId;
    }

    /**
     * 某产品下的活跃会话集合:SET,member = sessionId。
     *
     * 与有序索引取交集即可「只取该产品这一页」。写入方有两个:bi-gateway 上报活跃度时 {@code SADD},auth-center 在吊销会话时
     * {@code SREM}(见类注释)。
     *
     * @param system 产品编码,对齐 {@code auth_system.code}
     * @return 该产品的成员集键
     */
    public static String productKey(String system) {
        return PRODUCT_PREFIX + system;
    }

    /** 工具类不实例化。 */
    private SessionKeys() {}
}
