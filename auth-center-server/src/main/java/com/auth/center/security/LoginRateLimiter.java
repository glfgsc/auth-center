package com.auth.center.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录频率限制器 -- 基于内存的暴力破解防护.
 *
 * <p>跟踪每个用户名的失败登录次数, 达到阈值后锁定该账户一段时间.
 * 登录成功后自动清除记录. 后台定时任务每小时清理过期条目.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);

    /** 最大允许失败次数 */
    private static final int MAX_ATTEMPTS = 5;

    /** 锁定时长 (毫秒): 15 分钟 */
    private static final long LOCK_DURATION_MS = 15L * 60 * 1000;

    /** 失败计数窗口 (毫秒): 1 小时 */
    private static final long ATTEMPT_WINDOW_MS = 60L * 60 * 1000;

    /** 定时清理间隔 (毫秒): 1 小时 */
    private static final long CLEANUP_INTERVAL_MS = 60L * 60 * 1000;

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    /**
     * 检查指定用户名是否处于锁定状态.
     *
     * @param username 用户名
     * @return {@code true} 表示当前已锁定, {@code false} 表示未锁定
     */
    public boolean isLocked(String username) {
        AttemptRecord record = attempts.get(username);
        if (record == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        // 锁定已过期, 清除记录
        if (record.lockUntil > 0 && record.lockUntil <= now) {
            attempts.remove(username);
            return false;
        }
        return record.lockUntil > now;
    }

    /**
     * 记录一次登录失败.
     *
     * <p>若失败次数达到 {@value #MAX_ATTEMPTS} 次, 将锁定该用户名 {@value #LOCK_DURATION_MS} 毫秒.
     *
     * @param username 用户名
     */
    public void recordFailure(String username) {
        long now = System.currentTimeMillis();
        attempts.compute(username, (key, existing) -> {
            if (existing == null || isWindowExpired(existing, now)) {
                // 新记录或窗口已过期, 重新开始计数
                AttemptRecord fresh = new AttemptRecord();
                fresh.failCount = 1;
                fresh.lastFailTime = now;
                fresh.lockUntil = 0;
                return fresh;
            }
            existing.failCount++;
            existing.lastFailTime = now;
            if (existing.failCount >= MAX_ATTEMPTS) {
                existing.lockUntil = now + LOCK_DURATION_MS;
                log.warn("用户 {} 连续失败 {} 次, 已锁定 {} 分钟",
                        username, existing.failCount, LOCK_DURATION_MS / 60000);
            }
            return existing;
        });
    }

    /**
     * 登录成功后清除该用户名的失败记录.
     *
     * @param username 用户名
     */
    public void recordSuccess(String username) {
        attempts.remove(username);
    }

    /**
     * 获取指定用户名的剩余锁定秒数.
     *
     * @param username 用户名
     * @return 剩余锁定秒数, 未锁定时返回 0
     */
    public long remainingLockSeconds(String username) {
        AttemptRecord record = attempts.get(username);
        if (record == null) {
            return 0;
        }
        long remaining = record.lockUntil - System.currentTimeMillis();
        return remaining > 0 ? remaining / 1000 : 0;
    }

    /**
     * 定时清理过期的失败记录, 每小时执行一次.
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MS)
    public void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, AttemptRecord>> it = attempts.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            Map.Entry<String, AttemptRecord> entry = it.next();
            AttemptRecord record = entry.getValue();
            // 清理: 锁定已过期 或 失败窗口已过期
            boolean lockExpired = record.lockUntil > 0 && record.lockUntil <= now;
            boolean windowExpired = isWindowExpired(record, now);
            if (lockExpired || windowExpired) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("登录限流器清理了 {} 条过期记录, 当前剩余 {} 条", removed, attempts.size());
        }
    }

    /**
     * 判断失败记录的时间窗口是否已过期.
     *
     * @param record 失败记录
     * @param now    当前时间戳
     * @return {@code true} 表示窗口已过期
     */
    private boolean isWindowExpired(AttemptRecord record, long now) {
        return (now - record.lastFailTime) > ATTEMPT_WINDOW_MS;
    }

    /**
     * 登录尝试记录 -- 存储单个用户名的失败信息.
     */
    private static class AttemptRecord {

        /** 失败次数 */
        int failCount;

        /** 最近一次失败时间戳 (毫秒) */
        long lastFailTime;

        /** 锁定截止时间戳 (毫秒), 0 表示未锁定 */
        long lockUntil;
    }
}
