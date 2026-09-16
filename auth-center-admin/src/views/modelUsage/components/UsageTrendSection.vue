<!--
  ② 时间 × 模型 —— 回答「什么时候烧的、烧在哪个模型上」。
  图与表是同一份数据的两种看法:图看趋势,表拿具体数值。
-->
<template>
  <SectionCard
    class="usage-section"
    :title="t('usage.trendTitle')"
    :description="t('usage.trendHint')"
    :icon="BarChartOutlined"
  >
    <template #extra>
      <div class="usage-viewtoggle">
        <button
          v-for="v in VIEWS"
          :key="v.key"
          type="button"
          class="usage-viewbtn"
          :class="{ 'usage-viewbtn--active': view === v.key }"
          @click="view = v.key"
        >
          <component :is="v.icon" />
          {{ t(v.labelKey) }}
        </button>
      </div>
    </template>

    <UsageStackedBars
      v-if="view === 'chart'"
      :points="series"
      :bucket="seriesBucket"
      :empty-text="t('usage.empty')"
    />
    <a-table
      v-else
      class="usage-table"
      :columns="seriesColumns"
      :data-source="series"
      :loading="loading"
      :pagination="false"
      :row-key="(r: UsageSeriesPoint) => r.bucket + '|' + r.model"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'totalTokens'">
          <span class="mono">{{ fmt((record as UsageSeriesPoint).totalTokens) }}</span>
        </template>
        <template v-else-if="column.key === 'cacheHitRate'">
          <span class="mono">{{ cacheHitRate(record as UsageSeriesPoint) ?? '—' }}</span>
        </template>
      </template>
    </a-table>
  </SectionCard>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { BarChartOutlined, UnorderedListOutlined } from '@ant-design/icons-vue'
import SectionCard from '@/components/common/SectionCard.vue'
import type { UsageSeriesPoint } from '@/api'
import UsageStackedBars from './UsageStackedBars.vue'
import { cacheHitRate, fmt } from '../usageFormat'

defineProps<{
  /** 时间 × 模型的数据点。 */
  series: UsageSeriesPoint[]
  /** 后端定的聚合粒度(day / hour 等)，图例与轴按它渲染。 */
  seriesBucket: string
  /** 数据加载中。 */
  loading: boolean
}>()

const { t } = useI18n()

const VIEWS = [
  { key: 'chart' as const, labelKey: 'usage.viewChart', icon: BarChartOutlined },
  { key: 'table' as const, labelKey: 'usage.viewTable', icon: UnorderedListOutlined },
]

/** 图/表切换是纯展示偏好，不影响取数，故留在本组件内部而非提到页面状态里。 */
const view = ref<'chart' | 'table'>('chart')

const seriesColumns = computed(() => [
  { title: t('usage.colBucket'), dataIndex: 'bucket', key: 'bucket' },
  { title: t('usage.colModel'), dataIndex: 'model', key: 'model' },
  { title: t('usage.colTotal'), dataIndex: 'totalTokens', key: 'totalTokens', align: 'right' },
  { title: t('usage.colCacheHitRate'), key: 'cacheHitRate', align: 'right' },
])
</script>

<style scoped lang="scss">
@use './_usageShared.scss';

.usage-section {
  margin-bottom: 18px;
}

/* ── 图表 / 表格切换 ── */
.usage-viewtoggle {
  display: inline-flex;
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius-sm);
  overflow: hidden;
}
.usage-viewbtn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 11px;
  border: none;
  background: var(--ds-card-bg);
  color: var(--ds-text-muted);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;

  & + & {
    border-left: 1px solid var(--ds-border);
  }
  &--active {
    background: var(--ds-primary-soft);
    color: var(--ds-primary-soft-text);
    font-weight: 600;
  }
}
</style>
