package com.auth.center.service.impl;

import com.auth.center.entity.AiContentSignal;
import com.auth.center.entity.AiGeneration;
import com.auth.center.entity.AiTrustLog;
import com.auth.center.mapper.AiContentSignalMapper;
import com.auth.center.mapper.AiGenerationMapper;
import com.auth.center.mapper.AiTrustLogMapper;
import com.auth.center.service.IAiTrustLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** {@link IAiTrustLogService} 默认实现。 */
@Service
public class AiTrustLogServiceImpl implements IAiTrustLogService {

    /** 拦截标记值。 */
    private static final int BLOCKED = 1;

    /** 每页条数上限。 */
    private static final int MAX_PAGE_SIZE = 200;

    /** 单轮调用数上限 —— 超出丢弃。防的是循环失控把一轮写成几千行。 */
    private static final int MAX_GENERATIONS = 200;

    /** 单轮信号数上限。 */
    private static final int MAX_SIGNALS = 200;

    private final AiTrustLogMapper trustMapper;
    private final AiGenerationMapper generationMapper;
    private final AiContentSignalMapper signalMapper;

    /**
     * 构造注入。
     *
     * @param trustMapper 轮级 Mapper
     * @param generationMapper 调用级 Mapper
     * @param signalMapper 质量信号 Mapper
     */
    public AiTrustLogServiceImpl(
            AiTrustLogMapper trustMapper,
            AiGenerationMapper generationMapper,
            AiContentSignalMapper signalMapper) {
        this.trustMapper = trustMapper;
        this.generationMapper = generationMapper;
        this.signalMapper = signalMapper;
    }

    @Override
    @Transactional
    public void record(
            AiTrustLog log, List<AiGeneration> generations, List<AiContentSignal> signals) {
        trustMapper.insert(log);
        if (generations != null && !generations.isEmpty()) {
            generationMapper.insertBatch(cap(generations, MAX_GENERATIONS));
        }
        if (signals != null && !signals.isEmpty()) {
            signalMapper.insertBatch(cap(signals, MAX_SIGNALS));
        }
    }

    @Override
    public List<AiGeneration> generationsOf(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return List.of();
        }
        return generationMapper.selectList(
                new LambdaQueryWrapper<AiGeneration>()
                        .eq(AiGeneration::getRequestId, requestId)
                        .orderByAsc(AiGeneration::getSeq));
    }

    @Override
    public List<AiContentSignal> signalsOf(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return List.of();
        }
        return signalMapper.selectList(
                new LambdaQueryWrapper<AiContentSignal>()
                        .eq(AiContentSignal::getRequestId, requestId)
                        .orderByAsc(AiContentSignal::getId));
    }

    /** 截到上限;不截的话一次失控的循环就能把这张表撑坏。 */
    private static <T> List<T> cap(List<T> rows, int max) {
        return rows.size() <= max ? rows : rows.subList(0, max);
    }

    @Override
    public List<AiTrustLog> query(
            String agentKey,
            String source,
            String systemCode,
            Boolean blockedOnly,
            int page,
            int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 1);
        long offset = (long) (safePage - 1) * safeSize;

        LambdaQueryWrapper<AiTrustLog> wrapper =
                buildWrapper(agentKey, source, systemCode, blockedOnly)
                        .orderByDesc(AiTrustLog::getCreatedAt)
                        .last("LIMIT " + offset + ", " + safeSize);
        return trustMapper.selectList(wrapper);
    }

    @Override
    public long count(String agentKey, String source, String systemCode, Boolean blockedOnly) {
        return trustMapper.selectCount(buildWrapper(agentKey, source, systemCode, blockedOnly));
    }

    /**
     * 构造过滤条件(query 与 count 共用)。
     *
     * @param agentKey 智能体 key 模糊过滤(可空)
     * @param source 上报方精确过滤(可空)
     * @param systemCode 产品精确过滤(可空);历史行 system_code 为空,按产品筛时不出现
     * @param blockedOnly 仅看被拦截的(可空)
     * @return 组装好的查询包装器
     */
    private LambdaQueryWrapper<AiTrustLog> buildWrapper(
            String agentKey, String source, String systemCode, Boolean blockedOnly) {
        LambdaQueryWrapper<AiTrustLog> wrapper = new LambdaQueryWrapper<>();
        if (agentKey != null && !agentKey.isBlank()) {
            wrapper.like(AiTrustLog::getAgentKey, agentKey.trim());
        }
        if (source != null && !source.isBlank()) {
            wrapper.eq(AiTrustLog::getSource, source.trim());
        }
        if (systemCode != null && !systemCode.isBlank()) {
            wrapper.eq(AiTrustLog::getSystemCode, systemCode.trim());
        }
        if (Boolean.TRUE.equals(blockedOnly)) {
            wrapper.eq(AiTrustLog::getBlocked, BLOCKED);
        }
        return wrapper;
    }
}
