<!--
  统一审计控制台。
  两个板块:活动审计(认证中心 + BI + 循迹 汇入,谁改了什么)+ AI 信任日志(agent 汇入,LLM 信任层遥测)。
  只读:活动审计由中心切面 + 各系统推送写入;AI 信任由 agent 推送写入。

  本文件只做编排:两个板块共用一套分页与加载态(收敛在 useAuditTrail),各自的表格与展开详情
  分别落在 ActivityAuditTable / TrustAuditTable。
-->
<template>
  <div class="ac-page audit-page">
    <PageHeader :title="t('audit.title')">
      <template #actions>
        <a-button :loading="loading" @click="reload">
          <template #icon><ReloadOutlined /></template>
          {{ t('common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <!-- ── 下划线 Tab ── -->
    <nav class="audit-tabs" role="tablist">
      <button
        v-for="td in tabDefs"
        :key="td.key"
        type="button"
        role="tab"
        class="audit-tab"
        :class="{ 'audit-tab--active': tab === td.key }"
        :aria-selected="tab === td.key"
        @click="switchTab(td.key)"
      >
        <component :is="td.icon" class="audit-tab__icon" />
        {{ td.label }}
      </button>
    </nav>

    <!-- ── 筛选工具条 ── -->
    <div class="audit-toolbar">
      <FilterOutlined class="audit-toolbar__lead" />
      <template v-if="tab === 'activity'">
        <!-- 来源系统的下拉去掉了:口径由顶栏产品切换器统一给,页内不再各留一份 -->
        <a-input
          v-model:value="af.actor"
          allow-clear
          :placeholder="t('audit.filterActor')"
          style="width: 156px"
          @press-enter="reload"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-input
          v-model:value="af.module"
          allow-clear
          :placeholder="t('audit.filterModule')"
          style="width: 128px"
          @press-enter="reload"
        />
        <a-input
          v-model:value="af.operationType"
          allow-clear
          :placeholder="t('audit.filterOperation')"
          style="width: 128px"
          @press-enter="reload"
        />
        <a-select
          v-model:value="af.status"
          allow-clear
          :placeholder="t('audit.filterStatus')"
          style="width: 108px"
          :options="statusOptions"
        />
        <a-range-picker v-model:value="dateRange" show-time />
        <div class="audit-toolbar__actions">
          <a-button type="primary" @click="reload">{{ t('audit.search') }}</a-button>
          <a-button @click="resetActivity">{{ t('audit.reset') }}</a-button>
        </div>
      </template>

      <template v-else>
        <a-input
          v-model:value="tf.agentKey"
          allow-clear
          :placeholder="t('audit.filterAgent')"
          style="width: 200px"
          @press-enter="reload"
        >
          <template #prefix><SearchOutlined /></template>
        </a-input>
        <a-select
          v-model:value="tf.source"
          allow-clear
          :placeholder="t('audit.filterTrustSource')"
          style="width: 168px"
          :options="trustSourceOptions"
        />
        <label class="audit-blocked">
          <a-switch v-model:checked="tf.blockedOnly" size="small" />
          {{ t('audit.blockedOnly') }}
        </label>
        <div class="audit-toolbar__actions">
          <a-button type="primary" @click="reload">{{ t('audit.search') }}</a-button>
          <a-button @click="resetTrust">{{ t('audit.reset') }}</a-button>
        </div>
      </template>
    </div>

    <!-- ── 表卡片 ── -->
    <div class="audit-card">
      <ActivityAuditTable v-if="tab === 'activity'" :rows="activityRows" :loading="loading" />
      <TrustAuditTable v-else :rows="trustRows" :loading="loading" />

      <!-- ── 页脚:合计 + 分页 ── -->
      <div class="audit-foot">
        <span class="audit-foot__total">{{ t('audit.totalCount', { n: total }) }}</span>
        <a-pagination
          v-model:current="page"
          v-model:page-size="size"
          :total="total"
          :show-size-changer="true"
          :page-size-options="['20', '50', '100']"
          size="small"
          @change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  AuditOutlined,
  FilterOutlined,
  ReloadOutlined,
  RobotOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useAuditTrail } from './composables/useAuditTrail'
import ActivityAuditTable from './components/ActivityAuditTable.vue'
import TrustAuditTable from './components/TrustAuditTable.vue'

const { t } = useI18n()

const {
  tab,
  loading,
  total,
  page,
  size,
  activityRows,
  af,
  dateRange,
  trustRows,
  tf,
  load,
  reload,
  switchTab,
  resetActivity,
  resetTrust,
} = useAuditTrail()

const tabDefs = computed(() => [
  { key: 'activity' as const, label: t('audit.tabActivity'), icon: markRaw(AuditOutlined) },
  { key: 'trust' as const, label: t('audit.tabTrust'), icon: markRaw(RobotOutlined) },
])

const statusOptions = computed(() => [
  { value: 'success', label: t('audit.statusSuccess') },
  { value: 'failed', label: t('audit.statusFailed') },
])
const trustSourceOptions = computed(() => [
  { value: 'RUNTIME', label: t('audit.trustSource.runtime') },
  { value: 'BI_UTILITY', label: t('audit.trustSource.bi') },
])
</script>

<style scoped lang="scss">
/* ── 下划线 Tab（与用户目录页同构） ── */
.audit-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--ds-border);
}
.audit-tab {
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

  &:hover { color: var(--ds-text); }
  &--active {
    color: var(--ds-primary);
    font-weight: 600;
    border-bottom-color: var(--ds-primary);
  }
}
.audit-tab__icon { font-size: 15px; }

/* ── 筛选工具条 ── */
.audit-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: var(--ds-radius);
}
.audit-toolbar__lead {
  color: var(--ds-text-faint);
  font-size: 14px;
}
.audit-toolbar__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.audit-blocked {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--ds-text-soft);
  cursor: pointer;
  user-select: none;
}

/* ── 表卡片 ── */
.audit-card {
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius);
  box-shadow: var(--ds-shadow-sm);
  overflow: hidden;
}

/* ── 页脚 ── */
.audit-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid var(--ds-border);
}
.audit-foot__total { font-size: 12.5px; color: var(--ds-text-muted); }
</style>
