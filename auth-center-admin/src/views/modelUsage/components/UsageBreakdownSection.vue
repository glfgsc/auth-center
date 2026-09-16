<!--
  ③ 明细下钻 —— 回答「具体是谁」。
  逐轮维度是最细一层:展开即列出该轮逐次用量(请求 ID ↔ 该次 token),可直接和服务商控制台逐行对。
-->
<template>
  <SectionCard
    class="usage-section"
    :title="t('usage.breakdownTitle')"
    :icon="TableOutlined"
  >
    <!-- 单轮定位横幅:从产品侧会话回放深链进来,明细区按对账键只取那一轮 -->
    <div v-if="deepTurnId" class="usage-deeplink">
      <span class="usage-deeplink__text">{{ t('usage.turnFilterBanner') }}</span>
      <span class="mono usage-deeplink__id">{{ deepTurnId }}</span>
      <a-button size="small" @click="emit('clear-deeplink')">
        {{ t('usage.turnFilterClear') }}
      </a-button>
    </div>

    <nav class="usage-tabs" role="tablist">
      <button
        v-for="d in DIMENSIONS"
        :key="d.key"
        type="button"
        role="tab"
        class="usage-tab"
        :class="{ 'usage-tab--active': dimension === d.key }"
        :aria-selected="dimension === d.key"
        @click="emit('switch-dimension', d.key)"
      >
        <component :is="d.icon" class="usage-tab__icon" />
        {{ t(d.labelKey) }}
      </button>
    </nav>

    <a-table
      class="usage-table"
      :columns="breakdownColumns"
      :data-source="rows"
      :loading="loading"
      :row-key="rowKeyOf"
      :expanded-row-keys="expandedKeys"
      :pagination="false"
      size="small"
      @expand="(expanded: boolean, row: UsageBreakdownRow) => emit('expand', expanded, row)"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'subject'">
          <span class="usage-subject">{{ subjectOf(record as UsageBreakdownRow) }}</span>
          <span v-if="dimension === 'model'" class="usage-subject__sub">
            {{ (record as UsageBreakdownRow).provider }}
          </span>
          <span v-else-if="dimension === 'turn'" class="usage-subject__sub">
            {{ (record as UsageBreakdownRow).model }}
          </span>
        </template>
        <!-- 少报标记:出网次数多于已记账次数,差出来的那几次服务商可能已计费而我们没记 -->
        <template v-else-if="column.key === 'httpAttempts'">
          <span class="mono">{{ (record as UsageBreakdownRow).httpAttempts ?? '—' }}</span>
          <span
            v-if="underReported(record as UsageBreakdownRow)"
            class="pill pill--xs pill--warning usage-gap"
            :title="t('usage.underReportedTip')"
          >
            +{{ gapOf(record as UsageBreakdownRow) }}
          </span>
        </template>
        <template v-else-if="NUMERIC_KEYS.has(String(column.key))">
          <span class="mono">{{ fmt(Number(record[column.key as string] ?? 0)) }}</span>
        </template>
        <!-- 命中率按行算:没有输入的行无所谓命中,给占位 -->
        <template v-else-if="column.key === 'cacheHitRate'">
          <span class="mono">{{ cacheHitRate(record as UsageBreakdownRow) ?? '—' }}</span>
        </template>
      </template>

      <template v-if="dimension === 'turn'" #expandedRowRender="{ record }">
        <UsageCallsDetail :row="record as UsageBreakdownRow" />
      </template>
    </a-table>

    <!-- 深链定位一轮时本区只有那一行,分页器无意义;其余情形一律给,别让人以为看到的就是全部。 -->
    <div v-if="!deepTurnId" class="usage-foot">
      <span class="usage-foot__total">{{ t('usage.totalCount', { n: total }) }}</span>
      <a-pagination
        :current="page"
        :page-size="size"
        :total="total"
        :show-size-changer="true"
        :page-size-options="['20', '50', '100']"
        size="small"
        @change="(p: number, s: number) => emit('pick-page', p, s)"
      />
    </div>

    <p v-if="deepTurnId && !loading && !rows.length" class="usage-deeplink__empty">
      {{ t('usage.turnFilterEmpty') }}
    </p>
  </SectionCard>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  BarChartOutlined,
  FieldTimeOutlined,
  RobotOutlined,
  TableOutlined,
  TeamOutlined,
} from '@ant-design/icons-vue'
import SectionCard from '@/components/common/SectionCard.vue'
import type { UsageBreakdownRow } from '@/api'
import type { Dimension } from '../composables/useModelUsage'
import { NUMERIC_KEYS, cacheHitRate, fmt, gapOf, underReported } from '../usageFormat'
import UsageCallsDetail from './UsageCallsDetail.vue'

const props = defineProps<{
  /** 当前维度下的明细行。 */
  rows: UsageBreakdownRow[]
  /** 当前下钻维度。 */
  dimension: Dimension
  /** 数据加载中。 */
  loading: boolean
  /** 深链对账键；置位时本区只显示那一轮。 */
  deepTurnId: string
  /** 已展开的行键。 */
  expandedKeys: string[]
  /** 行键取值函数（随维度变化，故由调用方给）。 */
  rowKeyOf: (row: UsageBreakdownRow) => string
  /** 当前过滤条件下的命中总数（服务端给，不是本页行数）。 */
  total: number
  /** 当前页码。 */
  page: number
  /** 每页条数。 */
  size: number
}>()

