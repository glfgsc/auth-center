<!--
  逐轮维度的展开区 —— 该轮逐次用量归属(请求 ID ↔ 该次 token),可直接和服务商控制台逐行对。

  明细按需取(展开才拉):逐次归属已从轮级那一行的 JSON 列行化到调用级表,一轮十几次调用、
  每次可能带全文,随列表下发会让一页几十行变成几兆。
-->
<template>
  <div class="usage-calls">
    <!-- 同一轮的另一半账:语义面(问了什么、路由到哪、答了什么)在产品侧的会话回放。
         旧行没有 session_id(互链上线前落的),不给链接 —— 指个 404 不如不指 -->
    <p v-if="row.sessionId" class="usage-calls__trace">
      <a :href="traceUrl" target="_blank" rel="noopener">{{ t('usage.turnLink') }} ↗</a>
      <span class="usage-calls__trace-hint">{{ t('usage.turnLinkHint') }}</span>
    </p>
    <p class="usage-calls__hint">{{ t('usage.callsHint') }}</p>

    <table v-if="calls.length" class="usage-calls__table">
      <thead>
        <tr>
          <th class="usage-calls__idx">#</th>
          <th>{{ t('usage.colRequestId') }}</th>
          <th class="usage-calls__num">{{ t('usage.colInput') }}</th>
          <th class="usage-calls__num">{{ t('usage.colOutput') }}</th>
          <th class="usage-calls__num">{{ t('usage.colCached') }}</th>
          <th class="usage-calls__num">{{ t('usage.colTotal') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(c, i) in calls" :key="i">
          <td class="usage-calls__idx">{{ i + 1 }}</td>
          <td>
            <span class="mono usage-calls__id">{{ c.providerRequestId || '—' }}</span>
            <!-- ID 可信度:精确的不标(默认即精确),按序配上的与没配上的必须标出来,
                 否则拿一个「看起来像 ID」的值去服务商侧查不到会白费功夫 -->
            <!-- 输出被截断是「答得不全」最直接的解释,标出来免得从别处找原因 -->
            <span
              v-if="c.finishReason === 'length'"
              class="pill pill--xs pill--warning usage-calls__tag"
              :title="t('usage.truncatedTip')"
              >{{ t('usage.truncated') }}</span
            >
            <!-- 流式那几次没有服务商原文可比,标出来免得对不上时抓错方向 -->
            <span
              v-if="c.streamed"
              class="pill pill--xs pill--neutral usage-calls__tag"
              :title="t('usage.streamedTip')"
              >{{ t('usage.streamed') }}</span
            >
          </td>
          <td class="usage-calls__num mono">{{ fmt(c.inputTokens ?? 0) }}</td>
          <td class="usage-calls__num mono">{{ fmt(c.outputTokens ?? 0) }}</td>
          <td class="usage-calls__num mono">{{ fmt(c.cachedInputTokens ?? 0) }}</td>
          <td class="usage-calls__num mono">
            {{ fmt((c.inputTokens ?? 0) + (c.outputTokens ?? 0)) }}
          </td>
        </tr>
      </tbody>
      <!-- 逐次求和 —— 与本行的合计对不上就是 bug,两边都摆出来让它自己暴露 -->
      <tfoot>
        <tr>
          <td colspan="2">{{ t('usage.colTotal') }}</td>
          <td class="usage-calls__num mono">{{ fmt(sums.input) }}</td>
          <td class="usage-calls__num mono">{{ fmt(sums.output) }}</td>
          <td class="usage-calls__num mono">{{ fmt(sums.cached) }}</td>
          <td class="usage-calls__num mono">{{ fmt(sums.input + sums.output) }}</td>
        </tr>
      </tfoot>
    </table>

    <!-- 三态分开说:在拉 / 拉失败 / 确实没有。合成一句「暂无」会让人以为查过了 -->
    <p v-else-if="loading" class="usage-calls__empty">{{ t('usage.callsLoading') }}</p>
    <p v-else-if="failed" class="usage-calls__empty">{{ t('usage.callsFailed') }}</p>
    <p v-else class="usage-calls__empty">{{ t('usage.requestIdsEmpty') }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AiGenerationItem, UsageBreakdownRow } from '@/api'
import { trustApi } from '@/api'
import { callSum, fmt } from '../usageFormat'

const props = defineProps<{
  /** 展开的那一轮。 */
  row: UsageBreakdownRow
}>()

const { t } = useI18n()

const calls = ref<AiGenerationItem[]>([])
const loading = ref(false)
/** 拉失败要与「确实没有」分开说 —— 合成一句「暂无」会让人以为查过了。 */
const failed = ref(false)

onMounted(async () => {
  const requestId = props.row.requestId
  if (!requestId) return
  loading.value = true
  try {
    const res = await trustApi.detail(requestId)
    calls.value = res.data?.generations ?? []
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

const sums = computed(() => ({
  input: callSum(calls.value, 'inputTokens'),
  output: callSum(calls.value, 'outputTokens'),
  cached: callSum(calls.value, 'cachedInputTokens'),
}))

/** 产品侧会话回放地址 —— 生产同域名(工坊挂 /agent 前缀);dev 跨端口时用 VITE_WORKSHOP_URL 指过去。 */
const traceUrl = computed(() => {
  const base =
    (import.meta.env.VITE_WORKSHOP_URL as string | undefined) || `${window.location.origin}/agent`
  return `${base}/sessions/${props.row.sessionId}?turn=${encodeURIComponent(props.row.requestId ?? '')}`
})
</script>

<style scoped lang="scss">
@use './_usageShared.scss';

.usage-calls {
  padding: 4px 2px;
}
.usage-calls__hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--ds-text-faint);
}
.usage-calls__trace {
  margin: 0 0 8px;
  font-size: 12.5px;

  a {
    font-weight: 600;
  }
}
.usage-calls__trace-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ds-text-faint);
}
.usage-calls__table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;

  th {
    text-align: left;
    font-weight: 600;
    font-size: 11.5px;
    color: var(--ds-text-faint);
    padding: 6px 10px;
    border-bottom: 1px solid var(--ds-border);
  }
  td {
    padding: 6px 10px;
    border-bottom: 1px solid var(--ds-divider);
    color: var(--ds-text-soft);
  }
  tbody tr:last-child td {
    border-bottom: 1px solid var(--ds-border);
  }
  tfoot td {
    font-weight: 600;
    color: var(--ds-text);
    border-bottom: none;
  }
}
.usage-calls__num {
  text-align: right;
  white-space: nowrap;
}
.usage-calls__idx {
  width: 32px;
  color: var(--ds-text-faint);
}
/* ID 要能整段选中复制 —— 这一列的唯一用途就是粘到服务商控制台里查 */
.usage-calls__id {
  user-select: all;
  word-break: break-all;
}
.usage-calls__tag {
  margin-left: 8px;
}
.usage-calls__empty {
  margin: 0;
  font-size: 12px;
  color: var(--ds-text-faint);
}

.usage-ids__list {
  margin: 0;
  padding-left: 22px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 4px 16px;

  li {
    /* ID 要能整段选中复制 —— 这一列的唯一用途就是粘到服务商控制台里查 */
    user-select: all;
    word-break: break-all;
  }
}
</style>
