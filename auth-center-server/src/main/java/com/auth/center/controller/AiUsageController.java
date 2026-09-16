package com.auth.center.controller;

import com.auth.center.common.Result;
import com.auth.center.service.IAiUsageService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型用量查询端点 —— 管理员控制台的「模型用量」看板。
 *
 * 三个端点对应看板三层:{@code /summary} 顶部分产品卡片、{@code /series} 中部按时间与模型的堆叠序列、 {@code /breakdown}
 * 底部按用户/模型/智能体的明细下钻。数据源是 AI 信任遥测(所有 LLM 调用的统一留痕), 与逐条查询的 {@link AiTrustController} 同表不同问法。
 *
 * 仅管理员可读:用量能反推出「谁在用、用得多凶」,属组织级敏感信息。
 */
@RestController
@RequestMapping("/api/auth/admin/audit/usage")
@PreAuthorize("@authPerm.isAdmin()")
public class AiUsageController {

    /** 未指定时间范围时回看的天数。 */
    private static final int DEFAULT_LOOKBACK_DAYS = 7;

    /** 超过这个跨度就按天聚合 —— 小时桶在长跨度下会产生几百根柱子,读不出趋势。 */
    private static final int HOUR_BUCKET_MAX_DAYS = 2;

    /** 明细每页条数上限 —— 与会话/登录历史同口径,挡住 size 给个大数把分页绕过去。 */
    private static final int MAX_PAGE_SIZE = 200;

    private final IAiUsageService usageService;

    /**
     * 构造注入。
     *
     * @param usageService 用量统计服务
     */
    public AiUsageController(IAiUsageService usageService) {
        this.usageService = usageService;
    }

    /**
     * 分产品用量汇总(看板顶部卡片)。
     *
     * @param from 起始日期 {@code yyyy-MM-dd},空则回看 7 天
     * @param to 截止日期 {@code yyyy-MM-dd}(含当天),空则至今天
     * @param systemCode 产品过滤;空则全部。三个端点必须都认它 —— 少一个,看板就会一半按筛选、一半不按,卡片报着甲产品的数而底下的趋势与明细说没有记录。
     * @return {@code {records, from, to}}
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String systemCode) {
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", usageService.summary(start, end, systemCode));
        data.put("from", start.toString());
        data.put("to", end.toString());
        return Result.ok(data);
    }

    /**
     * 按时间桶 × 模型的用量序列(看板中部堆叠图)。
     *
     * 桶粒度由跨度自动决定而非交给调用方:长跨度配小时桶会画出几百根柱子,既慢又读不出趋势。
     *
     * @param from 起始日期,空则回看 7 天
     * @param to 截止日期(含当天),空则至今天
     * @param systemCode 产品过滤;空则全部
     * @return {@code {records, bucket}};records 为 {@code {bucket, model, inputTokens,
     *     cachedInputTokens, totalTokens}}
     */
    @GetMapping("/series")
    public Result<Map<String, Object>> series(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String systemCode) {
        LocalDateTime start = startOf(from);
        LocalDateTime end = endOf(to);
        IAiUsageService.Bucket bucket =
                start.plusDays(HOUR_BUCKET_MAX_DAYS).isAfter(end)
                        ? IAiUsageService.Bucket.HOUR
                        : IAiUsageService.Bucket.DAY;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", usageService.series(start, end, systemCode, bucket));
        data.put("bucket", bucket.name().toLowerCase(Locale.ROOT));
        return Result.ok(data);
    }

    /**
     * 按维度的用量明细(看板底部下钻表)。
     *
     * @param dimension 下钻维度 {@code user} / {@code model} / {@code agent} / {@code turn};缺省 {@code
     *     user}。{@code turn} 不聚合,带服务商请求 ID,供逐条对账
     * @param from 起始日期,空则回看 7 天
     * @param to 截止日期(含当天),空则至今天
     * @param systemCode 产品过滤;空则全部
     * @param requestId 按请求 id(= 产品侧该轮 turn_id)精确取一轮 —— 产品侧深链入口。给了它就无视时间窗与其余过滤,维度强制 {@code
     *     turn}:深链只带对账键,别让缺省 7 天窗把老轮次挡在外面
     * @param page 页码,默认 1
     * @param size 每页条数,默认 20
     * @return {@code {records, dimension, total, page, size}}
     */
    @GetMapping("/breakdown")
    public Result<Map<String, Object>> breakdown(
            @RequestParam(defaultValue = "user") String dimension,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IAiUsageService.Dimension dim = parseDimension(dimension);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        List<Map<String, Object>> records;
        long total;
        if (requestId != null && !requestId.isBlank()) {
            // 深链只定位一轮,本来就是一页,不套分页。
            records = usageService.turnByRequestId(requestId);
            total = records.size();
            dim = IAiUsageService.Dimension.TURN;
        } else {
            IAiUsageService.UsageBreakdown result =
                    usageService.breakdown(
                            startOf(from),
                            endOf(to),
                            systemCode,
                            dim,
                            (safePage - 1) * safeSize,
                            safeSize);
            records = result.records();
            total = result.total();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", records);
        data.put("dimension", dim.name().toLowerCase(Locale.ROOT));
        data.put("total", total);
        data.put("page", safePage);
        data.put("size", safeSize);
        return Result.ok(data);
    }

    /**
     * 解析下钻维度;认不出的一律回落到用户维度(而非报错 —— 看板缺省视图不该因一个拼错的参数白屏)。
     *
     * @param raw 请求参数
     * @return 维度枚举
     */
    private static IAiUsageService.Dimension parseDimension(String raw) {
        if (raw == null) {
            return IAiUsageService.Dimension.USER;
        }
        try {
            return IAiUsageService.Dimension.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return IAiUsageService.Dimension.USER;
        }
    }

    /**
     * 起始时间 —— 当日零点;解析不出则回看默认天数。
     *
     * @param raw {@code yyyy-MM-dd}
     * @return 起始时刻
     */
    private static LocalDateTime startOf(String raw) {
        LocalDate date = parseDate(raw);
        return date != null
                ? date.atStartOfDay()
                : LocalDate.now().minusDays(DEFAULT_LOOKBACK_DAYS).atStartOfDay();
    }

    /**
     * 截止时间 —— 次日零点(即「含当天」的开区间上界);解析不出则至今天。
     *
     * @param raw {@code yyyy-MM-dd}
     * @return 截止时刻(不含)
     */
    private static LocalDateTime endOf(String raw) {
        LocalDate date = parseDate(raw);
        return (date != null ? date : LocalDate.now()).plusDays(1).atStartOfDay();
    }

    /**
     * 宽松解析日期 —— 空或非法返回 {@code null},由调用方套默认值。
     *
     * @param raw 原始串
     * @return 日期或 {@code null}
     */
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