const emit = defineEmits<{
  'switch-dimension': [d: Dimension]
  'clear-deeplink': []
  expand: [expanded: boolean, row: UsageBreakdownRow]
  'pick-page': [page: number, size: number]
}>()

const { t } = useI18n()

const DIMENSIONS = [
  { key: 'user' as Dimension, labelKey: 'usage.byUser', icon: TeamOutlined },
  { key: 'model' as Dimension, labelKey: 'usage.byModel', icon: BarChartOutlined },
  { key: 'agent' as Dimension, labelKey: 'usage.byAgent', icon: RobotOutlined },
  // 最细一层:不聚合,带服务商请求 ID —— 对账和看少报都只能在这一层做
  { key: 'turn' as Dimension, labelKey: 'usage.byTurn', icon: FieldTimeOutlined },
]

const SUBJECT_TITLE_KEYS: Record<Dimension, string> = {
  user: 'usage.colUser',
  model: 'usage.colModel',
  agent: 'usage.colAgent',
  turn: 'usage.colTurn',
}

const breakdownColumns = computed(() => {
  const cols: Record<string, unknown>[] = [
    { title: t(SUBJECT_TITLE_KEYS[props.dimension]), key: 'subject' },
  ]
  // 逐轮维度没有「轮次数」可言(它本身就是一轮),换成时间;
  // 并多两列对账用的:真实出网次数与其中失败次数。
  if (props.dimension === 'turn') {
    cols.push({ title: t('usage.colTime'), key: 'time', dataIndex: 'createdAt' })
  } else {
    cols.push({ title: t('usage.colTurns'), dataIndex: 'turns', key: 'turns', align: 'right' })
  }
  cols.push(
    { title: t('usage.colLlmCalls'), dataIndex: 'llmCalls', key: 'llmCalls', align: 'right' },
    { title: t('usage.colInput'), dataIndex: 'inputTokens', key: 'inputTokens', align: 'right' },
    { title: t('usage.colOutput'), dataIndex: 'outputTokens', key: 'outputTokens', align: 'right' },
    {
      title: t('usage.colCached'),
      dataIndex: 'cachedInputTokens',
      key: 'cachedInputTokens',
      align: 'right',
    },
    { title: t('usage.colCacheHitRate'), key: 'cacheHitRate', align: 'right' },
    { title: t('usage.colTotal'), dataIndex: 'totalTokens', key: 'totalTokens', align: 'right' },
  )
  if (props.dimension === 'turn') {
    cols.push({
      title: t('usage.colHttpAttempts'),
      dataIndex: 'httpAttempts',
      key: 'httpAttempts',
      align: 'right',
    })
  }
  return cols
})

/** 主体列文案：人相关的维度显示昵称/用户名，其余显示模型或智能体标识。 */
function subjectOf(row: UsageBreakdownRow): string {
  if (props.dimension === 'user' || props.dimension === 'turn') {
    return row.nickname || row.username || (row.userId != null ? `#${row.userId}` : '—')
  }
  return (props.dimension === 'model' ? row.model : row.agentKey) || '—'
}

</script>

<style scoped lang="scss">
@use './_usageShared.scss';

/* ── 明细分页页脚(与会话/登录历史页脚同构) ── */
.usage-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 2px 2px;
}
.usage-foot__total {
  font-size: 12.5px;
  color: var(--ds-text-muted);
}


.usage-section {
  margin-bottom: 18px;
}

/* ── 单轮定位(产品侧深链) ── */
.usage-deeplink {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
  background: var(--ds-primary-soft);
  border: 1px solid var(--ds-primary-soft-border);
  border-radius: var(--ds-radius-sm);
}
.usage-deeplink__text {
  font-size: 12.5px;
  color: var(--ds-primary-soft-text);
}
.usage-deeplink__id {
  user-select: all;
  word-break: break-all;
}
.usage-deeplink__empty {
  margin: 12px 2px 0;
  font-size: 12.5px;
  color: var(--ds-text-faint);
}

/* ── 下划线 Tab(与审计台同构) ── */
.usage-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 12px;
  border-bottom: 1px solid var(--ds-border);
}
.usage-tab {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 9px 15px;
  margin-bottom: -1px;
  border: none;
  background: none;
  cursor: pointer;
  font: inherit;
  font-size: 13.5px;
  color: var(--ds-text-muted);
  border-bottom: 2px solid transparent;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--ds-text);
  }
  &--active {
    color: var(--ds-primary);
    font-weight: 600;
    border-bottom-color: var(--ds-primary);
  }
}
.usage-tab__icon {
  font-size: 15px;
}

.usage-subject {
  color: var(--ds-text);
}
.usage-subject__sub {
  margin-left: 8px;
  font-size: 11.5px;
  color: var(--ds-text-faint);
}

/* 少报标记紧跟出网次数,一眼看出这一轮差了几次 */
.usage-gap {
  margin-left: 6px;
}

</style>
