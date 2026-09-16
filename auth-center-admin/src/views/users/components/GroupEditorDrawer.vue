<!--
  GroupEditorDrawer — 新增 / 编辑用户组抽屉。
  create 态(record=null):编码 + 名称必填;edit 态:编码禁改。
  自提交(直接调 groupApi),成功后 emit saved。
-->
<template>
  <a-drawer
    :open="open"
    :title="isEdit ? $t('groups.editTitle') : $t('groups.createTitle')"
    :width="420"
    @close="$emit('close')"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        :label="$t('groups.code')"
        name="code"
        :rules="[{ required: true, message: $t('groups.codeRequired') }]"
        :extra="isEdit ? '' : $t('groups.codeHint')"
      >
        <a-input v-model:value="form.code" :disabled="isEdit" allow-clear placeholder="data_team" />
      </a-form-item>

      <a-form-item
        :label="$t('groups.name')"
        name="name"
        :rules="[{ required: true, message: $t('groups.nameRequired') }]"
      >
        <a-input v-model:value="form.name" allow-clear />
      </a-form-item>

      <a-form-item :label="$t('groups.description')" name="description">
        <a-textarea v-model:value="form.description" :rows="3" allow-clear />
      </a-form-item>
    </a-form>

    <template #footer>
      <div class="ged-footer">
        <a-button @click="$emit('close')">{{ $t('common.cancel') }}</a-button>
        <a-button type="primary" :loading="saving" @click="submit">{{ $t('common.save') }}</a-button>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'

import { groupApi, type AuthGroup, type GroupSaveInput } from '@/api'

const props = defineProps<{ open: boolean; record: AuthGroup | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<GroupSaveInput>({ code: '', name: '', description: '' })

const isEdit = computed(() => !!props.record)

watch(
  () => [props.open, props.record] as const,
  ([open]) => {
    if (!open) return
    const r = props.record
    form.code = r?.code ?? ''
    form.name = r?.name ?? ''
    form.description = r?.description ?? ''
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
  saving.value = true
  try {
    if (isEdit.value && props.record) {
      await groupApi.update(props.record.id, { name: form.name, description: form.description })
      message.success(t('groups.updateSuccess'))
    } else {
      await groupApi.create({ code: form.code, name: form.name, description: form.description })
      message.success(t('groups.createSuccess'))
    }
    emit('saved')
    emit('close')
  } catch {
    message.error(t('groups.saveFail'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.ged-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
