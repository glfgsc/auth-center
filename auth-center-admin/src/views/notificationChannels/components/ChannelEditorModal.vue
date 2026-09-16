<!--
  ChannelEditorModal — 新增 / 编辑通知渠道。

  字段由后端 /types 下发的元数据驱动渲染,前端不硬编码任何厂商字段;新增渠道类型只需后端
  扩枚举,这里自动出表单。

  版式分三段(渠道类型 / 连接信息 / 标识与范围),按「配一个渠道」的实际顺序排:
  先定是哪家(类型决定后面有哪些字段),再填连得上的信息(地址与密钥),最后才是起名字、
  定归属这类元数据。选定类型前只渲染第一段 —— 类型不同字段就不同,把七种渠道的
  字段并集一次性摊开没有意义。

  标识段里成对的短字段(引用名 / 归属产品、渠道名称 / 可用范围)两列并排:满宽单列会把
  弹窗拉成一条稀疏的长表单,而这些字段本身都很短。

  编辑态刻意不回填凭据内容:读接口返回的是掩码,回填等于把 *** 当真值写回去。
  留空即保持原值(后端对掩码与空值都按「不变」处理)。
-->
<template>
  <a-modal
    :open="open"
    :title="isEdit ? $t('notificationChannels.editTitle') : $t('notificationChannels.createTitle')"
    :width="600"
    :confirm-loading="saving"
    :ok-text="isEdit ? $t('common.save') : $t('common.add')"
    :cancel-text="$t('common.cancel')"
    :ok-button-props="{ disabled: !canSave }"
    :destroy-on-close="true"
    wrap-class-name="ce-modal"
    @ok="submit"
    @cancel="$emit('close')"
  >
    <div class="ce-body">
      <ChannelTypePicker
        v-model="form.credType"
        :types="types"
        :locked="isEdit"
        @change="resetSecrets"
      />

      <template v-if="form.credType">
        <!-- ── 连接信息 ── -->
        <section class="ce-section">
          <h4 class="ce-section-title">{{ $t('notificationChannels.connSection') }}</h4>

          <div v-if="isEdit" class="ce-note">
            {{ $t('notificationChannels.hint.blankKeeps') }}
          </div>

          <div v-for="f in activeFields" :key="f.key" class="ce-field">
            <label class="ce-label" :class="{ 'ce-label--req': !isEdit && f.required }">
              {{ f.label }}
            </label>
            <a-input-password
              v-if="f.inputType === 'password'"
              v-model:value="secrets[f.key]"
              :placeholder="f.placeholder"
              autocomplete="new-password"
            />
            <a-input-number
              v-else-if="f.inputType === 'number'"
              v-model:value="secrets[f.key]"
              :placeholder="f.placeholder"
              class="ce-full"
            />
            <a-switch v-else-if="f.inputType === 'boolean'" v-model:checked="secrets[f.key]" />
            <a-input v-else v-model:value="secrets[f.key]" :placeholder="f.placeholder" />
          </div>
        </section>

        <ChannelIdentityFields
          v-model:cred-key="form.credKey"
          v-model:target-system="form.targetSystem"
          v-model:display-name="form.displayName"
          v-model:scope-ref="form.scopeRef"
          v-model:enabled="form.enabled"
          :locked="isEdit"
        />
      </template>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  notificationCredentialApi,
  type ChannelTypeMeta,
  type NotificationCredentialItem,
} from '@/api'
import { useProductScope } from '@/composables/useProductScope'
import ChannelIdentityFields from './ChannelIdentityFields.vue'
import ChannelTypePicker from './ChannelTypePicker.vue'

const props = defineProps<{
  open: boolean
  /** 传入即编辑态;为空即新建。 */
  record: NotificationCredentialItem | null
  /** 从空态类型入口进来时预选的类型。 */
  presetType?: string
  types: ChannelTypeMeta[]
}>()

const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()
const { current } = useProductScope()

const saving = ref(false)
const secrets = reactive<Record<string, unknown>>({})

/** 与后端 INotificationCredentialService.SECRET_MASK 同值。 */
const SECRET_MASK = '***'

const form = reactive({
  credType: undefined as string | undefined,
  credKey: '',
  displayName: '',
  targetSystem: 'bi',
  scopeRef: '',
  enabled: true,
})

const isEdit = computed(() => Boolean(props.record?.id))

const activeFields = computed(
  () => props.types.find((tp) => tp.name === form.credType)?.fields ?? [],
)

const canSave = computed(() => {
  if (!form.credType || !form.credKey.trim() || !form.targetSystem) return false
  if (isEdit.value) return true
  return activeFields.value
    .filter((f) => f.required)
    .every((f) => {
      const v = secrets[f.key]
      return v !== undefined && v !== null && String(v).trim() !== ''
    })
})

/** 换类型即清空已填内容 —— 字段名不同,留着只会把上一家的值带进下一家。 */
function resetSecrets(): void {
  Object.keys(secrets).forEach((k) => delete secrets[k])
}

/** 收集用户真正填了的字段;一个都没填时返回 undefined,让后端保持原值。 */
function collectSecretJson(): string | undefined {
  const payload: Record<string, unknown> = {}
  for (const f of activeFields.value) {
    const v = secrets[f.key]
    if (v === undefined || v === null || String(v).trim() === '') continue
    payload[f.key] = v
  }
  return Object.keys(payload).length ? JSON.stringify(payload) : undefined
}

async function submit(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  try {
    const body = {
      targetSystem: form.targetSystem,
      credKey: form.credKey.trim(),
      credType: form.credType as string,
      displayName: form.displayName.trim() || form.credKey.trim(),
      encryptedSecretJson: collectSecretJson(),
      scopeRef: form.scopeRef.trim() || undefined,
      enabled: form.enabled,
    }
    if (isEdit.value && props.record?.id) {
      await notificationCredentialApi.update(props.record.id, body)
    } else {
      await notificationCredentialApi.create(body)
    }
    message.success(t('notificationChannels.saved'))
    emit('saved')
  } catch (e) {
    console.error('[NotifChannels] 保存失败', e)
    message.error(t('notificationChannels.saveFail'))
  } finally {
    saving.value = false
  }
}

watch(
  () => props.open,
  (val) => {
    if (!val) return
    Object.keys(secrets).forEach((k) => delete secrets[k])
    const r = props.record
    form.credType = r?.credType ?? props.presetType
    form.credKey = r?.credKey ?? ''
    form.displayName = r?.displayName ?? ''
    // 新建默认落在顶栏当前产品;「全部产品」档时退回 bi。
    form.targetSystem = r?.targetSystem ?? (current.value === 'agent' ? 'agent' : 'bi')
    form.scopeRef = r?.scopeRef ?? ''
    form.enabled = r ? r.enabled !== 0 : true
  },
  { immediate: true },
)
</script>

<style scoped lang="scss">
.ce-body {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

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

.ce-full {
  width: 100%;
}

/* 「留空即保持原值」会影响操作结果,给它一块底,别和普通 hint 一样轻 */
.ce-note {
  padding: 9px 12px;
  font-size: 12.5px;
  line-height: 1.55;
  color: var(--ds-text-soft);
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: 8px;
}

</style>
