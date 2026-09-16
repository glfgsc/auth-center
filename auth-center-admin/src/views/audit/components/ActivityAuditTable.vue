<!--
  活动审计表 —— 认证中心 + BI + 循迹 汇入的「谁改了什么」。
  只读:由中心切面与各系统推送写入,本组件不发起任何写操作。
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
      <template v-else-if="column.key === 'source'">
        <span class="pill" :class="`pill--${srcTone(record.sourceSystem)}`">
          {{ sourceLabel(record.sourceSystem) }}
        </span>
      </template>
      <template v-else-if="column.key === 'actor'">
        <span class="audit-actor">
          <span class="audit-actor__avatar">{{ actorInitial(record.actorUsername) }}</span>
          {{ record.actorUsername || t('audit.unknownActor') }}
        </span>
      </template>
      <template v-else-if="column.key === 'module'">
        <span class="mono">{{ record.module || '—' }}</span>
        <span v-if="record.sensitiveOp === 1" class="pill pill--danger pill--xs">
          {{ t('audit.sensitive') }}
        </span>
      </template>
      <template v-else-if="column.key === 'operation'">
        <span class="pill" :class="`pill--${opTone(record.operationType)}`">
          {{ record.operationType || record.operation || '—' }}
        </span>
      </template>
      <template v-else-if="column.key === 'target'">
        <span class="mono mono--soft">
          {{ record.targetName || record.targetType || record.targetId || '—' }}
        </span>
      </template>
      <template v-else-if="column.key === 'status'">
        <span class="status" :class="record.status === 200 ? 'status--ok' : 'status--err'">
          <i class="status__dot"></i>
          {{ record.status === 200 ? t('audit.ok') : String(record.status ?? '—') }}
        </span>
      </template>
    </template>

    <template #expandedRowRender="{ record }">
      <div class="detail">
        <div class="detail__meta">
          <span class="detail__k">{{ t('audit.path') }}</span>
          <code class="detail__code">
            {{ record.method }} {{ record.path || record.operation || '—' }}
          </code>
          <span class="detail__k">{{ t('audit.ip') }}</span>
          <span class="detail__v">{{ record.ip || '—' }}</span>
          <span class="detail__k">{{ t('audit.duration') }}</span>
          <span class="detail__v">
            {{ record.durationMs != null ? record.durationMs + ' ms' : '—' }}
          </span>
          <template v-if="record.workspaceId != null">
            <span class="detail__k">WS</span>
            <span class="detail__v">{{ record.workspaceId }}</span>
          </template>
        </div>
        <div v-if="record.errorMsg" class="detail__error">
          <span class="detail__k">{{ t('audit.error') }}</span>
          <span>{{ record.errorMsg }}</span>
        </div>
        <div v-if="record.oldValue || record.newValue" class="detail__block">
          <span class="detail__k">{{ t('audit.diff') }}</span>
          <pre class="detail__pre">旧: {{ record.oldValue || '—' }}
新: {{ record.newValue || '—' }}</pre>
        </div>
        <div class="detail__block">
          <span class="detail__k">{{ t('audit.payload') }}</span>
          <pre class="detail__pre">{{ record.params || '—' }}</pre>
        </div>
      </div>
    </template>
  </a-table>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { InboxOutlined } from '@ant-design/icons-vue'
import type { AuditLogItem } from '@/api'
import { fmtTime } from '../auditFormat'

defineProps<{
  /** 当前页的审计行。 */
  rows: AuditLogItem[]
  /** 数据加载中。 */
  loading: boolean
}>()

const { t } = useI18n()

const columns = computed(() => [
  { title: t('audit.colTime'), key: 'createdAt', width: 165 },
  { title: t('audit.colSource'), key: 'source', width: 96 },
  { title: t('audit.colActor'), key: 'actor', width: 150 },
  { title: t('audit.colModule'), key: 'module', width: 160 },
  { title: t('audit.colOperation'), key: 'operation', width: 118 },
  { title: t('audit.colTarget'), key: 'target', ellipsis: true },
  { title: t('audit.colStatus'), key: 'status', width: 96 },
])

/** 来源 → 软色调:中心=品牌 / BI=信息蓝 / 循迹=成功绿。 */
function srcTone(s?: string): string {
  if (s === 'bi') return 'info'
  if (s === 'tracking') return 'success'
  if (s === 'auth_center') return 'primary'
  return 'neutral'
}

/** 操作类型 → 软色调:增=绿 / 删撤=红 / 其余=中性。 */
function opTone(op?: string): string {
  if (op === 'delete' || op === 'revoke_secret' || op === 'unassign' || op === 'remove_member')
    return 'danger'
  if (op === 'create' || op === 'add_member' || op === 'generate_secret' || op === 'assign')
    return 'success'
  return 'info'
}

function sourceLabel(s?: string): string {
  return t(`audit.source.${s}`, s || '—')
}

function actorInitial(name?: string): string {
  return (name || '?').charAt(0).toUpperCase()
}
</script>

<style scoped lang="scss">
@use './_auditTable.scss';
</style>
