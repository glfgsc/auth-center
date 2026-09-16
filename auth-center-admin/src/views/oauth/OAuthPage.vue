<template>
  <div class="ac-page oauth-page">
    <PageHeader :title="$t('oauth.title')" :description="$t('oauth.subtitle')" />

    <a-spin :spinning="loading">
      <a-card :title="$t('oauth.settings')" class="oauth-card">
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
                <a-textarea v-model:value="scopeText" :rows="3" />
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
      </a-card>

      <a-card :title="$t('oauth.clients')" class="oauth-card">
        <template #extra><a-button type="primary" @click="clientModalOpen = true">{{ $t('oauth.addClient') }}</a-button></template>
        <a-empty v-if="!clients.length" />
        <div v-for="client in clients" :key="client.id" class="client-row">
          <div class="client-main">
            <div class="client-title">{{ client.clientName }} <a-tag>{{ client.clientType }}</a-tag></div>
            <div class="client-id">{{ client.clientId }}</div>
            <div class="client-meta">{{ client.redirectUris.join(' · ') }} · {{ client.scopes.join(' ') }} · aud={{ client.resourceAudience }}</div>
          </div>
          <a-popconfirm :title="$t('oauth.deleteConfirm')" @confirm="removeClient(client.id)">
            <a-button danger type="text">{{ $t('common.delete') }}</a-button>
          </a-popconfirm>
        </div>
      </a-card>
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
import PageHeader from '@/components/common/PageHeader.vue'
import { oauthApi, type OAuthClientItem, type OAuthSettings } from '@/api'

const loading = ref(false)
const saving = ref(false)
const creating = ref(false)
const clients = ref<OAuthClientItem[]>([])
const clientModalOpen = ref(false)
const secretModalOpen = ref(false)
const createdSecret = ref('')

const settingsForm = reactive<OAuthSettings>({
  issuer: '', mcpResourceUrl: '', scopes: [], accessTokenTtlSeconds: 900,
  refreshTokenTtlSeconds: 604800, authorizationCodeTtlSeconds: 60,
  resourceName: '', resourceDocumentation: '',
})
const scopeText = computed({
  get: () => settingsForm.scopes.join('\n'),
  set: (value: string) => { settingsForm.scopes = value.split(/\s+/).map((item) => item.trim()).filter(Boolean) },
})
const clientForm = reactive({ clientName: '', clientType: 'public' as 'public' | 'confidential', redirectUrisText: '', scopes: [] as string[], tokenTtlSeconds: 900 })
const clientTypeOptions = computed(() => [
  { value: 'public', label: 'Public（PKCE）' },
  { value: 'confidential', label: 'Confidential（密钥）' },
])

async function load(): Promise<void> {
  loading.value = true
  try {
    const [settings, registered] = await Promise.all([oauthApi.settings(), oauthApi.clients()])
    Object.assign(settingsForm, settings.data)
    clients.value = registered.data ?? []
    clientForm.tokenTtlSeconds = settingsForm.accessTokenTtlSeconds
  } catch {
    message.error('OAuth configuration load failed')
  } finally { loading.value = false }
}

async function saveSettings(): Promise<void> {
  if (!settingsForm.issuer.trim() || !settingsForm.mcpResourceUrl.trim() || !settingsForm.scopes.length) { message.warning('OAuth settings are incomplete'); return }
  saving.value = true
  try { Object.assign(settingsForm, (await oauthApi.saveSettings({ ...settingsForm })).data); message.success('OAuth configuration saved') }
  catch { message.error('OAuth configuration save failed') }
  finally { saving.value = false }
}

async function createClient(): Promise<void> {
  const redirectUris = clientForm.redirectUrisText.split(/\r?\n/).map((item) => item.trim()).filter(Boolean)
  if (!clientForm.clientName.trim() || !redirectUris.length || !clientForm.scopes.length) { message.warning('OAuth client registration is incomplete'); return }
  creating.value = true
  try {
    const result = (await oauthApi.createClient({ clientName: clientForm.clientName.trim(), clientType: clientForm.clientType, redirectUris, scopes: clientForm.scopes, resourceAudience: settingsForm.mcpResourceUrl, tokenTtlSeconds: clientForm.tokenTtlSeconds })).data
    clients.value.unshift(result.client)
    clientModalOpen.value = false
    clientForm.clientName = ''; clientForm.redirectUrisText = ''; clientForm.scopes = []
    if (result.clientSecret) { createdSecret.value = result.clientSecret; secretModalOpen.value = true }
    message.success('OAuth client created')
  } catch { message.error('OAuth client creation failed') }
  finally { creating.value = false }
}

async function removeClient(id: number): Promise<void> {
  try { await oauthApi.deleteClient(id); clients.value = clients.value.filter((client) => client.id !== id); message.success('OAuth client deleted') }
  catch { message.error('OAuth client deletion failed') }
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
