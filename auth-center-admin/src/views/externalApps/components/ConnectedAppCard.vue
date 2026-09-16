<!--
  单个外部应用(Connected App)卡片 —— 基本信息 + 认证方式 + 密钥列表。
  所有写操作都以事件上抛给页面统一处理,本组件不直接调 API。
-->
<template>
  <div class="tn-card ea-card" :class="{ 'ea-card--off': app.status === 'disabled' }">
    <div class="ea-card__head">
      <span class="ea-card__name">{{ app.name }}</span>
      <a-tag :color="app.status === 'enabled' ? 'green' : 'default'">
        {{ app.status === 'enabled' ? $t('externalApps.enabled') : $t('externalApps.disabled') }}
      </a-tag>
      <a-tag class="ea-sys-tag">{{ systemLabel(app.targetSystem) }}</a-tag>
      <div class="ea-spacer" />
      <a-tooltip
        :title="app.status === 'enabled' ? $t('externalApps.disable') : $t('externalApps.enable')"
      >
        <a-button size="small" :loading="toggling" @click="emit('toggle', app)">
          <PauseCircleOutlined v-if="app.status === 'enabled'" />
          <PlayCircleOutlined v-else />
        </a-button>
      </a-tooltip>
      <a-tooltip :title="$t('common.edit')">
        <a-button size="small" @click="emit('edit', app)"><EditOutlined /></a-button>
      </a-tooltip>
      <a-popconfirm :title="$t('externalApps.deleteConfirm')" @confirm="emit('remove', app.id)">
        <a-button size="small" danger><DeleteOutlined /></a-button>
      </a-popconfirm>
    </div>

    <div class="ea-info">
      <span class="ea-info__label">Client ID</span>
      <code class="ea-info__val">{{ app.clientId }}</code>
      <a-button size="small" type="text" @click="emit('copy', app.clientId)">
        <CopyOutlined />
      </a-button>
    </div>
    <div class="ea-info">
      <span class="ea-info__label">{{ $t('externalApps.domains') }}</span>
      <span class="ea-info__val">
        {{ formatDomains(app.allowedDomains, $t('externalApps.noDomains')) }}
      </span>
    </div>
    <div class="ea-info">
      <span class="ea-info__label">{{ $t('externalApps.authMethod') }}</span>
      <span class="ea-auth-badges">
        <span class="ea-badge">{{ $t('externalApps.authServer') }}</span>
        <span v-if="app.hasPublicKey" class="ea-badge ea-badge--sso">
          {{ $t('externalApps.directTrust') }}
        </span>
      </span>
    </div>

    <div class="ea-secrets">
      <div class="ea-secrets__head">
        <KeyOutlined class="ea-secrets__icon" />
        <span class="ea-secrets__title">{{ $t('externalApps.secrets') }}</span>
        <span class="ea-secrets__count">{{ app.secretCount ?? 0 }} / {{ MAX_SECRETS }}</span>
        <div class="ea-spacer" />
        <a-button
          size="small"
          :disabled="(app.secretCount ?? 0) >= MAX_SECRETS"
          :loading="rotating"
          @click="emit('generate-secret', app)"
        >
          <template #icon><PlusOutlined /></template>
          {{ $t('externalApps.newSecret') }}
        </a-button>
      </div>
      <div v-for="sec in app.secrets || []" :key="sec.secretId" class="ea-secret-row">
        <code class="ea-secret-row__id">{{ sec.secretId }}</code>
        <span class="ea-secret-row__date">{{ formatDate(sec.createdAt) }}</span>
        <div class="ea-spacer" />
        <a-popconfirm
          :title="$t('externalApps.revokeConfirm')"
          @confirm="emit('revoke-secret', app.id, sec.secretId)"
        >
          <a-button size="small" danger type="text"><StopOutlined /></a-button>
        </a-popconfirm>
      </div>
      <div v-if="!app.secrets?.length" class="ea-secret-row ea-secret-row--empty">
        {{ $t('externalApps.noSecrets') }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import {
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  KeyOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  StopOutlined,
} from '@ant-design/icons-vue'
import type { ConnectedAppItem } from '@/api'
import { formatDate, formatDomains } from '../externalAppsFormat'

/**
 * 单个应用同时可持有的密钥数上限 —— 与后端轮换策略一致:留两把才能「先发新、再撤旧」
 * 地无缝轮换。调大会让撤销遗漏的旧密钥长期有效。
 */
const MAX_SECRETS = 2

defineProps<{
  /** 应用行。 */
  app: ConnectedAppItem
  /** 该应用的启停请求进行中。 */
  toggling: boolean
  /** 该应用的新建密钥请求进行中。 */
  rotating: boolean
}>()

const emit = defineEmits<{
  toggle: [app: ConnectedAppItem]
  edit: [app: ConnectedAppItem]
  remove: [id: number]
  copy: [text: string]
  'generate-secret': [app: ConnectedAppItem]
  'revoke-secret': [appId: number, secretId: string]
}>()

const { t, te } = useI18n()

/** 目标系统标签:优先 i18n(users.system.*),回退 code。 */
function systemLabel(code: string): string {
  const key = `users.system.${code}`
  return te(key) ? t(key) : code
}
</script>

<style scoped lang="scss">
@use './_externalApps.scss';

.ea-card {
  padding: 16px 18px;
  &--off {
    opacity: 0.6;
  }
}
.ea-card__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.ea-card__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
}
.ea-badge {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--ds-bg-soft);
  color: var(--ds-text-soft);
  &--sso {
    background: var(--ds-primary-soft);
    color: var(--ds-primary-soft-text);
  }
}
.ea-auth-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.ea-sys-tag {
  margin-inline-end: 0;
}

.ea-secrets {
  border-top: 1px solid var(--ds-border-soft);
  margin-top: 10px;
  padding-top: 10px;
}
.ea-secrets__head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}
.ea-secrets__icon {
  color: var(--ds-text-muted);
}
.ea-secrets__title {
  font-size: 12px;
  font-weight: 600;
  color: var(--ds-text-soft);
}
.ea-secrets__count {
  font-size: 11px;
  color: var(--ds-text-faint);
}
.ea-secret-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 0;
  & + & {
    border-top: 1px solid var(--ds-border-soft);
  }
}
.ea-secret-row__id {
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  color: var(--ds-text);
}
.ea-secret-row__date {
  font-size: 11px;
  color: var(--ds-text-faint);
}
</style>
