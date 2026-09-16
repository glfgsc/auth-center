package com.auth.center.security;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Refresh Token 族谱管理服务 — 基于 Redis 实现 token rotation 和 replay 检测.
 *
 *   - 每次登录创建一个 token family (familyId)
 *   - 每次 refresh 标记旧 token JTI 为已使用,签发新 token 对
 *   - 若已使用的 refresh token 被重放 → 整个 family 失效 (泄露检测)
 *
 * Redis Key 结构:{@code auth:rt:family:{familyId}} = "active"(family 存活标记);{@code
 * auth:rt:jti:{jti}} = familyId(JTI→family 映射,当前有效);{@code auth:rt:used:{jti}} = "1"(已消费的 JTI,用于
 * replay 检测)。
 */
@Component
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final String FAMILY_KEY_PREFIX = "auth:rt:family:";
    private static final String JTI_KEY_PREFIX = "auth:rt:jti:";
    private static final String USED_KEY_PREFIX = "auth:rt:used:";

    /**
     * 原子化 rotation + replay 检测 Lua 脚本 —— 在 Redis 端单步完成「replay 检查 / family 存活检查 / 消费旧 JTI / 注册新
     * JTI」,消除非原子 check-then-act 竞态(并发刷新曾可凭同一 oldJti 兑出多对有效令牌且不触发泄露告警)。
     *
     * KEYS[1]=used:oldJti，KEYS[2]=jti:oldJti，KEYS[3]=jti:newJti；ARGV[1]=ttlMs，ARGV[2]=family key
     * 前缀。返回:{@code REPLAY:<familyId>}(重放,调用方销毁该 family)/ {@code INVALID}(无效或 family 已失效)/ {@code
     * <familyId>}(rotation 成功)。
     */
    private static final RedisScript<String> ROTATE_SCRIPT =
            new DefaultRedisScript<>(
                    "if redis.call('EXISTS', KEYS[1]) == 1 then\n"
                            + "  return 'REPLAY:' .. (redis.call('GET', KEYS[1]) or '')\n"
                            + "end\n"
                            + "local familyId = redis.call('GET', KEYS[2])\n"
                            + "if not familyId then return 'INVALID' end\n"
                            + "if redis.call('EXISTS', ARGV[2] .. familyId) == 0 then return 'INVALID' end\n"
                            + "redis.call('DEL', KEYS[2])\n"
                            + "redis.call('PSETEX', KEYS[1], ARGV[1], familyId)\n"
                            + "redis.call('PSETEX', KEYS[3], ARGV[1], familyId)\n"
                            + "return familyId",
                    String.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 构造函数.
     *
     * @param redisTemplate Redis 模板
     */
    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 创建新的 token family — 登录时调用.
     *
     * @param jti 初始 refresh token 的 JTI
     * @param ttlMs family 存活时间 (应与 refresh token TTL 一致)
     * @return familyId
     */
    public String createFamily(String jti, long ttlMs) {
        String familyId = UUID.randomUUID().toString();
        redisTemplate
                .opsForValue()
                .set(FAMILY_KEY_PREFIX + familyId, "active", ttlMs, TimeUnit.MILLISECONDS);
        redisTemplate
                .opsForValue()
                .set(JTI_KEY_PREFIX + jti, familyId, ttlMs, TimeUnit.MILLISECONDS);
        return familyId;
    }

    /**
     * 消费 refresh token 并执行 rotation — 返回 familyId 或 null(无效/被盗).
     *
     *   - JTI 在 used 集合中 → replay attack, 销毁整个 family, 返回 null
     *   - JTI 不在 jti→family 映射中 → 无效 token, 返回 null
     *   - family 已失效 → 返回 null
     *   - 正常:标记旧 JTI 为 used, 注册新 JTI, 返回 familyId
     *
     * @param oldJti 当前 refresh token 的 JTI
     * @param newJti 新签发的 refresh token 的 JTI
     * @param ttlMs 剩余 TTL
     * @return familyId (rotation 成功) 或 null (无效/被盗)
     */
    public String rotate(String oldJti, String newJti, long ttlMs) {
        // 单步原子执行 replay 检测 + rotation。拆成多条 Redis 命令会留下 check-then-act 窗口,
        // 两个携同一有效 oldJti 的并发刷新就能都通过检查、各自兑出有效令牌对。
        String result =
                redisTemplate.execute(
                        ROTATE_SCRIPT,
                        List.of(
                                USED_KEY_PREFIX + oldJti,
                                JTI_KEY_PREFIX + oldJti,
                                JTI_KEY_PREFIX + newJti),
                        String.valueOf(ttlMs),
                        FAMILY_KEY_PREFIX);

        if (result == null || "INVALID".equals(result)) {
            return null;
        }
        // Replay detection: 已消费的 token 被重用 → 销毁整个 family(泄露检测)
        if (result.startsWith("REPLAY:")) {
            String familyId = result.substring("REPLAY:".length());
            if (!familyId.isEmpty()) {
                invalidateFamily(familyId);
                log.warn(
                        "[Security] Refresh token replay detected! familyId={}, reusedJti={}",
                        familyId,
                        oldJti);
            }
            return null;
        }
        return result;
    }

    /**
     * 查 JTI 归属的 family —— 供 rotation 失败后回答「这条会话是被吊销了,还是本就不存在」.
     *
     * {@link #invalidateFamily} 只删 family 键、不动 jti→family 映射,故会话被顶下线后仍能据此认出是哪条会话,进而取到吊销原因码回给前端。
     *
     * @param jti refresh token 的 JTI
     * @return familyId;映射不存在(令牌已消费 / 已过期)返回 {@code null}
     */
    public String familyOf(String jti) {
        return redisTemplate.opsForValue().get(JTI_KEY_PREFIX + jti);
    }

    /**
     * 销毁整个 token family — 所有属于该 family 的 refresh token 立即失效.
     *
     * @param familyId family 标识
     */
    public void invalidateFamily(String familyId) {
        redisTemplate.delete(FAMILY_KEY_PREFIX + familyId);
        log.info("[Security] Token family invalidated: {}", familyId);
    }

    /**
     * 注销时销毁指定 JTI 对应的整个 family.
     *
     * @param jti refresh token 的 JTI
     */
    public void invalidateByJti(String jti) {
        String familyId = redisTemplate.opsForValue().get(JTI_KEY_PREFIX + jti);
        if (familyId != null) {
            invalidateFamily(familyId);
        }
        redisTemplate.delete(JTI_KEY_PREFIX + jti);
    }
}
