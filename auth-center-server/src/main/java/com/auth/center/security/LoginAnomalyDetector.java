package com.auth.center.security;

import com.auth.center.entity.AuthLoginHistory;
import com.auth.center.mapper.AuthLoginHistoryMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 登录异常检测器 —— 基于登录历史留痕对「异常登录」信号做启发式判定.
 *
 * 检出四类异常:新 IP({@link #ANOMALY_NEW_IP})、新设备({@link #ANOMALY_NEW_DEVICE})、失败突发({@link
 * #ANOMALY_FAILED_BURST})、并发异地({@link #ANOMALY_CONCURRENT_LOCATION})。
 * 判定读取「当前尝试之前」的历史(检测在落库之前调用),故不含本次记录。首次成功登录无历史基线,不误报新 IP / 新设备。生产无外网:并发异地以「近窗口内另一公网 IP
 * 的成功登录」近似不可能旅行,不依赖外部 GeoIP。
 */
@Component
public class LoginAnomalyDetector {

    /** 登录结果:成功。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 登录结果:失败。 */
    public static final String STATUS_FAILED = "FAILED";

    /** 异常:首次出现的 IP(该用户历史成功登录中从未见过)。 */
    public static final String ANOMALY_NEW_IP = "NEW_IP";

    /** 异常:首次出现的设备(该用户历史成功登录中从未见过的设备指纹)。 */
    public static final String ANOMALY_NEW_DEVICE = "NEW_DEVICE";

    /** 异常:失败突发(近窗口内该用户名失败次数达阈值)。 */
    public static final String ANOMALY_FAILED_BURST = "FAILED_BURST";

    /** 异常:并发异地(近窗口内存在来自另一公网 IP 的成功登录,疑似不可能旅行)。 */
    public static final String ANOMALY_CONCURRENT_LOCATION = "CONCURRENT_LOCATION";

    /** 失败突发阈值:近窗口内失败次数(含本次)达到即判定。 */
    private static final int FAILED_BURST_THRESHOLD = 5;

    /** 失败突发时间窗口(分钟)。 */
    private static final int FAILED_BURST_WINDOW_MINUTES = 15;

    /** 并发异地时间窗口(分钟)。 */
    private static final int CONCURRENT_WINDOW_MINUTES = 30;

    /** 新 IP / 新设备基线回溯的历史成功记录条数上限(借 idx_user_time,查询廉价)。 */
    private static final int HISTORY_LOOKBACK_LIMIT = 200;

    private final AuthLoginHistoryMapper loginHistoryMapper;

    /**
     * 构造注入。
     *
     * @param loginHistoryMapper 登录历史 Mapper
     */
    public LoginAnomalyDetector(AuthLoginHistoryMapper loginHistoryMapper) {
        this.loginHistoryMapper = loginHistoryMapper;
    }

    /**
     * 检测本次登录尝试的异常标记。必须在本次登录记录落库「之前」调用,以免把当前尝试算入历史基线。
     *
     * @param userId 用户 id(失败且用户不存在时为空)
     * @param username 登录用户名(尝试值)
     * @param ip 客户端 IP
     * @param ipClass IP 归类({@link IpGeoService#CLASS_INTERNAL} / {@link IpGeoService#CLASS_PUBLIC})
     * @param userAgent User-Agent 原串
     * @param status 登录结果({@link #STATUS_SUCCESS} / {@link #STATUS_FAILED})
     * @param now 当前时间
     * @return 逗号分隔的异常标记;无异常返回 {@code null}
     */
    public String detect(
            Long userId,
            String username,
            String ip,
            String ipClass,
            String userAgent,
            String status,
            LocalDateTime now) {
        List<String> flags = new ArrayList<>();

        // 失败尝试:仅失败突发有意义(新 IP / 新设备以成功登录为基线)
        if (STATUS_FAILED.equals(status)) {
            if (isFailedBurst(username, now)) {
                flags.add(ANOMALY_FAILED_BURST);
            }
            return join(flags);
        }

        // 成功登录:以历史成功记录为基线判定新 IP / 新设备
        List<AuthLoginHistory> priorSuccess = recentSuccess(userId);
        if (!priorSuccess.isEmpty()) {
            if (ip != null && priorSuccess.stream().noneMatch(h -> ip.equals(h.getIp()))) {
                flags.add(ANOMALY_NEW_IP);
            }
            String device = deviceKey(userAgent);
            if (device != null
                    && priorSuccess.stream()
                            .noneMatch(h -> device.equals(deviceKey(h.getUserAgent())))) {
                flags.add(ANOMALY_NEW_DEVICE);
            }
        }

        // 并发异地:本次为公网且近窗口内存在来自另一公网 IP 的成功登录
        if (IpGeoService.CLASS_PUBLIC.equals(ipClass)
                && hasConcurrentPublicLogin(userId, ip, now)) {
            flags.add(ANOMALY_CONCURRENT_LOCATION);
        }

        return join(flags);
    }

    /**
     * 判定失败突发:近窗口内该用户名的失败次数(含本次)是否达阈值。
     *
     * @param username 登录用户名
     * @param now 当前时间
     * @return 达阈值返回 {@code true}
     */
    private boolean isFailedBurst(String username, LocalDateTime now) {
        if (username == null || username.isBlank()) {
            return false;
        }
        LocalDateTime since = now.minusMinutes(FAILED_BURST_WINDOW_MINUTES);
        LambdaQueryWrapper<AuthLoginHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginHistory::getUsername, username)
                .eq(AuthLoginHistory::getStatus, STATUS_FAILED)
                .ge(AuthLoginHistory::getLoginTime, since);
        long priorFails = loginHistoryMapper.selectCount(wrapper);
        // 历史失败数 + 本次这一次,达到阈值即为突发
        return priorFails + 1 >= FAILED_BURST_THRESHOLD;
    }

    /**
     * 取该用户最近的历史成功登录记录(作为新 IP / 新设备基线)。
     *
     * @param userId 用户 id
     * @return 最近成功记录(倒序,上限 {@link #HISTORY_LOOKBACK_LIMIT});userId 为空返回空列表
     */
    private List<AuthLoginHistory> recentSuccess(Long userId) {
        if (userId == null) {
            return List.of();
        }
        LambdaQueryWrapper<AuthLoginHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginHistory::getUserId, userId)
                .eq(AuthLoginHistory::getStatus, STATUS_SUCCESS)
                .orderByDesc(AuthLoginHistory::getLoginTime)
                .last("LIMIT " + HISTORY_LOOKBACK_LIMIT);
        return loginHistoryMapper.selectList(wrapper);
    }

    /**
     * 判定并发异地:近窗口内是否存在该用户来自「另一公网 IP」的成功登录。
     *
     * @param userId 用户 id
     * @param ip 本次公网 IP
     * @param now 当前时间
     * @return 存在则 {@code true}
     */
    private boolean hasConcurrentPublicLogin(Long userId, String ip, LocalDateTime now) {
        if (userId == null || ip == null || ip.isBlank()) {
            return false;
        }
        LocalDateTime since = now.minusMinutes(CONCURRENT_WINDOW_MINUTES);
        LambdaQueryWrapper<AuthLoginHistory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginHistory::getUserId, userId)
                .eq(AuthLoginHistory::getStatus, STATUS_SUCCESS)
                .eq(AuthLoginHistory::getIpClass, IpGeoService.CLASS_PUBLIC)
                .ne(AuthLoginHistory::getIp, ip)
                .ge(AuthLoginHistory::getLoginTime, since);
        return loginHistoryMapper.selectCount(wrapper) > 0;
    }

    /**
     * 由 User-Agent 提炼粗粒度设备指纹(操作系统族 + 浏览器族,去版本号),用于新设备判定.
     *
     * 去版本号避免浏览器小版本升级误报新设备。判定顺序处理 UA 串包含关系:Edge UA 含 {@code chrome}, 故先判 Edge;Chrome UA 含 {@code
     * safari},故先判 Chrome。
     *
     * @param userAgent User-Agent 原串
     * @return 形如 {@code windows/chrome} 的指纹;UA 空返回 {@code null}
     */
    private String deviceKey(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        String s = userAgent.toLowerCase();
        String os = "unknown";
        if (s.contains("windows")) {
            os = "windows";
        } else if (s.contains("iphone") || s.contains("ipad") || s.contains("ios")) {
            os = "ios";
        } else if (s.contains("mac os") || s.contains("macintosh")) {
            os = "macos";
        } else if (s.contains("android")) {
            os = "android";
        } else if (s.contains("linux")) {
            os = "linux";
        }
        String browser = "unknown";
        if (s.contains("edg")) {
            browser = "edge";
        } else if (s.contains("chrome") || s.contains("crios")) {
            browser = "chrome";
        } else if (s.contains("firefox") || s.contains("fxios")) {
            browser = "firefox";
        } else if (s.contains("safari")) {
            browser = "safari";
        }
        return os + "/" + browser;
    }

    /**
     * 拼接异常标记为逗号分隔串;空则返回 {@code null}(落库存 NULL)。
     *
     * @param flags 异常标记列表
     * @return 逗号分隔串或 {@code null}
     */
    private String join(List<String> flags) {
        return flags.isEmpty() ? null : String.join(",", flags);
    }
}
