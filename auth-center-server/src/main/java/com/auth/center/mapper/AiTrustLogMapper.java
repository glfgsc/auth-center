package com.auth.center.mapper;

import com.auth.center.entity.AiTrustLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * AI 信任层遥测 Mapper —— 下面几个聚合查询支撑「大模型用量」看板的三层:分产品卡片 → 按时间与模型的堆叠序列 → 按用户 / 模型 / 智能体的明细下钻。
 *
 * 各处共同的口径约定,改动前务必对齐:
 *
 *   - 合计恒为输入+输出。{@code reasoning_tokens} 是输出的子集、{@code cached_input_tokens} 是输入的子集,
 *       相加会得出一个和账单对不上的数,故它们只作明细列出、不进合计。
 *   - {@code SUM} 对全 NULL 分组返回 NULL,故一律 {@code COALESCE} 兜 0 —— 前端拿到 null 会渲染成空白格,而
 *       这里的语义是「这批里没有一条采到用量」,0 是对的。这与存储侧的约定不冲突:落库仍留 NULL 以区分「没采到」和「真的是 0」,只有聚合出口才兜 0。
 *   - 产品 / 模型为空的行归入 {@code unknown} 而不是丢弃 —— 丢了会让各分组之和对不上总量,而「有一批调用不知道该算谁头上」本身就是要被看见的问题。
 */
@Mapper
public interface AiTrustLogMapper extends BaseMapper<AiTrustLog> {

    /**
     * 分产品用量汇总 —— 看板顶部的产品卡片。
     *
     * 产品过滤与 {@link #seriesByBucketAndModel} / {@link #breakdownBy} 同款:同一块看板的三层若只有
     * 两层认这个条件,页面就会自相矛盾 —— 卡片报着甲产品的数,底下的趋势与明细却说这段时间没有记录。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 每个产品一行:轮次 / 模型调用次数 / 各类 token / 去重用户数
     */
    @Select(
            "<script>"
                    + "SELECT COALESCE(system_code, 'unknown') AS systemCode,"
                    + " COUNT(*) AS turns,"
                    + " COALESCE(SUM(llm_call_count), 0) AS llmCalls,"
                    + " COALESCE(SUM(input_tokens), 0) AS inputTokens,"
                    + " COALESCE(SUM(output_tokens), 0) AS outputTokens,"
                    + " COALESCE(SUM(reasoning_tokens), 0) AS reasoningTokens,"
                    + " COALESCE(SUM(cached_input_tokens), 0) AS cachedInputTokens,"
                    + " COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS totalTokens,"
                    + " COUNT(DISTINCT user_id) AS users"
                    + " FROM auth_ai_trust_log"
                    + " WHERE created_at &gt;= #{from} AND created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND system_code = #{systemCode}</if>"
                    + " GROUP BY COALESCE(system_code, 'unknown')"
                    + " ORDER BY COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) DESC"
                    + "</script>")
    List<Map<String, Object>> summarizeBySystem(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);

    /**
     * 按时间桶 × 模型的用量序列 —— 看板中部的堆叠柱状图(一根柱 = 一个时间桶,分段 = 模型)。
     *
     * {@code bucketExpr} 直接进 SQL,故服务层只从白名单取值,绝不透传调用方原文。
     *
     * 输入与缓存命中随合计一起给:缓存命中率 = 命中 / 输入,分子分母都得在同一格里,前端才能按桶算;除法不在 SQL 做(空桶除零)。
     *
     * @param bucketExpr 时间桶表达式(白名单值)
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return {@code {bucket, model, inputTokens, cachedInputTokens, totalTokens}} 行集,按桶升序
     */
    @Select(
            "<script>"
                    + "SELECT ${bucketExpr} AS bucket,"
                    + " COALESCE(model, 'unknown') AS model,"
                    + " COALESCE(SUM(input_tokens), 0) AS inputTokens,"
                    + " COALESCE(SUM(cached_input_tokens), 0) AS cachedInputTokens,"
                    + " COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS totalTokens"
                    + " FROM auth_ai_trust_log"
                    + " WHERE created_at &gt;= #{from} AND created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND system_code = #{systemCode}</if>"
                    + " GROUP BY ${bucketExpr}, COALESCE(model, 'unknown')"
                    + " ORDER BY bucket"
                    + "</script>")
    List<Map<String, Object>> seriesByBucketAndModel(
            @Param("bucketExpr") String bucketExpr,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);

