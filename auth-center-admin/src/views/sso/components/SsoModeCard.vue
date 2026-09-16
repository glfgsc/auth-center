<!--
  SsoModeCard — 登录模式分区:三种模式的紧凑单选行(原生 radio 保 a11y,
  视觉为整行可点的选择态卡片,单一品牌色,不做逐模式彩色)。
-->
<template>
  <SectionCard
    :title="$t('sso.modeLabel')"
    :icon="LoginOutlined"
  >
    <div class="mode-list" role="radiogroup" :aria-label="$t('sso.modeLabel')">
      <label
        v-for="mode in modes"
        :key="mode.value"
        class="mode-row"
        :class="{ 'mode-row--active': form.loginMode === mode.value }"
      >
        <input
          v-model="form.loginMode"
          type="radio"
          class="mode-row__input"
          name="sso-login-mode"
          :value="mode.value"
        />
        <span class="mode-row__icon"><component :is="mode.icon" /></span>
        <span class="mode-row__text">
          <span class="mode-row__title">{{ mode.label }}</span>
          <span class="mode-row__desc">{{ mode.desc }}</span>
        </span>
        <CheckCircleFilled v-if="form.loginMode === mode.value" class="mode-row__check" />
      </label>
    </div>
  </SectionCard>
</template>

<script setup lang="ts">
import { computed, inject, markRaw } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  CheckCircleFilled, LockOutlined, LoginOutlined, StopOutlined, SwapOutlined,
} from '@ant-design/icons-vue'

import type { IdpLoginMode } from '@/api'
import { SSO_FORM_KEY } from '../composables/useSsoConfig'
import SectionCard from '@/components/common/SectionCard.vue'

const { t } = useI18n()
const form = inject(SSO_FORM_KEY)!

const modes = computed(() => [
  { value: 'mixed' as IdpLoginMode, label: t('sso.modeMixed'), desc: t('sso.modeMixedHint'), icon: markRaw(SwapOutlined) },
  { value: 'enforced' as IdpLoginMode, label: t('sso.modeEnforced'), desc: t('sso.modeEnforcedHint'), icon: markRaw(LockOutlined) },
  { value: 'disabled' as IdpLoginMode, label: t('sso.modeDisabled'), desc: t('sso.modeDisabledHint'), icon: markRaw(StopOutlined) },
])
</script>

<style scoped lang="scss">
.mode-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mode-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    background: var(--ds-hover-bg);
  }

  &--active {
    border-color: var(--ds-primary);
    background: var(--ds-primary-soft);

    &:hover {
      background: var(--ds-primary-soft);
    }
  }
}

/* 原生 radio 仅保留给键盘/读屏,视觉隐藏;focus 时给整行画 ring。 */
.mode-row__input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.mode-row:has(.mode-row__input:focus-visible) {
  box-shadow: 0 0 0 3px var(--ds-primary-soft-border);
}

.mode-row__icon {
  width: 34px;
  height: 34px;
  border-radius: var(--ds-radius-sm);
  background: var(--ds-neutral-soft);
  color: var(--ds-text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;

  .mode-row--active & {
    background: var(--ds-card-bg);
    color: var(--ds-primary-soft-text);
  }
}

.mode-row__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mode-row__title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ds-text);
  line-height: 1.4;
}

.mode-row__desc {
  font-size: 12px;
  color: var(--ds-text-muted);
  line-height: 1.5;
}

.mode-row__check {
  color: var(--ds-primary);
  font-size: 16px;
  flex-shrink: 0;
}
</style>
