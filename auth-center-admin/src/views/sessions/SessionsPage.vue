<!--
  SessionsView — 会话与登录安全。
  两个板块:活跃会话(在线登录会话,可吊销/按用户踢下线)+ 登录历史(每次登录尝试留痕 + 异常检测)。
  会话在登录时登记(refresh 族 ID 为主键,跨 access 续期稳定),只追踪部署后的新登录。
-->
<template>
  <div class="ac-page sessions-page">
    <PageHeader :title="t('sessions.title')" :description="t('sessions.subtitle')" />

    <!-- ── 下划线 Tab ── -->
    <nav class="sess-tabs" role="tablist">
      <button
        v-for="td in tabDefs"
        :key="td.key"
        type="button"
        role="tab"
        class="sess-tab"
        :class="{ 'sess-tab--active': tab === td.key }"
        :aria-selected="tab === td.key"
        @click="tab = td.key"
      >
        <component :is="td.icon" class="sess-tab__icon" />
        {{ td.label }}
      </button>
    </nav>

    <ActiveSessionsPanel v-if="tab === 'active'" />
    <LoginHistoryPanel v-else />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { DesktopOutlined, HistoryOutlined } from '@ant-design/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import ActiveSessionsPanel from './components/ActiveSessionsPanel.vue'
import LoginHistoryPanel from './components/LoginHistoryPanel.vue'

const { t } = useI18n()

const tab = ref<'active' | 'history'>('active')

const tabDefs = computed(() => [
  { key: 'active' as const, label: t('sessions.tabActive'), icon: markRaw(DesktopOutlined) },
  { key: 'history' as const, label: t('sessions.tabHistory'), icon: markRaw(HistoryOutlined) },
])
</script>

<style scoped lang="scss">
/* ── 下划线 Tab(与审计控制台同构) ── */
.sess-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--ds-border);
}
.sess-tab {
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
.sess-tab__icon { font-size: 15px; }
</style>

<!-- 面板共用视觉原子(非 scoped,统一命名空间到 .sessions-page,供两个子面板复用) -->
<style lang="scss">
.sessions-page {
  /* ── 工具条 ── */
  .sess-toolbar {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-bottom: 14px;
  }
  .sess-toolbar__stat {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    margin-left: auto;
    font-size: 13px;
    color: var(--ds-text-muted);
  }
  .sess-toolbar__dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: var(--ds-success);
    box-shadow: 0 0 0 3px var(--ds-success-soft);
  }

  /* ── 表卡片 ── */
  .sess-card {
    background: var(--ds-card-bg);
    border: 1px solid var(--ds-border);
    border-radius: var(--ds-radius);
    box-shadow: var(--ds-shadow-sm);
    overflow: hidden;
  }

  /* a-table 精修:去外框、克制表头、行悬浮、留白 */
  .session-table {
    .ant-table {
      background: transparent;
      font-size: 13px;
    }
    .ant-table-thead > tr > th {
      background: transparent;
      color: var(--ds-text-faint);
      font-weight: 600;
      font-size: 12px;
      letter-spacing: 0.02em;
      border-bottom: 1px solid var(--ds-border);
      padding: 11px 16px;
      &::before { display: none !important; }
    }
    .ant-table-tbody > tr > td {
      border-bottom: 1px solid var(--ds-divider);
      padding: 13px 16px;
      transition: background 0.12s;
    }
    .ant-table-tbody > tr:last-child > td { border-bottom: none; }
    .ant-table-tbody > tr.ant-table-row:hover > td { background: var(--ds-hover-bg); }
  }

  /* ── 单元格原子 ── */
  .sess-user {
    display: inline-flex;
    align-items: center;
    gap: 9px;
    flex-wrap: wrap;
  }
  .sess-user__avatar {
    width: 26px;
    height: 26px;
    border-radius: 50%;
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    font-size: 12px;
    font-weight: 600;
    background: var(--ds-primary-soft);
    color: var(--ds-primary-soft-text);
  }
  .sess-user__name { font-weight: 500; color: var(--ds-text); }

  .sess-device,
  .sess-loc {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    color: var(--ds-text-soft);
  }
  .sess-device__icon,
  .sess-loc__icon { font-size: 14px; color: var(--ds-text-muted); }

  .mono {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    color: var(--ds-text-soft);
  }
  .sess-time {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12px;
    color: var(--ds-text-muted);
    white-space: nowrap;
    &--faint { color: var(--ds-text-faint); }
  }
  .sess-dash { color: var(--ds-text-faint); }

  /* ── 软色胶囊 ── */
  .pill {
    display: inline-flex;
    align-items: center;
    height: 20px;
    padding: 0 8px;
    border-radius: 6px;
    font-size: 11.5px;
    font-weight: 500;
    line-height: 1;
    border: 1px solid transparent;
    white-space: nowrap;

    &--xs { height: 18px; padding: 0 6px; font-size: 11px; }

    &--primary { background: var(--ds-primary-soft); color: var(--ds-primary-soft-text); border-color: var(--ds-primary-soft-border); }
    &--info    { background: var(--ds-info-soft);    color: var(--ds-info-soft-text);    border-color: var(--ds-info-soft-border); }
    &--success { background: var(--ds-success-soft); color: var(--ds-success-soft-text); border-color: var(--ds-success-soft-border); }
    &--warning { background: var(--ds-warning-soft); color: var(--ds-warning-soft-text); border-color: var(--ds-warning-soft-border); }
    &--danger  { background: var(--ds-danger-soft);  color: var(--ds-danger-soft-text);  border-color: var(--ds-danger-soft-border); }
    &--neutral { background: var(--ds-neutral-soft); color: var(--ds-neutral-soft-text); border-color: var(--ds-neutral-soft-border); }
  }

  /* ── 状态点 ── */
  .status {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    white-space: nowrap;
    &__dot { width: 6px; height: 6px; border-radius: 50%; }
    &--ok { color: var(--ds-success); .status__dot { background: var(--ds-success); } }
    &--err { color: var(--ds-danger); .status__dot { background: var(--ds-danger); } }
  }

  /* ── 空态 ── */
  .sess-empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 48px 0;
    color: var(--ds-text-faint);
    font-size: 13px;
  }
  .sess-empty__icon { font-size: 30px; opacity: 0.5; }

  /* ── 页脚 ── */
  .sess-foot {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    border-top: 1px solid var(--ds-border);
  }
  .sess-foot__total { font-size: 12.5px; color: var(--ds-text-muted); }
}
</style>
