<!-- SsoConnectionCard — 连接配置分区:显示名称 / 图标 / 服务地址 / 协议版本。 -->
<template>
  <SectionCard
    :title="$t('sso.sectionConnection')"
    :icon="CloudServerOutlined"
  >
    <div class="conn-grid">
      <div class="ac-field conn-grid__name">
        <label class="ac-field__label">{{ $t('sso.name') }}</label>
        <a-input v-model:value="form.name" placeholder="CAS" />
      </div>
      <div class="ac-field conn-grid__icon">
        <label class="ac-field__label">{{ $t('sso.icon') }}</label>
        <a-input v-model:value="form.icon" placeholder="&#x1F3E2;" />
      </div>
      <div class="ac-field conn-grid__full">
        <label class="ac-field__label">{{ $t('sso.casServer') }}</label>
        <a-input v-model:value="form.serverUrl" placeholder="https://cas.your-company.com/cas" />
        <span class="ac-field__hint">{{ $t('sso.casServerHint') }}</span>
      </div>
      <div class="ac-field conn-grid__proto">
        <label class="ac-field__label">{{ $t('sso.casProtocol') }}</label>
        <a-select v-model:value="form.protocolVersion">
          <a-select-option value="3.0">CAS 3.0 (/p3/serviceValidate)</a-select-option>
          <a-select-option value="2.0">CAS 2.0 (/serviceValidate)</a-select-option>
        </a-select>
        <span class="ac-field__hint">{{ $t('sso.casProtocolHint') }}</span>
      </div>
    </div>
  </SectionCard>
</template>

<script setup lang="ts">
import { inject } from 'vue'
import { CloudServerOutlined } from '@ant-design/icons-vue'

import { SSO_FORM_KEY } from '../composables/useSsoConfig'
import SectionCard from '@/components/common/SectionCard.vue'

const form = inject(SSO_FORM_KEY)!
</script>

<style scoped lang="scss">
.conn-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  gap: 16px;
}

.conn-grid__full {
  grid-column: 1 / -1;
}

.conn-grid__proto {
  grid-column: 1 / -1;
  max-width: 360px;
}
</style>
