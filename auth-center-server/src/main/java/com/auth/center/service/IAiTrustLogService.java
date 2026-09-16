package com.auth.center.service;

import com.auth.center.entity.AiContentSignal;
import com.auth.center.entity.AiGeneration;
import com.auth.center.entity.AiTrustLog;
import java.util.List;

/**
 * AI 信任层遥测服务 —— 写入与查询 agent 平台 LLM 调用的信任层留痕。
 *
 * 写入来源:agent-server 经 trust ingest 端点推送。查询供管理员审计控制台「AI 信任日志」板块按智能体 / 是否拦截 / 来源分页检索。活动审计是不同域,见
 * {@link IAuditLogService}。
 */
public interface IAiTrustLogService {

    /**
     * 记录一轮的 AI 信任遥测 —— 轮级那一行 + 逐次调用 + 逐条检测判定,一个事务。
     *
     * 三张表一起写而不是各写各的:它们描述的是同一轮,分开写就会出现「轮级有行、调用级没有」这种半截账,
     * 而读的人无从判断是没发生还是没写进来。
     *
     * @param log 轮级记录
     * @param generations 本轮各次模型调用(可空)
     * @param signals 本轮各检测器判定(可空)
     */
    void record(AiTrustLog log, List<AiGeneration> generations, List<AiContentSignal> signals);

    /**
     * 一轮的调用级明细(按调用顺序)。
     *
     * @param requestId 轮次标识
     * @return 该轮各次模型调用;无则空列表
     */
    List<AiGeneration> generationsOf(String requestId);

    /**
     * 一轮的检测判定。
     *
     * @param requestId 轮次标识
     * @return 该轮各条判定;无则空列表
     */
    List<AiContentSignal> signalsOf(String requestId);

    /**
     * 分页查询(按发生时间倒序)。
     *
     * @param agentKey 智能体 key 模糊过滤(可空)
     * @param source 上报方精确过滤(RUNTIME / BI_UTILITY,可空)—— 与 {@code systemCode} 是正交的两个维度
     * @param systemCode 产品精确过滤(可空);历史行未带产品标识,按产品筛时不出现
     * @param blockedOnly 仅看被拦截的(可空)
     * @param page 页码(从 1 起)
     * @param size 每页条数
     * @return 当前页记录
     */
    List<AiTrustLog> query(
            String agentKey,
            String source,
            String systemCode,
            Boolean blockedOnly,
            int page,
            int size);

    /**
     * 统计符合过滤条件的总条数。
     *
     * @param agentKey 智能体 key 模糊过滤(可空)
     * @param source 上报方精确过滤(可空)
     * @param systemCode 产品精确过滤(可空)
     * @param blockedOnly 仅看被拦截的(可空)
     * @return 总条数
     */
    long count(String agentKey, String source, String systemCode, Boolean blockedOnly);
}
