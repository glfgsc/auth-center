package com.auth.center.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/**
 * 「模型用量」看板各出口的列形状守卫 —— 缓存命中率的分子与分母必须在每条聚合查询里同时出现。
 *
 * 命中率 = {@code cachedInputTokens / inputTokens},由前端按行相除(SQL 里做除法要处理空分组除零)。
 * 任一出口少了其中一列,那一层看板就只能显示「—」,而且是静默的:查询照样跑通、其余列照常有数。故这里逐条读 {@link Select} 的 SQL
 * 文本钉住两列的别名,新增一条用量查询也自动进清单。
 */
class AiTrustLogMapperUsageColumnsTest {

    /** 看板三层用到的全部聚合查询;{@code turnByRequestId} 与逐轮列表共用同一段列定义,不重复列。 */
    private static final List<String> USAGE_QUERIES =
            List.of(
                    "summarizeBySystem",
                    "seriesByBucketAndModel",
                    "breakdownByUser",
                    "breakdownByModel",
                    "breakdownByAgent",
                    "breakdownByTurn");

    private static final Pattern INPUT_ALIAS = Pattern.compile("\\bAS inputTokens\\b");

    private static final Pattern CACHED_ALIAS = Pattern.compile("\\bAS cachedInputTokens\\b");

    /** 每条用量查询都同时选出输入与缓存命中两列。 */
    @Test
    void everyUsageQuerySelectsInputAndCachedInput() {
        for (String name : USAGE_QUERIES) {
            String sql = selectSqlOf(name);
            assertTrue(INPUT_ALIAS.matcher(sql).find(), name + " 缺 inputTokens 列(命中率分母)");
            assertTrue(CACHED_ALIAS.matcher(sql).find(), name + " 缺 cachedInputTokens 列(命中率分子)");
        }
    }

    /**
     * 取 Mapper 方法上 {@link Select} 的 SQL 文本(多段拼接后的整串)。
     *
     * @param methodName Mapper 方法名(用量查询各自签名不同,按名字取即可)
     * @return SQL 文本
     */
    private static String selectSqlOf(String methodName) {
        for (Method m : AiTrustLogMapper.class.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                Select select = m.getAnnotation(Select.class);
                assertTrue(select != null, methodName + " 没有 @Select");
                return String.join(" ", select.value());
            }
        }
        throw new AssertionError("AiTrustLogMapper 没有方法 " + methodName);
    }
}