    /**
     * 按用户的用量明细 —— 下钻表。左连用户表取用户名;用户已删除则用户名为空,由前端回退显示 ID。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @param offset 起始偏移
     * @param limit 本页条数
     * @return 每个用户一行,按合计降序
     */
    @Select(
            "<script>"
                    + "SELECT t.user_id AS userId, u.username AS username, u.nickname AS nickname,"
                    + " COUNT(*) AS turns,"
                    + " COALESCE(SUM(t.llm_call_count), 0) AS llmCalls,"
                    + " COALESCE(SUM(t.input_tokens), 0) AS inputTokens,"
                    + " COALESCE(SUM(t.output_tokens), 0) AS outputTokens,"
                    + " COALESCE(SUM(t.cached_input_tokens), 0) AS cachedInputTokens,"
                    + " COALESCE(SUM(t.input_tokens), 0) + COALESCE(SUM(t.output_tokens), 0) AS totalTokens"
                    + " FROM auth_ai_trust_log t"
                    + " LEFT JOIN auth_user u ON u.id = t.user_id"
                    + " WHERE t.created_at &gt;= #{from} AND t.created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND t.system_code = #{systemCode}</if>"
                    + " GROUP BY t.user_id, u.username, u.nickname"
                    + " ORDER BY totalTokens DESC"
                    + " LIMIT #{limit} OFFSET #{offset}"
                    + "</script>")
    List<Map<String, Object>> breakdownByUser(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 用户维度的命中总数(= 分组数),供分页器。
     *
     * WHERE 与分组必须与 {@link #breakdownByUser} 逐字一致 —— 两处条件一旦漂移,总数与实际能翻到的页数就对不上。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 用户数
     */
    @Select(
            "<script>"
                    + "SELECT COUNT(*) FROM (SELECT t.user_id"
                    + " FROM auth_ai_trust_log t"
                    + " LEFT JOIN auth_user u ON u.id = t.user_id"
                    + " WHERE t.created_at &gt;= #{from} AND t.created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND t.system_code = #{systemCode}</if>"
                    + " GROUP BY t.user_id, u.username, u.nickname) x"
                    + "</script>")
    long countBreakdownByUser(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);

    /**
     * 按模型的用量明细 —— 下钻表。服务商与模型一并分组:换服务商后同名模型不会被混成一行。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 每个「服务商 + 模型」一行,按合计降序
     */
    @Select(
            "<script>"
                    + "SELECT COALESCE(provider, 'unknown') AS provider,"
                    + " COALESCE(model, 'unknown') AS model,"
                    + " COUNT(*) AS turns,"
                    + " COALESCE(SUM(llm_call_count), 0) AS llmCalls,"
                    + " COALESCE(SUM(input_tokens), 0) AS inputTokens,"
                    + " COALESCE(SUM(output_tokens), 0) AS outputTokens,"
                    + " COALESCE(SUM(reasoning_tokens), 0) AS reasoningTokens,"
                    + " COALESCE(SUM(cached_input_tokens), 0) AS cachedInputTokens,"
                    + " COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS totalTokens"
                    + " FROM auth_ai_trust_log"
                    + " WHERE created_at &gt;= #{from} AND created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND system_code = #{systemCode}</if>"
                    + " GROUP BY COALESCE(provider, 'unknown'), COALESCE(model, 'unknown')"
                    + " ORDER BY totalTokens DESC"
                    + "</script>")
    List<Map<String, Object>> breakdownByModel(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);

    /** 逐轮明细的 SELECT 列(与 {@link #turnByRequestId} 共用 —— 深链取的单行必须和列表行同形)。 */
    String TURN_COLUMNS =
            "SELECT t.request_id AS requestId, t.session_id AS sessionId, t.created_at AS createdAt,"
                    + " COALESCE(t.system_code, 'unknown') AS systemCode,"
                    + " t.user_id AS userId, u.username AS username, u.nickname AS nickname,"
                    + " COALESCE(t.agent_key, 'unknown') AS agentKey,"
                    + " COALESCE(t.provider, 'unknown') AS provider,"
                    + " COALESCE(t.model, 'unknown') AS model,"
                    + " COALESCE(t.input_tokens, 0) AS inputTokens,"
                    + " COALESCE(t.output_tokens, 0) AS outputTokens,"
                    + " COALESCE(t.cached_input_tokens, 0) AS cachedInputTokens,"
                    + " COALESCE(t.input_tokens, 0) + COALESCE(t.output_tokens, 0) AS totalTokens,"
                    + " t.llm_call_count AS llmCalls,"
                    + " t.llm_http_attempts AS httpAttempts,"
                    + " t.llm_failed_attempts AS failedAttempts,"
                    // 逐次归属与服务商请求 ID 不在这里取:它们已行化到 auth_ai_generation,
                    // 由前端按 requestId 展开时另取(一轮十几次调用、每次可能带全文,
                    // 塞进列表会让一页几十行变成几兆)。
                    + " t.latency_ms AS latencyMs"
                    + " FROM auth_ai_trust_log t"
                    + " LEFT JOIN auth_user u ON u.id = t.user_id";

    /**
     * 逐轮明细 —— 下钻的最细一层,不聚合。
     *
     * 其余维度回答「谁花的」,这一层回答「具体哪一次」:带上出网次数,也是唯一能看出「出网 &gt; 已记账」这类少报的地方 ——
     * 聚合之后差值就被摊平了。服务商请求 ID 逐次挂在 {@code auth_ai_generation} 上,展开某一轮时另取。
     *
     * 只取有用量的轮次:没采到用量的行在这张表里全是空格。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @param offset 起始偏移
     * @param limit 本页条数
     * @return 每轮一行,按时间倒序
     */
    @Select(
            "<script>"
                    + TURN_COLUMNS
                    + " WHERE t.created_at &gt;= #{from} AND t.created_at &lt; #{to}"
                    + " AND (t.input_tokens IS NOT NULL OR t.output_tokens IS NOT NULL)"
                    + "<if test='systemCode != null'> AND t.system_code = #{systemCode}</if>"
                    + " ORDER BY t.created_at DESC"
                    + " LIMIT #{limit} OFFSET #{offset}"
                    + "</script>")
    List<Map<String, Object>> breakdownByTurn(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode,
            @Param("offset") int offset,
            @Param("limit") int limit);

    /**
     * 逐轮维度的命中总数,供分页器。
     *
     * WHERE 必须与 {@link #breakdownByTurn} 逐字一致,「只取有用量的轮次」那条也要带上 ——
     * 漏了它总数会比实际能翻到的多出一截(没采到用量的行并不在列表里)。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 轮次数
     */
    @Select(
            "<script>"
                    + "SELECT COUNT(*) FROM auth_ai_trust_log t"
                    + " WHERE t.created_at &gt;= #{from} AND t.created_at &lt; #{to}"
                    + " AND (t.input_tokens IS NOT NULL OR t.output_tokens IS NOT NULL)"
                    + "<if test='systemCode != null'> AND t.system_code = #{systemCode}</if>"
                    + "</script>")
    long countBreakdownByTurn(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);

    /**
     * 按请求 ID 精确取一轮 —— 产品侧深链的反向入口。
     *
     * 不设时间窗、不过滤用量非空:深链只带对账键,时间由行自己说;被拦/失败的轮次没 token 但行存在,如实给出。{@code LIMIT 5}
     * 只是防脏数据兜底,request_id 正常唯一。
     *
     * @param requestId 请求 id(= 产品侧该轮 turn_id)
     * @return 命中行集(正常至多一行)
     */
    @Select(TURN_COLUMNS + " WHERE t.request_id = #{requestId} ORDER BY t.created_at DESC LIMIT 5")
    List<Map<String, Object>> turnByRequestId(@Param("requestId") String requestId);

    /**
     * 按智能体的用量明细 —— 下钻表。
     *
     * @param from 起始时间(含)
     * @param to 截止时间(不含)
     * @param systemCode 产品过滤;空则不限
     * @return 每个智能体一行,按合计降序
     */
    @Select(
            "<script>"
                    + "SELECT COALESCE(agent_key, 'unknown') AS agentKey,"
                    + " COUNT(*) AS turns,"
                    + " COALESCE(SUM(llm_call_count), 0) AS llmCalls,"
                    + " COALESCE(SUM(input_tokens), 0) AS inputTokens,"
                    + " COALESCE(SUM(output_tokens), 0) AS outputTokens,"
                    + " COALESCE(SUM(cached_input_tokens), 0) AS cachedInputTokens,"
                    + " COALESCE(SUM(input_tokens), 0) + COALESCE(SUM(output_tokens), 0) AS totalTokens"
                    + " FROM auth_ai_trust_log"
                    + " WHERE created_at &gt;= #{from} AND created_at &lt; #{to}"
                    + "<if test='systemCode != null'> AND system_code = #{systemCode}</if>"
                    + " GROUP BY COALESCE(agent_key, 'unknown')"
                    + " ORDER BY totalTokens DESC"
                    + "</script>")
    List<Map<String, Object>> breakdownByAgent(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("systemCode") String systemCode);
}
