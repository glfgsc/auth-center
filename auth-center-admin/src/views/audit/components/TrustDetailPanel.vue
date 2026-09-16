<!--
  一轮的下两层 —— 逐次模型调用 + 逐条检测判定,展开时才拉。

  这里回答的是轮级那一行答不了的两个问题:模型究竟看到了什么、原样吐了什么(一轮 N 次调用,
  每次一份提示词),以及各检测器判了什么(而不只是「拦没拦」)。

  正文按留存策略给:`textRetention=DIGEST` 的调用只有字数没有全文。这两者要说清区别 ——
  「按策略没留」和「没采到」在界面上都是空,不标出来的话读的人会以为系统漏采了。
-->
<template>
  <div class="trust-detail">
    <p v-if="loading" class="trust-detail__hint">{{ t('audit.detailLoading') }}</p>
    <p v-else-if="failed" class="trust-detail__hint">{{ t('audit.detailFailed') }}</p>

    <template v-else>
      <!-- 检测判定:打分型看分,判档型看档 —— 两种量纲各显各的,不硬凑成一列 -->
      <div v-if="signals.length" class="trust-detail__block">
        <span class="detail__k">{{ t('audit.signals') }}</span>
        <div class="trust-detail__signals">
          <span v-for="s in signals" :key="s.id" class="pill pill--xs" :class="pillClass(s)">
            {{ s.detectorType }}<template v-if="s.contentType">·{{ s.contentType }}</template>
            {{ ' ' }}
            <template v-if="s.valueNum != null">{{ Number(s.valueNum).toFixed(2) }}</template>
            <template v-else-if="s.valueLabel">{{ s.valueLabel }}</template>
            <template v-if="s.detail"> ({{ s.detail }})</template>
          </span>
        </div>
      </div>

      <!-- 逐次调用:模型看到的与用户看到的并排,一眼可对 -->
      <div v-if="generations.length" class="trust-detail__block">
        <span class="detail__k">{{ t('audit.generations') }}</span>
        <div v-for="g in generations" :key="g.generationId" class="trust-detail__gen">
          <div class="trust-detail__gen-head">
            <span class="mono">#{{ g.seq }}</span>
            <span class="mono mono--soft">{{ g.model || '—' }}</span>
            <span v-if="g.latencyMs != null" class="mono mono--soft">{{ g.latencyMs }} ms</span>
            <span v-if="g.providerRequestId" class="mono mono--soft">{{ g.providerRequestId }}</span>
            <!-- 输出被截断是「答得不全」最直接的解释 -->
            <span v-if="g.finishReason === 'length'" class="pill pill--xs pill--warning">
              {{ t('audit.truncated') }}
            </span>
            <span v-if="g.errorType" class="pill pill--xs pill--danger">{{ g.errorType }}</span>
          </div>
          <template v-if="g.textRetention === 'FULL'">
            <pre class="detail__pre">{{ g.promptText || '—' }}</pre>
            <pre class="detail__pre">{{ g.rawResponse || '—' }}</pre>
          </template>
          <!--
            没留全文时报字数而不是留白:字数说明「采到了、只是按策略没存」,
            留白会被读成「系统没采」。两者在排查时导向完全不同的动作。
          -->
          <p v-else class="trust-detail__digest">
            {{ t('audit.digestOnly', { prompt: g.promptChars ?? 0, response: g.responseChars ?? 0 }) }}
          </p>
        </div>
      </div>

      <p v-if="!signals.length && !generations.length" class="trust-detail__hint">
        {{ t('audit.detailEmpty') }}
      </p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { AiContentSignalItem, AiGenerationItem } from '@/api'
import { trustApi } from '@/api'

const props = defineProps<{
  /** 轮次标识(= 产品侧 turn_id)。 */
  requestId: string
}>()

const { t } = useI18n()

const generations = ref<AiGenerationItem[]>([])
const signals = ref<AiContentSignalItem[]>([])
const loading = ref(true)
/** 拉失败与「确实没有」分开说 —— 合成一句「暂无」会让人以为查过了。 */
const failed = ref(false)

onMounted(async () => {
  try {
    const res = await trustApi.detail(props.requestId)
    generations.value = res.data?.generations ?? []
    signals.value = res.data?.signals ?? []
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
})

/** 毒性 / PII 命中标黄阈值 —— 与信任层的告警口径一致。 */
const SIGNAL_HIGH = 0.5

/** 需要引起注意的判档取值。 */
const ALERT_LABELS = new Set(['HIGH', 'PARTIALLY', 'UNRESOLVED'])

/** 信号的显示色:够高的分与需注意的档标黄,其余中性 —— 只有该看的才抢眼。 */
function pillClass(s: AiContentSignalItem): string {
  if (s.valueNum != null && Number(s.valueNum) >= SIGNAL_HIGH) return 'pill--warning'
  if (s.valueLabel && ALERT_LABELS.has(s.valueLabel)) return 'pill--warning'
  return 'pill--neutral'
}
</script>

<style scoped lang="scss">
.trust-detail {
  margin-top: 8px;
}

.trust-detail__hint {
  color: var(--text-tertiary, #999);
  font-size: 12px;
  margin: 4px 0;
}

.trust-detail__block {
  margin-top: 10px;
}

.trust-detail__signals {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.trust-detail__gen {
  margin-top: 8px;
  padding-left: 10px;
  border-left: 2px solid var(--border-secondary, #eee);
}

.trust-detail__gen-head {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  font-size: 12px;
}

.trust-detail__digest {
  color: var(--text-tertiary, #999);
  font-size: 12px;
  margin: 4px 0 0;
}
</style>
