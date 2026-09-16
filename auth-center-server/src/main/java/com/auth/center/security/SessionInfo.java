package com.auth.center.security;

import java.util.Map;

/**
 * 活跃会话信息 —— 一次登录对应一条,以 refresh token 族 ID(familyId)为稳定主键.
 *
 * access token 短命(默认 15 分钟)且由 refresh 无感续期,故「会话」以登录(refresh 族)为单位,跨多次 access 续期保持稳定。{@code
 * accessJti} / {@code accessExpiresAt} 随每次续期更新,供吊销时精确拉黑当前 access token。序列化为 JSON 存入 Redis {@code
 * auth:session:{sessionId}}。
 */
public class SessionInfo {

    /** 会话 ID —— 即 refresh token 族 ID(familyId),跨续期稳定;亦作为 access token 的 {@code sid} claim。 */
    private String sessionId;

    /** 用户 ID。 */
    private Long userId;

    /** 用户名(快照)。 */
    private String username;

    /** 当前 access token 的 JTI —— 随每次续期更新,吊销时据此拉黑当前令牌。 */
    private String accessJti;

    /** 当前 access token 的过期时间(epoch ms)—— 作为拉黑条目的 TTL 上界。 */
    private long accessExpiresAt;

    /** 登录时间(epoch ms)。 */
    private long loginAt;

    /** 最近活跃时间(epoch ms)—— 每次续期刷新。 */
    private long lastActiveAt;

    /** 会话过期时间(epoch ms)—— 即 refresh 族存活上限,作为注册表条目的 TTL。 */
    private long expiresAt;

    /** 客户端 IP(取 X-Forwarded-For 首段,回退 remoteAddr)。 */
    private String ip;

    /** 客户端 User-Agent 原串(前端解析为设备/浏览器展示)。 */
    private String userAgent;

    /** IP 归类:INTERNAL / PUBLIC —— 供列表在无精确地点时仍能展示「内网 / 公网」。 */
    private String ipClass;

    /** 登录地点 —— 内网为 {@code null}(展示「内网」),公网经可插拔 GeoIP 解析,无库时 {@code null}。 */
    private String location;

    /** 登录时算出的异常标记快照,逗号分隔(NEW_IP / NEW_DEVICE / CONCURRENT_LOCATION)—— 供会话列表标风险。 */
    private String anomalies;

    /**
     * 各业务系统最近使用时间(系统码 → epoch ms)—— 展示「令牌正在哪些系统活跃」。
     *
     * 非会话本体属性,不落注册表:登录时为空,列表读取时由 {@link ISessionActivityService} 合并填充。
     */
    private Map<String, Long> systemActivity;

    /**
     * 发起本次登录的产品编码，对齐 {@code auth_system.code}（可空）。
     *
     * 与 {@link #systemActivity} 是两回事:那个是令牌之后在哪些产品用过（一次登录可跨多个产品），
     * 这个是这次登录从哪个产品发起。{@code null} = 调用方登录时未报来源产品；产品前端目前均未上报，故本字段恒空，管理台按产品筛会话走的是 {@link
     * #systemActivity}。
     */
    private String systemCode;

    /** 无参构造 —— Jackson 反序列化需要。 */
    public SessionInfo() {}

    /**
     * 获取会话 ID。
     *
     * @return 会话 ID(familyId)
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 设置会话 ID。
     *
     * @param sessionId 会话 ID(familyId)
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * 获取用户 ID。
     *
     * @return 用户 ID
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置用户 ID。
     *
     * @param userId 用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 获取用户名。
     *
     * @return 用户名
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置用户名。
     *
     * @param username 用户名
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 获取当前 access JTI。
     *
     * @return 当前 access token 的 JTI
     */
    public String getAccessJti() {
        return accessJti;
    }

    /**
     * 设置当前 access JTI。
     *
     * @param accessJti 当前 access token 的 JTI
     */
    public void setAccessJti(String accessJti) {
        this.accessJti = accessJti;
    }

    /**
     * 获取当前 access token 过期时间。
     *
     * @return 过期时间(epoch ms)
     */
    public long getAccessExpiresAt() {
        return accessExpiresAt;
    }

    /**
     * 设置当前 access token 过期时间。
     *
     * @param accessExpiresAt 过期时间(epoch ms)
     */
    public void setAccessExpiresAt(long accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    /**
     * 获取登录时间。
     *
     * @return 登录时间(epoch ms)
     */
    public long getLoginAt() {
        return loginAt;
    }

    /**
     * 设置登录时间。
     *
     * @param loginAt 登录时间(epoch ms)
     */
    public void setLoginAt(long loginAt) {
        this.loginAt = loginAt;
    }

    /**
     * 获取最近活跃时间。
     *
     * @return 最近活跃时间(epoch ms)
     */
    public long getLastActiveAt() {
        return lastActiveAt;
    }

    /**
     * 设置最近活跃时间。
     *
     * @param lastActiveAt 最近活跃时间(epoch ms)
     */
    public void setLastActiveAt(long lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }

    /**
     * 获取会话过期时间。
     *
     * @return 会话过期时间(epoch ms)
     */
    public long getExpiresAt() {
        return expiresAt;
    }

    /**
     * 设置会话过期时间。
     *
     * @param expiresAt 会话过期时间(epoch ms)
     */
    public void setExpiresAt(long expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * 获取客户端 IP。
     *
     * @return 客户端 IP
     */
    public String getIp() {
        return ip;
    }

    /**
     * 设置客户端 IP。
     *
     * @param ip 客户端 IP
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * 获取 User-Agent。
     *
     * @return User-Agent 原串
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * 设置 User-Agent。
     *
     * @param userAgent User-Agent 原串
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * 获取 IP 归类。
     *
     * @return IP 归类(INTERNAL / PUBLIC)
     */
    public String getIpClass() {
        return ipClass;
    }

    /**
     * 设置 IP 归类。
     *
     * @param ipClass IP 归类(INTERNAL / PUBLIC)
     */
    public void setIpClass(String ipClass) {
        this.ipClass = ipClass;
    }

    /**
     * 获取登录地点。
     *
     * @return 登录地点(内网或无法解析为 {@code null})
     */
    public String getLocation() {
        return location;
    }

    /**
     * 设置登录地点。
     *
     * @param location 登录地点
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * 获取登录异常标记快照。
     *
     * @return 逗号分隔的异常标记(无为 {@code null})
     */
    public String getAnomalies() {
        return anomalies;
    }

    /**
     * 设置登录异常标记快照。
     *
     * @param anomalies 逗号分隔的异常标记
     */
    public void setAnomalies(String anomalies) {
        this.anomalies = anomalies;
    }

    /**
     * 获取各业务系统最近使用时间。
     *
     * @return 系统码 → epoch ms(可空)
     */
    public Map<String, Long> getSystemActivity() {
        return systemActivity;
    }

    /**
     * 设置各业务系统最近使用时间。
     *
     * @param systemActivity 系统码 → epoch ms
     */
    public void setSystemActivity(Map<String, Long> systemActivity) {
        this.systemActivity = systemActivity;
    }

    /**
     * 获取发起本次登录的产品编码。
     *
     * @return 产品编码，未报为 {@code null}
     */
    public String getSystemCode() {
        return systemCode;
    }

    /**
     * 设置发起本次登录的产品编码。
     *
     * @param systemCode 产品编码
     */
    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }
}
