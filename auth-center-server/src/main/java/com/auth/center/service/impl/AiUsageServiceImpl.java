package com.auth.center.service.impl;

import com.auth.center.mapper.AiTrustLogMapper;
import com.auth.center.service.IAiUsageService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** {@link IAiUsageService} 默认实现。 */
@Service
public class AiUsageServiceImpl implements IAiUsageService {

    /**
     * 时间桶的 SQL 表达式白名单。
     *
     * 这两个串是唯一会以 {@code ${}} 拼进 SQL 的片段,故只能由此处的枚举映射产生,绝不接受调用方原文 —— 请求参数只用来选枚举,选不中就落到默认值。
     */
    private static final String BUCKET_HOUR = "DATE_FORMAT(created_at, '%Y-%m-%d %H:00')";

    private static final String BUCKET_DAY = "DATE_FORMAT(created_at, '%Y-%m-%d')";

    private final AiTrustLogMapper trustMapper;

    /**
     * 构造注入。
     *
     * @param trustMapper AI 信任遥测 Mapper
     */
    public AiUsageServiceImpl(AiTrustLogMapper trustMapper) {
        this.trustMapper = trustMapper;
    }

    @Override
    public List<Map<String, Object>> summary(
            LocalDateTime from, LocalDateTime to, String systemCode) {
        return trustMapper.summarizeBySystem(from, to, blankToNull(systemCode));
    }

    @Override
    public List<Map<String, Object>> series(
            LocalDateTime from, LocalDateTime to, String systemCode, Bucket bucket) {
        String expr = bucket == Bucket.HOUR ? BUCKET_HOUR : BUCKET_DAY;
        return trustMapper.seriesByBucketAndModel(expr, from, to, blankToNull(systemCode));
    }

    @Override
    public UsageBreakdown breakdown(
            LocalDateTime from,
            LocalDateTime to,
            String systemCode,
            Dimension dimension,
            int offset,
            int limit) {
        String code = blankToNull(systemCode);
        return switch (dimension) {
                // 聚合后基数天然小,整份给;总数即行数,不另发一次计数查询。
            case MODEL -> whole(trustMapper.breakdownByModel(from, to, code));
            case AGENT -> whole(trustMapper.breakdownByAgent(from, to, code));
            case USER ->
                    new UsageBreakdown(
                            trustMapper.breakdownByUser(from, to, code, offset, limit),
                            trustMapper.countBreakdownByUser(from, to, code));
            case TURN ->
                    new UsageBreakdown(
                            trustMapper.breakdownByTurn(from, to, code, offset, limit),
                            trustMapper.countBreakdownByTurn(from, to, code));
        };
    }

    /**
     * 不分页的维度:整份即一页,总数就是行数。
     *
     * @param rows 明细行
     * @return 一页
     */
    private static UsageBreakdown whole(List<Map<String, Object>> rows) {
        return new UsageBreakdown(rows, rows.size());
    }

    @Override
    public List<Map<String, Object>> turnByRequestId(String requestId) {
        String id = blankToNull(requestId);
        return id == null ? List.of() : trustMapper.turnByRequestId(id);
    }

    /**
     * 空白串归一为 {@code null} —— Mapper 侧以 {@code null} 判定「不加这个过滤」,前端传空字符串表示「全部产品」,不归一会变成过滤出
     * system_code='' 的空结果。
     *
     * @param value 原始值
     * @return 去空白后的值;空白或 {@code null} 返回 {@code null}
     */
    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
