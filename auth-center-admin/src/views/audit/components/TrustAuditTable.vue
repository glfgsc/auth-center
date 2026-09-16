<!--
  AI 信任日志表 —— agent 汇入的 LLM 信任层遥测(脱敏提示词 / 答复 / PII / 毒性 / 是否拦截)。
  只读:由 agent 推送写入,本组件不发起任何写操作。
-->
<template>
  <a-table
    class="audit-table"
    :columns="columns"
    :data-source="rows"
    :loading="loading"
    :pagination="false"
    row-key="id"
    :expand-row-by-click="true"
  >
    <template #emptyText>
      <div class="audit-empty">
        <InboxOutlined class="audit-empty__icon" />
        <span>{{ t('audit.empty') }}</span>
      </div>
    </template>
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'createdAt'">
        <span class="audit-time">{{ fmtTime(record.createdAt) }}</span>
      </template>
      <template v-else-if="column.key === 'agentKey'">
        <span class="mono mono--soft">{{ record.agentKey || '—' }}</span>
      </template>
      <template v-else-if="column.key === 'trustSource'">
        <span class="pill pill--neutral">{{ record.source || '—' }}</span>
      </template>
      <template v-else-if="column.key === 'model'">
        <span class="mono">{{ record.provider }}/{{ record.model || '—' }}</span>
      </template>
      <template v-else-if="column.key === 'blocked'">
        <span v-if="record.blocked === 1" class="pill pill--danger">
          {{ record.blockReason || t('audit.blocked') }}
        </span>
        <span v-else class="status status--ok">
          <i class="status__dot"></i>{{ t('audit.passed') }}
        </span>
      </template>
    </template>

    <template #expandedRowRender="{ record }">
      <div class="detail">
        <div class="detail__meta">
          <span class="detail__k">{{ t('audit.latency') }}</span>
          <span class="detail__v">
            {{ record.latencyMs != null ? record.latencyMs + ' ms' : '—' }}
          </span>
          <span class="detail__k">{{ t('audit.grounding') }}</span>
          <span class="detail__v">{{ record.groundingSource || '—' }}</span>
        </div>
        <div class="detail__block">
          <span class="detail__k">{{ t('audit.question') }}</span>
          <pre class="detail__pre">{{ record.questionText || '—' }}</pre>
        </div>
        <div class="detail__block">
          <span class="detail__k">{{ t('audit.answer') }}</span>
          <pre class="detail__pre">{{ record.responseText || '—' }}</pre>
        </div>

        <!--
          检测判定与逐次调用按需取:一轮十几次调用、每次可能带全文,随列表下发会让一页几十行变成几兆。
          三态分开说 —— 在拉 / 拉失败 / 确实没有,合成一句「暂无」会让人以为查过了。
        -->
        <TrustDetailPanel v-if="record.requestId" :request-id="record.requestId" />
      </div>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { InboxOutlined } from '@ant-design/icons-vue'
import type { AiTrustItem } from '@/api'
import { fmtTime } from '../auditFormat'
import TrustDetailPanel from './TrustDetailPanel.vue'

defineProps<{
  /** 当前页的信任遥测行。 */
  rows: AiTrustItem[]
  /** 数据加载中。 */
  loading: boolean
}>()

const { t } = useI18n()

const columns = computed(() => [
  { title: t('audit.colTime'), key: 'createdAt', width: 165 },
  { title: t('audit.colAgent'), key: 'agentKey', width: 150 },
  { title: t('audit.colSource'), key: 'trustSource', width: 120 },
  { title: t('audit.colModel'), key: 'model', ellipsis: true },
  { title: t('audit.colBlocked'), key: 'blocked', width: 120 },
])
</script>

<style scoped lang="scss">
@use './_auditTable.scss';
</style>
