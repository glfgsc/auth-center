<!--
  ① 分产品卡片 —— 回答「哪个产品在烧 token」。
  卡片同时是最顺手的筛选入口:点一下筛该产品,再点一次取消。
-->
<template>
  <div v-if="rows.length" class="usage-cards">
    <article
      v-for="row in rows"
      :key="row.systemCode"
      class="usage-card"
      :class="{ 'usage-card--active': activeCode === row.systemCode }"
      @click="emit('toggle', row.systemCode)"
    >
      <header class="usage-card__head">
        <span class="usage-card__name">{{ systemLabel(row.systemCode) }}</span>
        <span class="pill pill--xs" :class="sharePill(row, grandTotal)">
          {{ sharePercent(row, grandTotal) }}
        </span>
      </header>
      <div class="usage-card__total">{{ fmt(row.totalTokens) }}</div>
      <div class="usage-card__unit">{{ t('usage.tokens') }}</div>

      <!-- 输入/输出占比条:一眼看出这个产品是"问得长"还是"答得长" -->
      <div class="usage-bar" :title="ioTitle(row)">
        <i class="usage-bar__in" :style="{ width: inputShare(row) }" />
        <i class="usage-bar__out" :style="{ width: outputShare(row) }" />
      </div>
      <dl class="usage-card__meta">
        <div><dt>{{ t('usage.turns') }}</dt><dd>{{ row.turns }}</dd></div>
        <div><dt>{{ t('usage.llmCalls') }}</dt><dd>{{ row.llmCalls }}</dd></div>
        <div><dt>{{ t('usage.users') }}</dt><dd>{{ row.users }}</dd></div>
        <!-- 命中率 = 命中 / 输入;悬浮给两个原数,读数对不上时能直接核 -->
        <div>
          <dt>{{ t('usage.cacheHit') }}</dt>
          <a-tooltip :title="cacheTip(row)">
            <dd>{{ cacheHitRate(row) ?? '—' }}</dd>
          </a-tooltip>
        </div>
      </dl>
    </article>
  </div>
  <div v-else-if="!loading" class="usage-empty">{{ t('usage.empty') }}</div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import type { UsageSystemRow } from '@/api'
import {
  cacheHitRate,
  fmt,
  inputShare,
  outputShare,
  sharePercent,
  sharePill,
} from '../usageFormat'

defineProps<{
  /** 各产品的汇总行。 */
  rows: UsageSystemRow[]
  /** 全平台总量，用于算占比。 */
  grandTotal: number
  /** 当前筛选中的产品标识；未筛选为 undefined。 */
  activeCode: string | undefined
  /** 数据加载中 —— 加载期不显示空态，免得闪一下「暂无数据」。 */
  loading: boolean
  /** 产品标识 → 展示名。 */
  systemLabel: (code: string) => string
}>()

const emit = defineEmits<{ toggle: [code: string] }>()

const { t } = useI18n()

/** 悬浮提示：输入 / 输出 / 缓存命中三行明细。 */
function ioTitle(row: UsageSystemRow): string {
  return [
    t('usage.inputTokens', { n: fmt(row.inputTokens) }),
    t('usage.outputTokens', { n: fmt(row.outputTokens) }),
    t('usage.cachedTip', { n: fmt(row.cachedInputTokens) }),
  ].join('\n')
}

/** 命中率悬浮提示:命中与输入的原数。 */
function cacheTip(row: UsageSystemRow): string {
  return t('usage.cacheHitTip', { cached: fmt(row.cachedInputTokens), input: fmt(row.inputTokens) })
}
</script>

<style scoped lang="scss">
@use './_usageShared.scss';

/* ── ① 产品卡片 ── */
.usage-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.usage-card {
  padding: 16px 18px;
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius);
  box-shadow: var(--ds-shadow-sm);
  cursor: pointer;
  transition: border-color 0.12s, box-shadow 0.12s;

  &:hover {
    border-color: var(--ds-primary);
  }
  &--active {
    border-color: var(--ds-primary);
    box-shadow: 0 0 0 1px var(--ds-primary) inset;
  }
}
.usage-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}
.usage-card__name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-text);
}
.usage-card__total {
  font-size: 26px;
  font-weight: 650;
  line-height: 1.1;
  color: var(--ds-text);
  font-variant-numeric: tabular-nums;
}
.usage-card__unit {
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--ds-text-faint);
}

.usage-bar {
  display: flex;
  height: 6px;
  margin: 12px 0 10px;
  border-radius: 3px;
  overflow: hidden;
  background: var(--ds-bg-soft);
}
.usage-bar__in {
  background: var(--ds-primary);
}
.usage-bar__out {
  background: var(--ds-success);
}

.usage-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 0;

  dt {
    font-size: 11px;
    color: var(--ds-text-faint);
  }
  dd {
    margin: 2px 0 0;
    font-size: 13px;
    color: var(--ds-text-soft);
    font-variant-numeric: tabular-nums;
  }
}

.usage-empty {
  padding: 48px 0;
  text-align: center;
  color: var(--ds-text-faint);
  font-size: 13px;
}
</style>
