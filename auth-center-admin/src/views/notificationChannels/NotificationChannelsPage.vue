<!--
  通知渠道。
  机器人 webhook / SMTP 等静态凭据的唯一落点:洞察的订阅摘要与指标告警、知数的流程通知步骤
  都按引用名取用它,各产品自己不再存地址。

  归属产品按顶栏产品档收窄。渠道必须归属某个产品,故本页不接受「全局」档
  (见 useProductScope 的 GLOBAL_SCOPE_PAGES)。

  秘密只进不出:列表与详情里的凭据内容恒为掩码,编辑时留空即保持原值。
-->
<template>
  <div class="ac-page nc-page">
    <PageHeader :title="$t('notificationChannels.title')" />

    <div class="nc-toolbar">
      <span class="nc-count">{{
        $t('notificationChannels.totalCount', { n: channels.length })
      }}</span>
      <a-button type="primary" @click="openCreate()">
        <template #icon><PlusOutlined /></template>
        {{ $t('notificationChannels.add') }}
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <!--
        空态不写「还没有渠道」就完事 —— 那句话信息量为零,首次进来的人真正要知道的是
        「这里能接什么」。直接把后端下发的类型摆成入口,点一下带着类型进抽屉。
      -->
      <div v-if="!channels.length && !loading" class="nc-empty">
        <p class="nc-empty-title">{{ $t('notificationChannels.empty') }}</p>
        <p class="nc-empty-hint">{{ $t('notificationChannels.emptyPick') }}</p>
        <div class="nc-empty-types">
          <button
            v-for="tp in types"
            :key="tp.name"
            type="button"
            class="nc-empty-type"
            @click="openCreate(tp.name)"
          >
            {{ tp.displayName }}
          </button>
        </div>
      </div>

      <table v-else-if="channels.length" class="nc-table">
        <thead>
          <tr>
            <th>{{ $t('notificationChannels.col.name') }}</th>
            <th class="nc-col-owner">{{ $t('notificationChannels.col.owner') }}</th>
            <th class="nc-col-type">{{ $t('notificationChannels.col.type') }}</th>
            <th class="nc-col-state">{{ $t('notificationChannels.col.state') }}</th>
            <th class="nc-col-test">{{ $t('notificationChannels.col.lastTest') }}</th>
            <th class="nc-col-actions"></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in channels" :key="row.id">
            <td>
              <div class="nc-name">{{ row.displayName || row.credKey }}</div>
              <div class="nc-key">{{ row.credKey }}</div>
            </td>
            <td class="nc-col-owner">
              <span class="nc-pill">{{ row.targetSystem }}</span>
              <span v-if="row.scopeRef" class="nc-scope">{{ row.scopeRef }}</span>
            </td>
            <td class="nc-col-type">
              <span class="nc-pill">{{ typeLabel(row.credType) }}</span>
            </td>
            <td class="nc-col-state">
              <span class="nc-state" :class="row.enabled === 0 ? 'is-off' : 'is-on'">
                {{
                  row.enabled === 0
                    ? $t('notificationChannels.state.disabled')
                    : $t('notificationChannels.state.enabled')
                }}
              </span>
            </td>
            <td class="nc-col-test">
              <template v-if="row.lastTestStatus">
                <span
                  class="nc-state"
                  :class="row.lastTestStatus === 'OK' ? 'is-ok' : 'is-bad'"
                  :title="row.lastTestMessage || ''"
                >
                  {{
                    row.lastTestStatus === 'OK'
                      ? $t('notificationChannels.state.testOk')
                      : $t('notificationChannels.state.testFailed')
                  }}
                </span>
                <span class="nc-test-at">{{ formatDateTime(row.lastTestAt) }}</span>
              </template>
              <span v-else class="nc-muted">—</span>
            </td>
            <td class="nc-col-actions">
              <div class="nc-actions">
                <a-button size="small" :loading="testingId === row.id" @click="test(row)">
                  {{ $t('notificationChannels.action.test') }}
                </a-button>
                <a-button size="small" @click="openEdit(row)">
                  {{ $t('common.edit') }}
                </a-button>
                <a-popconfirm
                  :title="$t('notificationChannels.confirmRemove')"
                  @confirm="remove(row)"
                >
                  <a-button size="small" danger>{{ $t('common.delete') }}</a-button>
                </a-popconfirm>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </a-spin>

    <ChannelEditorModal
      :open="editorOpen"
      :record="editorRecord"
      :preset-type="editorPresetType"
      :types="types"
      @close="editorOpen = false"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { PlusOutlined } from '@ant-design/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useNotificationChannels } from './composables/useNotificationChannels'
