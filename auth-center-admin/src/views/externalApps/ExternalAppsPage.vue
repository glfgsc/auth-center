<!--
  外部应用与密钥。
  外部应用(Connected App)注册 + 密钥轮换 + Direct-Trust 验签公钥,加签名公钥集(JWKS)查看。

  本文件只做编排:单个应用卡片、JWKS 面板、一次性密钥弹窗各自成组件,
  状态与写操作收敛在 useConnectedApps。
-->
<template>
  <div class="ac-page ea-page">
    <PageHeader :title="$t('externalApps.title')" />

    <div class="ea-toolbar">
      <span class="ea-count">{{ $t('externalApps.totalCount', { n: apps.length }) }}</span>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        {{ $t('externalApps.add') }}
      </a-button>
    </div>

    <a-spin :spinning="loading">
      <div v-if="!apps.length && !loading" class="ea-empty">{{ $t('externalApps.empty') }}</div>

      <div v-else class="ea-list">
        <ConnectedAppCard
          v-for="app in apps"
          :key="app.id"
          :app="app"
          :toggling="togglingId === app.id"
          :rotating="rotatingId === app.id"
          @toggle="toggleStatus"
          @edit="openEdit"
          @remove="removeApp"
          @copy="copyText"
          @generate-secret="generateSecret"
          @revoke-secret="revokeSecret"
        />
      </div>
    </a-spin>

    <JwksPanel :keys="jwksKeys" @copy="copyText" />

    <SecretRevealModal v-model:open="revealOpen" :reveal="reveal" @copy="copyText" />

    <ConnectedAppEditorModal
      :open="editorOpen"
      :record="editorRecord"
      @close="editorOpen = false"
      @saved="load"
      @reveal="showReveal"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import type { ConnectedAppItem } from '@/api'
import PageHeader from '@/components/common/PageHeader.vue'
import { useConnectedApps } from './composables/useConnectedApps'
import ConnectedAppCard from './components/ConnectedAppCard.vue'
import ConnectedAppEditorModal from './components/ConnectedAppEditorModal.vue'
import JwksPanel from './components/JwksPanel.vue'
import SecretRevealModal from './components/SecretRevealModal.vue'

const {
  apps,
  loading,
  togglingId,
  rotatingId,
  jwksKeys,
  revealOpen,
  reveal,
  load,
  showReveal,
  toggleStatus,
  removeApp,
  generateSecret,
  revokeSecret,
  copyText,
} = useConnectedApps()

/** 编辑弹窗是纯 UI 开关(record 为空即新建),不属于数据层,故留在页面。 */
const editorOpen = ref(false)
const editorRecord = ref<ConnectedAppItem | null>(null)

function openCreate(): void {
  editorRecord.value = null
  editorOpen.value = true
}

function openEdit(app: ConnectedAppItem): void {
  editorRecord.value = app
  editorOpen.value = true
}
</script>

<style scoped lang="scss">
.ea-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.ea-count {
  font-size: 12px;
  color: var(--ds-text-faint);
}
.ea-toolbar .ant-btn {
  margin-left: auto;
}

.ea-empty {
  padding: 48px 0;
  text-align: center;
  color: var(--ds-text-faint);
  font-size: 13px;
}

.ea-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
</style>
