package com.auth.center.mapper;

import com.auth.center.entity.AiGeneration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 调用级审计 Mapper。 */
@Mapper
public interface AiGenerationMapper extends BaseMapper<AiGeneration> {

    /**
     * 整轮的调用一次写入。
     *
     * 一轮十几次调用逐行 insert 会把同样次数的往返塞进收尾段,而这段跑在答复已送达之后 —— 慢不至于让用户看见,
     * 但会拖着连接与线程不放。
     *
     * 主键冲突时覆盖:上报走的是可重试通道,重复到达必须幂等,否则同一次调用会在账上出现两次、把用量算重。
     *
     * @param rows 调用(非空)
     * @return 受影响行数
     */
    @Insert(
            """
            <script>
            INSERT INTO auth_ai_generation
              (generation_id, request_id, session_id, seq, system_code, user_id, source,
               provider, model, model_role, prompt_text, raw_response, text_retention,
               prompt_chars, response_chars, input_tokens, output_tokens, reasoning_tokens,
               cached_input_tokens, provider_request_id, streamed, latency_ms, finish_reason,
               error_type, step_id, created_at)
            VALUES
            <foreach collection="rows" item="r" separator=",">
              (#{r.generationId}, #{r.requestId}, #{r.sessionId}, #{r.seq}, #{r.systemCode},
               #{r.userId}, #{r.source}, #{r.provider}, #{r.model}, #{r.modelRole},
               #{r.promptText}, #{r.rawResponse}, #{r.textRetention}, #{r.promptChars},
               #{r.responseChars}, #{r.inputTokens}, #{r.outputTokens}, #{r.reasoningTokens},
               #{r.cachedInputTokens}, #{r.providerRequestId}, #{r.streamed}, #{r.latencyMs},
               #{r.finishReason}, #{r.errorType}, #{r.stepId}, #{r.createdAt})
            </foreach>
            ON DUPLICATE KEY UPDATE
              prompt_text    = VALUES(prompt_text),
              raw_response   = VALUES(raw_response),
              text_retention = VALUES(text_retention),
              input_tokens   = VALUES(input_tokens),
              output_tokens  = VALUES(output_tokens),
              latency_ms     = VALUES(latency_ms),
              finish_reason  = VALUES(finish_reason),
              error_type     = VALUES(error_type)
            </script>
            """)
    int insertBatch(@Param("rows") List<AiGeneration> rows);
}
