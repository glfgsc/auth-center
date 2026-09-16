<template>
  <div class="oauth-page">
    <PageHeader :title="$t('oauth.title')" :description="$t('oauth.subtitle')" />

    <a-spin :spinning="loading">
      <SectionCard :title="$t('oauth.settings')" :icon="SettingOutlined" class="oauth-card">
        <a-form layout="vertical" :model="settingsForm">
          <a-form-item :label="$t('oauth.issuer')" required>
            <a-input v-model:value="settingsForm.issuer" />
            <div class="hint">{{ $t('oauth.issuerHint') }}</div>
          </a-form-item>
          <a-form-item :label="$t('oauth.resource')" required>
            <a-input v-model:value="settingsForm.mcpResourceUrl" />
            <div class="hint">{{ $t('oauth.resourceHint') }}</div>
          </a-form-item>
          <a-row :gutter="16">
            <a-col :span="12">
          <a-form-item :label="$t('oauth.scopes')" required>
            <a-select
              v-model:value="settingsForm.scopes"
              mode="tags"
              :token-separators="[',', ' ']"
              :placeholder="$t('oauth.scopesPlaceholder')"
              style="width: 100%"
            />
            <div class="hint">{{ $t('oauth.scopesHint') }}</div>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item :label="$t('oauth.resourceName')">
                <a-input v-model:value="settingsForm.resourceName" />
              </a-form-item>
              <a-form-item :label="$t('oauth.documentation')">
                <a-input v-model:value="settingsForm.resourceDocumentation" />
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="16">
            <a-col :span="8"><a-form-item :label="$t('oauth.accessTtl')"><a-input-number v-model:value="settingsForm.accessTokenTtlSeconds" :min="60" :max="3600" style="width: 100%" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item :label="$t('oauth.refreshTtl')"><a-input-number v-model:value="settingsForm.refreshTokenTtlSeconds" :min="300" :max="2592000" style="width: 100%" /></a-form-item></a-col>
            <a-col :span="8"><a-form-item :label="$t('oauth.codeTtl')"><a-input-number v-model:value="settingsForm.authorizationCodeTtlSeconds" :min="10" :max="300" style="width: 100%" /></a-form-item></a-col>
          </a-row>
          <a-button type="primary" :loading="saving" @click="saveSettings">{{ $t('common.save') }}</a-button>
        </a-form>
      </SectionCard>

      <SectionCard :title="$t('oauth.clients')" :icon="ApiOutlined" class="oauth-card">
        <template #extra><a-button type="primary" @click="clientModalOpen = true">{{ $t('oauth.addClient') }}</a-button></template>
        <a-empty v-if="!clients.length" />
        <div v-for="client in clients" :key="client.id" class="client-row">
          <div class="client-main">
            <div class="client-title">{{ client.clientName }} <a-tag>{{ client.clientType }}</a-tag></div>
            <div class="client-id">{{ $t('oauth.clientId') }}: {{ client.clientId }}</div>
            <div class="client-meta">
              {{ $t('oauth.redirectUris') }}: {{ client.redirectUris.join(' · ') }} ·
              {{ $t('oauth.scopes') }}: {{ client.scopes.join(' ') }} ·
              {{ $t('oauth.audienceShort') }}={{ client.resourceAudience }}
            </div>
          </div>
          <a-popconfirm :title="$t('oauth.deleteConfirm')" @confirm="removeClient(client.id)">
            <a-button danger type="text">{{ $t('common.delete') }}</a-button>
          </a-popconfirm>
        </div>
      </SectionCard>
    </a-spin>

    <a-modal v-model:open="clientModalOpen" :title="$t('oauth.addClient')" :confirm-loading="creating" @ok="createClient">
      <a-form layout="vertical">
        <a-form-item :label="$t('oauth.clientName')" required><a-input v-model:value="clientForm.clientName" /></a-form-item>
        <a-form-item :label="$t('oauth.clientType')" required><a-select v-model:value="clientForm.clientType" :options="clientTypeOptions" /></a-form-item>
        <a-form-item :label="$t('oauth.redirectUris')" required>
          <a-textarea v-model:value="clientForm.redirectUrisText" :rows="4" />
          <div class="hint">{{ $t('oauth.redirectUrisHint') }}</div>
        </a-form-item>
        <a-form-item :label="$t('oauth.clientScopes')" required><a-select v-model:value="clientForm.scopes" mode="multiple" :options="settingsForm.scopes.map((scope) => ({ value: scope, label: scope }))" /></a-form-item>
        <a-form-item :label="$t('oauth.clientTtl')"><a-input-number v-model:value="clientForm.tokenTtlSeconds" :min="60" :max="settingsForm.accessTokenTtlSeconds" style="width: 100%" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="secretModalOpen" :title="$t('oauth.clientSecret')" :footer="null">
      <a-alert type="warning" :message="$t('oauth.secretWarning')" show-icon />
      <a-typography-paragraph copyable class="secret">{{ createdSecret }}</a-typography-paragraph>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { ApiOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { useI18n } from 'vue-i18n'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/common/SectionCard.vue'
import { oauthApi, type OAuthClientItem, type OAuthSettings } from '@/api'

const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const clients = ref<OAuthClientItem[]>([])
const clientModalOpen = ref(false)
const secretModalOpen = ref(false)
const createdSecret = ref('')
const { t } = useI18n()

const settingsForm = reactive<OAuthSettings>({
  issuer: '', mcpResourceUrl: '', scopes: [], accessTokenTtlSeconds: 900,
  refreshTokenTtlSeconds: 604800, authorizationCodeTtlSeconds: 60,
  resourceName: '', resourceDocumentation: '',
})
const clientForm = reactive({ clientName: '', clientType: 'public' as 'public' | 'confidential', redirectUrisText: '', scopes: [] as string[], tokenTtlSeconds: 900 })
const clientTypeOptions = computed(() => [
  { value: 'public', label: t('oauth.publicClient') },
  { value: 'confidential', label: t('oauth.confidentialClient') },
])

async function load(): Promise<void> {
  loading.value = true
  try {
    const [settings, registered] = await Promise.all([oauthApi.settings(), oauthApi.clients()])
    Object.assign(settingsForm, settings.data)
    clients.value = registered.data ?? []
    clientForm.tokenTtlSeconds = settingsForm.accessTokenTtlSeconds
  } catch {
    message.error(t('oauth.loadFail'))
  } finally { loading.value = false }
}

async function saveSettings(): Promise<void> {
  if (!settingsForm.issuer.trim() || !settingsForm.mcpResourceUrl.trim() || !settingsForm.scopes.length) { message.warning(t('oauth.required')); return }
  saving.value = true
  try { Object.assign(settingsForm, (await oauthApi.saveSettings({ ...settingsForm })).data); message.success(t('oauth.saved')) }
  catch { message.error(t('oauth.saveFail')) }
  finally { saving.value = false }
}

async function createClient(): Promise<void> {
  const redirectUris = clientForm.redirectUrisText.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  if (!clientForm.clientName.trim() || !redirectUris.length || !clientForm.scopes.length) { message.warning(t('oauth.required')); return }
  creating.value = true
  try {
    const result = (await oauthApi.createClient({ clientName: clientForm.clientName.trim(), clientType: clientForm.clientType, redirectUris, scopes: clientForm.scopes, resourceAudience: settingsForm.mcpResourceUrl, tokenTtlSeconds: clientForm.tokenTtlSeconds })).data
    clients.value.unshift(result.client)
    clientModalOpen.value = false
    clientForm.clientName = ''; clientForm.redirectUrisText = ''; clientForm.scopes = []
    if (result.clientSecret) { createdSecret.value = result.clientSecret; secretModalOpen.value = true }
    message.success(t('oauth.created'))
  } catch { message.error(t('oauth.saveFail')) }
  finally { creating.value = false }
}

async function removeClient(id: number): Promise<void> {
  try { await oauthApi.deleteClient(id); clients.value = clients.value.filter((client) => client.id !== id); message.success(t('oauth.deleted')) }
  catch { message.error(t('oauth.saveFail')) }
}

onMounted(load)
</script>

<style scoped lang="scss">
.oauth-page { max-width: 1100px; }
.oauth-card { margin-bottom: 16px; }
.hint { color: var(--ds-text-faint); font-size: 12px; line-height: 1.5; margin-top: 4px; }
.client-row { display: flex; align-items: flex-start; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--ds-border); }
.client-row:last-child { border-bottom: 0; }
.client-main { min-width: 0; flex: 1; }
.client-title { color: var(--ds-text); font-weight: 600; }
.client-id, .client-meta { color: var(--ds-text-faint); font-size: 12px; overflow-wrap: anywhere; margin-top: 4px; }
.secret { padding: 12px; margin-top: 16px; background: var(--ds-surface-muted); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
</style>
