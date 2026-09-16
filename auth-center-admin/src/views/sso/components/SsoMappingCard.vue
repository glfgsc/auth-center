<!-- SsoMappingCard — 属性映射分区:CAS 属性 → 本地用户档案字段。 -->
<template>
  <SectionCard
    :title="$t('sso.sectionAttributes')"
    :description="$t('sso.sectionAttributesDesc')"
    :icon="UserSwitchOutlined"
  >
    <div class="attr-grid">
      <div class="ac-field">
        <label class="ac-field__label">{{ $t('sso.attrUserName') }}</label>
        <a-input v-model:value="form.userAttribute" placeholder="user" />
      </div>
      <div class="ac-field">
        <label class="ac-field__label">{{ $t('sso.attrEmail') }}</label>
        <a-input v-model:value="form.emailAttribute" placeholder="email" />
      </div>
      <div class="ac-field">
        <label class="ac-field__label">{{ $t('sso.attrName') }}</label>
        <a-input v-model:value="form.nameAttribute" placeholder="displayName" />
      </div>
    </div>
  </SectionCard>
</template>

<script setup lang="ts">
import { inject } from 'vue'
import { UserSwitchOutlined } from '@ant-design/icons-vue'

import { SSO_FORM_KEY } from '../composables/useSsoConfig'
import SectionCard from '@/components/common/SectionCard.vue'

const form = inject(SSO_FORM_KEY)!
</script>

<style scoped lang="scss">
.attr-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}
</style>
