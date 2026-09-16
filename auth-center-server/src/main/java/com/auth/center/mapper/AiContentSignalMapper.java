package com.auth.center.mapper;

import com.auth.center.entity.AiContentSignal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 质量信号 Mapper。 */
@Mapper
public interface AiContentSignalMapper extends BaseMapper<AiContentSignal> {

    /**
     * 整轮的信号一次写入。
     *
     * @param rows 信号(非空)
     * @return 受影响行数
     */
    @Insert(
            """
            <script>
            INSERT INTO auth_ai_content_signal
              (request_id, generation_id, system_code, detector_type, content_type,
               category, value_num, value_label, detail, created_at)
            VALUES
            <foreach collection="rows" item="r" separator=",">
              (#{r.requestId}, #{r.generationId}, #{r.systemCode}, #{r.detectorType},
               #{r.contentType}, #{r.category}, #{r.valueNum}, #{r.valueLabel},
               #{r.detail}, #{r.createdAt})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("rows") List<AiContentSignal> rows);
}
