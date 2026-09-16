<!-- SsoBehaviorCard — 行为设置分区:自动建号 / 单点登出开关。 -->
<template>
  <SectionCard :title="$t('sso.sectionBehavior')" :icon="ControlOutlined">
    <div class="toggle-list">
      <div class="toggle-row">
        <div class="toggle-row__info">
          <div class="toggle-row__name">{{ $t('sso.autoCreateLabel') }}</div>
          <div class="toggle-row__desc">{{ $t('sso.autoCreateHint') }}</div>
        </div>
        <a-switch v-model:checked="form.autoCreate" />
      </div>
      <div class="toggle-row">
        <div class="toggle-row__info">
          <div class="toggle-row__name">{{ $t('sso.sloLabel') }}</div>
          <div class="toggle-row__desc">{{ $t('sso.sloHint') }}</div>
        </div>
        <a-switch v-model:checked="form.singleLogout" />
      </div>
    </div>
  </SectionCard>
</template>

<script setup lang="ts">
import { inject } from 'vue'
import { ControlOutlined } from '@ant-design/icons-vue'

import { SSO_FORM_KEY } from '../composables/useSsoConfig'
import SectionCard from '@/components/common/SectionCard.vue'

const form = inject(SSO_FORM_KEY)!
</script>

<style scoped lang="scss">
.toggle-list {
  display: flex;
  flex-direction: column;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 0;

  & + & {
    border-top: 1px solid var(--ds-border-soft);
  }

  &:first-child {
    padding-top: 0;
  }

  &:last-child {
    padding-bottom: 0;
  }
}

.toggle-row__info {
  flex: 1;
  min-width: 0;
}

.toggle-row__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ds-text);
  line-height: 1.4;
}

.toggle-row__desc {
  font-size: 12px;
  color: var(--ds-text-muted);
  margin-top: 3px;
  line-height: 1.5;
}
</style>
