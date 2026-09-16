<!--
  ChannelIdentityFields —— 渠道的标识与归属:凭据键、归属产品、显示名、作用域,以及启用开关。

  凭据键与归属产品在编辑态锁死:它们是这条凭据的身份,下游按 (targetSystem, credKey)
  找它 —— 改了等于凭空造出一条新凭据,而引用它的那些配置还指着旧的。

  成对短字段两列并排:四个字段各占一行会让弹窗白白长出一屏,而它们本来就短。
-->
<template>
  <section class="ce-section">
    <h4 class="ce-section-title">{{ $t('notificationChannels.identitySection') }}</h4>

    <div class="ce-grid2">
      <div class="ce-field">
        <label class="ce-label ce-label--req">
          {{ $t('notificationChannels.field.credKey') }}
        </label>
        <a-input v-model:value="credKey" :disabled="locked" placeholder="ops-alert-group" />
      </div>

      <div class="ce-field">
        <label class="ce-label ce-label--req">
          {{ $t('notificationChannels.field.targetSystem') }}
        </label>
        <a-select v-model:value="targetSystem" :disabled="locked" :options="systemOptions" />
      </div>

      <div class="ce-field">
        <label class="ce-label">{{ $t('notificationChannels.field.displayName') }}</label>
        <a-input
          v-model:value="displayName"
          :placeholder="$t('notificationChannels.field.displayNamePh')"
        />
      </div>

      <div class="ce-field">
        <label class="ce-label">{{ $t('notificationChannels.field.scopeRef') }}</label>
        <a-input
          v-model:value="scopeRef"
          :disabled="locked"
          :placeholder="$t('notificationChannels.field.scopeRefPh')"
        />
      </div>
    </div>

    <p class="ce-hint">{{ $t('notificationChannels.hint.credKey') }}</p>

    <div class="ce-enable">
      <a-switch v-model:checked="enabled" size="small" />
      <span class="ce-enable-label">{{ $t('notificationChannels.field.enabled') }}</span>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  /** 编辑态:身份字段已定,不给改(见组件头)。 */
  locked?: boolean
}>()

const credKey = defineModel<string>('credKey', { required: true })
const targetSystem = defineModel<string>('targetSystem', { required: true })
const displayName = defineModel<string>('displayName', { required: true })
const scopeRef = defineModel<string>('scopeRef', { required: true })
const enabled = defineModel<boolean>('enabled', { required: true })

/** 归属产品只有这两个有通知能力 —— 循迹与认证中心自身不发通知。 */
const systemOptions = [
  { label: 'bi', value: 'bi' },
  { label: 'agent', value: 'agent' },
]
</script>

<style scoped lang="scss">
.ce-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 段标题:轻描淡写的小号大写标签,分组而不抢戏 */
.ce-section-title {
  margin: 0;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--ds-text-faint);
}

/* ── 字段 ── */
.ce-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ce-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ds-text-soft);
  line-height: 1.3;
}

/* 必填星号跟在文字后面 —— 前置星号会让一列标签的左边缘参差不齐 */
.ce-label--req::after {
  content: ' *';
  color: var(--ds-danger);
}

/* 成对短字段两列并排 */
.ce-grid2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 14px;
}

.ce-hint {
  margin: 0;
  font-size: 11.5px;
  line-height: 1.5;
  color: var(--ds-text-faint);
}

.ce-enable {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 2px;
}

.ce-enable-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ds-text-soft);
}
</style>
