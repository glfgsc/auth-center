<!--
  ConnectedAppEditorModal — 新增 / 编辑外部应用（Connected App）。
  目标系统 / 域名白名单 / Direct-Trust 验签公钥。自提交;create 成功把一次性密钥经 reveal 抛给父层。
  与 BI 原"外部应用集成"一致采用弹窗(modal)范式。内容作用域不在此配置——嵌入以真实用户身份运行,
  由目标系统按其工作区成员 + RLS 裁决可见资产。
-->
<template>
  <a-modal
    :open="open"
    :title="isEdit ? $t('externalApps.editTitle') : $t('externalApps.createTitle')"
    :width="560"
    :confirm-loading="saving"
    :ok-text="isEdit ? $t('common.save') : $t('common.add')"
    :cancel-text="$t('common.cancel')"
    :destroy-on-close="true"
    @ok="submit"
    @cancel="$emit('close')"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        :label="$t('externalApps.name')"
        name="name"
        :rules="[{ required: true, message: $t('externalApps.nameRequired') }]"
      >
        <a-input v-model:value="form.name" allow-clear :placeholder="$t('externalApps.namePh')" />
      </a-form-item>

      <a-form-item :label="$t('externalApps.targetSystem')" name="targetSystem">
        <a-select v-model:value="form.targetSystem" :options="systemOptions" />
        <div class="cae-hint">{{ $t('externalApps.targetSystemHint') }}</div>
      </a-form-item>

      <a-form-item :label="$t('externalApps.domains')" name="domainList">
        <a-select
          v-model:value="form.domainList"
          mode="tags"
          :open="false"
          :placeholder="$t('externalApps.domainsPh')"
        />
        <div class="cae-hint">{{ $t('externalApps.domainsHint') }}</div>
      </a-form-item>

      <a-divider style="margin: 4px 0 16px" />

      <a-form-item :label="$t('externalApps.authMethod')" name="ssoEnabled">
        <div class="cae-sso-row">
          <a-switch v-model:checked="form.ssoEnabled" />
          <div class="cae-sso-text">
            <span class="cae-sso-title">{{ $t('externalApps.directTrust') }}</span>
            <span class="cae-hint">{{ $t('externalApps.directTrustHint') }}</span>
          </div>
        </div>
      </a-form-item>

      <a-form-item v-if="form.ssoEnabled" :label="$t('externalApps.publicKey')" name="publicKey">
        <a-textarea
          v-model:value="form.assertionPublicKeyPem"
          :rows="6"
          class="cae-pem"
          placeholder="-----BEGIN PUBLIC KEY-----&#10;MIIBIjANBgkq...&#10;-----END PUBLIC KEY-----"
        />
        <div class="cae-hint">{{ $t('externalApps.publicKeyHint') }}</div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'

import {
  connectedAppApi,
  systemApi,
  type AuthSystemDef,
  type ConnectedAppItem,
  type SecretReveal,
} from '@/api'

const props = defineProps<{ open: boolean; record: ConnectedAppItem | null }>()
const emit = defineEmits<{ close: []; saved: []; reveal: [data: SecretReveal] }>()

const { t, te } = useI18n()

/** 目标系统默认值（当前唯一支持嵌入的系统为洞察）。 */
const DEFAULT_TARGET_SYSTEM = 'bi'

const formRef = ref<FormInstance>()
const saving = ref(false)
const systems = ref<AuthSystemDef[]>([])

const form = reactive<{
  name: string
  targetSystem: string
  domainList: string[]
  ssoEnabled: boolean
  assertionPublicKeyPem: string
}>({ name: '', targetSystem: DEFAULT_TARGET_SYSTEM, domainList: [], ssoEnabled: false, assertionPublicKeyPem: '' })

const isEdit = computed(() => !!props.record)

/** 目标系统标签：优先 i18n(users.system.*),回退注册表 name,再回退 code。 */
function systemLabel(code: string, name?: string): string {
  const key = `users.system.${code}`
  return te(key) ? t(key) : name || code
}

/** 可选目标系统 = 已注册系统(排除认证中心自身——中心不嵌入自身)。 */
const systemOptions = computed(() =>
  systems.value
    .filter((s) => s.code !== 'auth_center')
    .map((s) => ({ value: s.code, label: systemLabel(s.code, s.name) })),
)

function parseDomains(json?: string): string[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

watch(
  () => [props.open, props.record] as const,
  ([open]) => {
    if (!open) return
    const r = props.record
    form.name = r?.name ?? ''
    form.targetSystem = r?.targetSystem ?? DEFAULT_TARGET_SYSTEM
    form.domainList = parseDomains(r?.allowedDomains)
    form.assertionPublicKeyPem = r?.assertionPublicKeyPem ?? ''
    form.ssoEnabled = !!(r?.hasPublicKey || r?.assertionPublicKeyPem)
    formRef.value?.clearValidate()
  },
  { immediate: true },
)

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const publicKey = form.ssoEnabled ? form.assertionPublicKeyPem.trim() : ''
  if (form.ssoEnabled && !publicKey.includes('BEGIN PUBLIC KEY')) {
    message.warning(t('externalApps.publicKeyInvalid'))
    return
  }

  saving.value = true
  try {
    const allowedDomains = JSON.stringify(form.domainList)
    if (isEdit.value && props.record) {
      // 编辑态公钥:开=送 PEM;关=送空串清除
      await connectedAppApi.update(props.record.id, {
        name: form.name,
        allowedDomains,
        targetSystem: form.targetSystem,
        assertionPublicKeyPem: publicKey,
      })
      message.success(t('externalApps.saved'))
    } else {
      const res = await connectedAppApi.create({
        name: form.name,
        allowedDomains,
        targetSystem: form.targetSystem,
        assertionPublicKeyPem: publicKey || undefined,
      })
      message.success(t('externalApps.created'))
      if (res.data) emit('reveal', res.data)
    }
    emit('saved')
    emit('close')
  } catch {
    message.error(t('externalApps.saveFail'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  try {
    const res = await systemApi.list()
    systems.value = res.data ?? []
  } catch {
    systems.value = []
  }
})
</script>

<style scoped lang="scss">
.cae-hint {
  font-size: 12px;
  color: var(--ds-text-faint);
  line-height: 1.5;
  margin-top: 4px;
}
.cae-sso-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}
.cae-sso-text {
  display: flex;
  flex-direction: column;
}
.cae-sso-title {
  font-size: 13px;
  color: var(--ds-text);
}
.cae-pem {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
</style>