import ChannelEditorModal from './components/ChannelEditorModal.vue'

const {
  channels,
  types,
  loading,
  testingId,
  editorOpen,
  editorRecord,
  editorPresetType,
  load,
  openCreate,
  openEdit,
  remove,
  test,
} = useNotificationChannels()

function typeLabel(name: string): string {
  return types.value.find((t) => t.name === name)?.displayName ?? name
}

function formatDateTime(value?: string): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

function onSaved(): void {
  editorOpen.value = false
  load()
}
</script>

<style scoped lang="scss">
.nc-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.nc-count {
  font-size: 12px;
  color: var(--ds-text-faint);
}
.nc-toolbar .ant-btn {
  margin-left: auto;
}

/* ── 空态 ── */
.nc-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 24px 52px;
}
.nc-empty-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
}
.nc-empty-hint {
  margin: 5px 0 0;
  font-size: 12.5px;
  color: var(--ds-text-muted);
}
/* 限宽 —— 宽屏下不限宽这排会被拉成一条稀疏的横线。 */
.nc-empty-types {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 20px;
  max-width: 620px;
}
.nc-empty-type {
  padding: 9px 16px;
  font-size: 13px;
  color: var(--ds-text-soft);
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: 8px;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    color 0.15s ease;

  &:hover,
  &:focus-visible {
    border-color: var(--ds-primary);
    color: var(--ds-primary);
    outline: none;
  }
}

/* ── 表格 ── */
.nc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;

  thead th {
    text-align: left;
    padding: 7px 14px;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.04em;
    color: var(--ds-text-faint);
    background: var(--ds-bg-soft);
    border-bottom: 1px solid var(--ds-border-soft);
    white-space: nowrap;
  }

  td {
    padding: 9px 14px;
    vertical-align: middle;
    border-top: 1px solid var(--ds-border-soft);
  }

  tbody tr:hover {
    background: var(--ds-bg-soft);
  }
}

.nc-col-owner {
  width: 190px;
}
.nc-col-type {
  width: 150px;
}
.nc-col-state {
  width: 96px;
}
.nc-col-test {
  width: 200px;
}
.nc-col-actions {
  width: 190px;
  text-align: right;
  white-space: nowrap;
}

/* 三个内联操作(测试/编辑/删除)用同一种默认小按钮 —— 与外部应用页一致。
   都不带 type="text":text 型的 danger 按钮 hover 是实心红块,和另两个无边框文字对不齐;
   默认按钮三个都有淡边框、danger 只让删除变红,视觉统一。 */
.nc-actions {
  display: inline-flex;
  gap: 6px;
}

.nc-name {
  font-weight: 550;
  color: var(--ds-text);
  line-height: 1.3;
}
.nc-key {
  font-size: 11px;
  color: var(--ds-text-faint);
  font-family: ui-monospace, monospace;
}

.nc-pill {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  background: var(--ds-bg-soft);
  color: var(--ds-text-muted);
  border: 1px solid var(--ds-border-soft);
  white-space: nowrap;
}

.nc-scope {
  margin-left: 6px;
  font-size: 11px;
  color: var(--ds-text-faint);
  font-family: ui-monospace, monospace;
}

.nc-state {
  display: inline-block;
  padding: 1px 9px;
  border-radius: 999px;
  font-size: 11.5px;
  font-weight: 500;
  white-space: nowrap;

  &.is-on,
  &.is-ok {
    color: var(--ds-success-soft-text);
    background: var(--ds-success-soft);
    border: 1px solid var(--ds-success-soft-border);
  }
  &.is-bad {
    color: var(--ds-danger-soft-text);
    background: var(--ds-danger-soft);
    border: 1px solid var(--ds-danger-soft-border);
  }
  &.is-off {
    color: var(--ds-text-muted);
    background: var(--ds-bg-soft);
    border: 1px solid var(--ds-border-soft);
  }
}

.nc-test-at {
  margin-left: 8px;
  font-size: 11px;
  color: var(--ds-text-faint);
}

.nc-muted {
  color: var(--ds-text-faint);
}
</style>
