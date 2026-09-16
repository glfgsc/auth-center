package com.auth.center.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 一次检测判定 —— 对应 {@code auth_ai_content_signal} 表。
 *
 * 取代此前扁平的「一个布尔 + 一个毒性最高分 + 一个 JSON」:那种形态能回答「这轮被拦了吗」,回答不了「上周有多少次结论没通过数字溯源」
 * 「是检索层坏还是生成层坏」—— 而后面这类才是效果评估要问的问题。
 *
 * 取值分两列:检测器天生两种量纲 —— 毒性 / PII 是 0–1 的分,指令遵循 / 任务解决是三值档。硬塞进一列要么丢精度要么丢语义。
 */
@TableName("auth_ai_content_signal")
public class AiContentSignal {

    /** 个人信息命中。 */
    public static final String DETECTOR_PII = "PII";

    /** 毒性。 */
    public static final String DETECTOR_TOXICITY = "TOXICITY";

    /** 提示词注入防御。 */
    public static final String DETECTOR_PROMPT_DEFENSE = "PROMPT_DEFENSE";

    /** 指令遵循(判官模型)。 */
    public static final String DETECTOR_INSTRUCTION_ADHERENCE = "INSTRUCTION_ADHERENCE";

    /** 任务是否真解决(判官模型)。 */
    public static final String DETECTOR_TASK_RESOLUTION = "TASK_RESOLUTION";

    /** 用户限定有没有落到查询上 —— 口径闸与限定落位对账的判定。 */
    public static final String DETECTOR_SCOPE_LANDED = "SCOPE_LANDED";

    /** 结论里的数字能否溯源到取回的数据。 */
    public static final String DETECTOR_NUMBER_GROUNDED = "NUMBER_GROUNDED";

    /** 用户说的取值有没有接到真实值域上。 */
    public static final String DETECTOR_VALUE_GROUNDED = "VALUE_GROUNDED";

    /** 用户侧。 */
    public static final String CONTENT_INPUT = "INPUT";

    /** 模型侧。 */
    public static final String CONTENT_OUTPUT = "OUTPUT";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属轮次。 */
    private String requestId;

    /** 所属调用({@link AiGeneration#getGenerationId()});轮级判定为空。 */
    private String generationId;

    /** 算在哪个产品头上。 */
    private String systemCode;

    /** 检测器({@code DETECTOR_*} 之一)。 */
    private String detectorType;

    /** {@link #CONTENT_INPUT} / {@link #CONTENT_OUTPUT} —— 合规须能分辨「是用户问得脏还是模型答得脏」。 */
    private String contentType;

    /** 细分类别(PII 的 Name/Email、毒性的 hate/violence 等)。 */
    private String category;

    /** 0.000–1.000 的分;三值档检测器为空。 */
    private BigDecimal valueNum;

    /** {@code HIGH}/{@code LOW}/{@code UNCERTAIN} 或 {@code FULLY}/{@code PARTIALLY}/{@code UNRESOLVED}。 */
    private String valueLabel;

    /** 判定说理 / 命中项(不放证据原文)。 */
    private String detail;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getGenerationId() {
        return generationId;
    }

    public void setGenerationId(String generationId) {
        this.generationId = generationId;
    }

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getDetectorType() {
        return detectorType;
    }

    public void setDetectorType(String detectorType) {
        this.detectorType = detectorType;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public BigDecimal getValueNum() {
        return valueNum;
    }

    public void setValueNum(BigDecimal valueNum) {
        this.valueNum = valueNum;
    }

    public String getValueLabel() {
        return valueLabel;
    }

    public void setValueLabel(String valueLabel) {
        this.valueLabel = valueLabel;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
