<!--
  SsoStatusAside — SSO 页右侧粘性状态栏。
  卡 1「连接状态」:当前模式 / 服务主机 / 协议摘要 + 测试连接(结果就地反馈)。
  卡 2「集成信息」:回调地址复制;强制 CAS 模式下附紧急后门说明。
-->
<template>
  <div class="aside-stack">
    <!-- 连接状态 -->
    <section class="aside-card">
      <h3 class="aside-card__title">{{ $t('sso.statusCard') }}</h3>

      <div class="status-mode">
        <span class="status-mode__dot" :class="`status-mode__dot--${form.loginMode}`" />
        <span class="status-mode__name">{{ form.name || 'CAS' }}</span>
        <span class="tn-pill" :class="modePillClass">{{ modeLabel }}</span>
      </div>

      <dl class="status-meta">
        <div class="status-meta__row">
          <dt>{{ $t('sso.statusServer') }}</dt>
          <dd :title="form.serverUrl || undefined">{{ serverHost }}</dd>
        </div>
        <div class="status-meta__row">
          <dt>{{ $t('sso.statusProtocol') }}</dt>
          <dd>CAS {{ form.protocolVersion }}</dd>
        </div>
      </dl>

      <a-button block :loading="testing" @click="$emit('test')">
        <template #icon><ApiOutlined /></template>
        {{ $t('sso.testConnection') }}
      </a-button>

      <div
        v-if="testResult"
        class="test-result"
        :class="testResult.ok ? 'test-result--ok' : 'test-result--fail'"
      >
        <CheckCircleFilled v-if="testResult.ok" class="test-result__icon" />
        <CloseCircleFilled v-else class="test-result__icon" />
        <span class="test-result__msg">{{ testResult.message }}</span>
      </div>
    </section>

    <!-- 集成信息 -->
    <section class="aside-card">
      <h3 class="aside-card__title">{{ $t('sso.integrationCard') }}</h3>

      <div v-if="callbackPattern" class="integration-block">
        <div class="integration-block__label">
          {{ $t('sso.callbackLabel') }}
          <span class="integration-block__hint">{{ $t('sso.callbackHint') }}</span>
        </div>
        <div class="integration-code">
          <code>{{ callbackPattern }}</code>
          <a-button size="small" type="text" :aria-label="$t('common.copy')" @click="copyText(callbackPattern)">
            <template #icon><CopyOutlined /></template>
          </a-button>
        </div>
      </div>

      <div v-if="form.loginMode === 'enforced'" class="integration-block integration-block--warning">
        <div class="integration-block__label">
          <WarningOutlined class="integration-block__warn-icon" />
          {{ $t('sso.backdoorTitle') }}
        </div>
        <p class="integration-block__desc">{{ $t('sso.backdoorBody') }}</p>
        <div class="integration-code">
          <code>{{ $t('sso.backdoorUrl') }}</code>
          <a-button size="small" type="text" :aria-label="$t('common.copy')" @click="copyText($t('sso.backdoorUrl'))">
            <template #icon><CopyOutlined /></template>
          </a-button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  ApiOutlined, CheckCircleFilled, CloseCircleFilled, CopyOutlined, WarningOutlined,
} from '@ant-design/icons-vue'

import type { IdpTestResult } from '@/api'
import { SSO_FORM_KEY } from '../composables/useSsoConfig'
import { copyText } from '@/utils/clipboard'

defineProps<{
  testing: boolean
  testResult: IdpTestResult | null
  callbackPattern: string
}>()

defineEmits<{ test: [] }>()

const { t } = useI18n()
const form = inject(SSO_FORM_KEY)!

const modeLabel = computed(() => {
  if (form.loginMode === 'disabled') return t('sso.modeDisabled')
  if (form.loginMode === 'enforced') return t('sso.modeEnforced')
  return t('sso.modeMixed')
})

const modePillClass = computed(() => {
  if (form.loginMode === 'disabled') return 'tn-pill-neutral'
  if (form.loginMode === 'enforced') return 'tn-pill-info'
  return 'tn-pill-success'
})

const serverHost = computed(() => {
  if (!form.serverUrl.trim()) return t('sso.notConfigured')
  try {
    return new URL(form.serverUrl).host
  } catch {
    return form.serverUrl
  }
})
</script>

<style scoped lang="scss">
.aside-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.aside-card {
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius);
  box-shadow: var(--ds-shadow-sm);
  padding: 16px;
}

.aside-card__title {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ds-text-faint);
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

/* ── 状态摘要 ── */
.status-mode {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.status-mode__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &--mixed { background: var(--ds-success); }
  &--enforced { background: var(--ds-info); }
  &--disabled { background: var(--ds-text-faint); }
}

.status-mode__name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-meta {
  margin: 0 0 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.status-meta__row {
  display: flex;
  align-items: baseline;
  gap: 10px;

  dt {
    font-size: 12px;
    color: var(--ds-text-faint);
    flex-shrink: 0;
    width: 56px;
  }

  dd {
    margin: 0;
    font-size: 12.5px;
    color: var(--ds-text-soft);
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

/* ── 测试结果(就地反馈) ── */
.test-result {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: var(--ds-radius-sm);
  border: 1px solid transparent;

  &--ok {
    background: var(--ds-success-soft);
    border-color: var(--ds-success-soft-border);

    .test-result__icon { color: var(--ds-success); }
    .test-result__msg { color: var(--ds-success-soft-text); }
  }

  &--fail {
    background: var(--ds-danger-soft);
    border-color: var(--ds-danger-soft-border);

    .test-result__icon { color: var(--ds-danger); }
    .test-result__msg { color: var(--ds-danger-soft-text); }
  }
}

.test-result__icon {
  font-size: 14px;
  margin-top: 2px;
  flex-shrink: 0;
}

.test-result__msg {
  font-size: 12.5px;
  line-height: 1.55;
  word-break: break-all;
}

/* ── 集成信息 ── */
.integration-block {
  & + & {
    margin-top: 14px;
    padding-top: 14px;
    border-top: 1px solid var(--ds-border-soft);
  }

  &--warning .integration-block__label {
    color: var(--ds-warning-soft-text);
  }
}

.integration-block__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  font-weight: 600;
  color: var(--ds-text-soft);
  margin-bottom: 6px;
}

.integration-block__hint {
  font-weight: 400;
  color: var(--ds-text-faint);
  font-size: 12px;
}

.integration-block__warn-icon {
  color: var(--ds-warning);
}

.integration-block__desc {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--ds-text-muted);
  line-height: 1.55;
}

.integration-code {
  display: flex;
  align-items: center;
  gap: 4px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: var(--ds-radius-sm);
  padding: 6px 8px;

  code {
    flex: 1;
    min-width: 0;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 11.5px;
    color: var(--ds-text-soft);
    word-break: break-all;
    line-height: 1.5;
  }
}
</style>
