package com.auth.center.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 大模型用量统计 —— 从 AI 信任遥测(所有 LLM 调用的统一留痕)派生的读模型。
 *
 * 与 {@link IAiTrustLogService} 分开:那个是逐条留痕的读写(合规视角,「哪个模型看过这段数据」),
 * 这个是聚合统计(成本视角,「哪个产品、哪个用户花了多少」)。同一份底表,两种问法。
 */
public interface IAiUsageService {

    /** 时间桶粒度。 */
    enum Bucket {
        /** 按小时聚合 —— 看当天分布。 */
        HOUR,
        /** 按天聚合 —— 看跨周/月趋势。 */
        DAY
    }

    /** 下钻维度。 */
    enum Dimension {
        /** 按用户。 */
        USER,
        /** 按模型(含服务商)。 */
        MODEL,
        /** 按智能体。 */
        AGENT,
        /** 逐轮(不聚合)—— 带服务商请求 ID,是对账与看少报的唯一一层。 */
        TURN
    }

    /**
     * 分产品用量汇总。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 每个产品一行
     */
    List<Map<String, Object>> summary(LocalDateTime from, LocalDateTime to, String systemCode);

    /**
     * 按时间桶 × 模型的用量序列。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @param bucket 时间桶粒度
     * @return {@code {bucket, model, inputTokens, cachedInputTokens, totalTokens}} 行集
     */
    List<Map<String, Object>> series(
            LocalDateTime from, LocalDateTime to, String systemCode, Bucket bucket);

    /**
     * 用量明细的一页 —— 本页行 + 该过滤条件下的命中总数。
     *
     * 总数与行一起返回而不是各调一次:两者必须出自同一组过滤条件,分开取容易在改条件时只改一边,表现为「总数说有 300 条,翻到第 3 页却空了」。
     *
     * @param records 本页明细行
     * @param total 命中总数;不分页的维度即为返回行数
     */
    record UsageBreakdown(List<Map<String, Object>> records, long total) {}

    /**
     * 按指定维度的用量明细(带命中总数)。
     *
     * 分页只对会超出一屏的两个维度生效:{@code USER}(用户基数可能很大)与 {@code TURN}(不聚合,一天就可能上千轮)按 {@code
     * offset}/{@code limit} 取该页,{@code total} 是真实命中数。{@code MODEL} / {@code AGENT} 聚合后
     * 基数天然小(至多「服务商×模型」「智能体」各一行),整份返回,{@code offset}/{@code limit} 不生效,{@code total} 即返回行数。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @param dimension 下钻维度
     * @param offset 起始偏移(仅分页维度生效)
     * @param limit 本页条数(仅分页维度生效)
     * @return 本页明细与命中总数
     */
    UsageBreakdown breakdown(
            LocalDateTime from,
            LocalDateTime to,
            String systemCode,
            Dimension dimension,
            int offset,
            int limit);

    /**
     * 按请求 ID 精确取一轮的用量明细(逐轮维度行,同 {@link Dimension#TURN} 的列形状)。
     *
     * 供产品侧「从会话回放跳到 token 账本」的深链使用:深链只带对账键,不带时间范围,故这条查询不设时间窗、也不要求用量非空 —— 被拦截/失败的轮次没有 token,但那一行
     * 本身存在,如实给出比「查无此轮」诚实。
     *
     * @param requestId 请求 id(= 产品侧该轮 turn_id)
     * @return 命中行集(正常至多一行;空 = 该轮无遥测留痕)
     */
    List<Map<String, Object>> turnByRequestId(String requestId);
}
