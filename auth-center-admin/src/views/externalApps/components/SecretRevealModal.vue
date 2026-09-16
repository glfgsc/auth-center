<!--
  一次性密钥展示 —— 新建密钥后唯一能看到明文的时机,关掉就再也取不回。
  故 mask-closable=false:防止误点遮罩关掉后密钥丢失。
-->
<template>
  <a-modal
    :open="open"
    :title="$t('externalApps.secretRevealTitle')"
    :width="480"
    :footer="null"
    :mask-closable="false"
    @update:open="emit('update:open', $event)"
  >
    <a-alert
      type="warning"
      show-icon
      :message="$t('externalApps.secretRevealWarning')"
      style="margin-bottom: 16px"
    />
    <div v-for="f in fields" :key="f.label" class="ea-reveal">
      <span class="ea-reveal__label">{{ f.label }}</span>
      <div class="ea-reveal__row">
        <code class="ea-reveal__val" :class="{ 'ea-reveal__val--secret': f.secret }">
          {{ f.value }}
        </code>
        <a-button size="small" type="text" @click="emit('copy', f.value)"><CopyOutlined /></a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined } from '@ant-design/icons-vue'
import type { SecretReveal } from '@/api'

const props = defineProps<{
  /** 弹窗开关。 */
  open: boolean
  /** 后端刚签发的密钥三元组。 */
  reveal: SecretReveal
}>()

const emit = defineEmits<{
  'update:open': [open: boolean]
  copy: [text: string]
}>()

/** 展示项:只有 Secret Value 是真正的机密,单独着色提醒。 */
const fields = computed(() => [
  { label: 'Client ID', value: props.reveal.clientId ?? '', secret: false },
  { label: 'Secret ID', value: props.reveal.secretId ?? '', secret: false },
  { label: 'Secret Value', value: props.reveal.secretValue ?? '', secret: true },
])
</script>

<style scoped lang="scss">
.ea-reveal {
  margin-bottom: 12px;
}
.ea-reveal__label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--ds-text-muted);
  margin-bottom: 4px;
}
.ea-reveal__row {
  display: flex;
  align-items: center;
  gap: 6px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: 6px;
  padding: 6px 10px;
}
.ea-reveal__val {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  color: var(--ds-text);
  word-break: break-all;
  &--secret {
    color: #d46b08;
    font-weight: 600;
  }
}
</style>
