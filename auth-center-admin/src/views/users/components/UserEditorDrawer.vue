<!--
  UserEditorDrawer — 新增 / 编辑用户抽屉。
  create 态(record=null):用户名 + 密码必填;edit 态:用户名禁改、密码留空则不改。
  自提交(直接调 userApi),成功后 emit saved 供父层刷新。
-->
<template>
  <a-drawer
    :open="open"
    :title="isEdit ? $t('users.editUserTitle') : $t('users.createUserTitle')"
    :width="420"
    @close="$emit('close')"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        :label="$t('users.username')"
        name="username"
        :rules="[{ required: true, message: $t('users.usernameRequired') }]"
      >
        <a-input v-model:value="form.username" :disabled="isEdit" allow-clear />
      </a-form-item>

      <a-form-item :label="$t('users.nickname')" name="nickname">
        <a-input v-model:value="form.nickname" allow-clear />
      </a-form-item>

      <a-form-item :label="$t('users.email')" name="email">
        <a-input v-model:value="form.email" allow-clear />
      </a-form-item>

      <a-form-item :label="$t('users.phone')" name="phone">
        <a-input v-model:value="form.phone" allow-clear />
      </a-form-item>

      <a-form-item
        :label="$t('users.password')"
        name="password"
        :rules="passwordRules"
        :extra="isEdit ? $t('users.fieldPasswordEditHint') : $t('users.fieldPasswordCreateHint')"
      >
        <a-input-password
          v-model:value="form.password"
          autocomplete="new-password"
          :placeholder="isEdit ? $t('users.fieldPasswordEditHint') : $t('users.fieldPasswordCreateHint')"
        />
      </a-form-item>
    </a-form>

    <template #footer>
      <div class="ued-footer">
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

import { userApi, type AuthUser, type UserSaveInput } from '@/api'

const props = defineProps<{ open: boolean; record: AuthUser | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const saving = ref(false)
const form = reactive<UserSaveInput>({ username: '', nickname: '', email: '', phone: '', password: '' })

const isEdit = computed(() => !!props.record)

/**
 * 密码校验:两种态都可留空,填了才校长度。
 *
 * 新建态留空 = 外部账号(经 CAS/SSO 登录),后端置随机占位密码使其不可本地密码登录 —— 与 CAS 即时建号同款。新建态强制必填是
 * 前端单方面加的:后端 AuthUser.password 只有 @Size(6,72)、并无 @NotBlank,逼管理员给一个永远用不上的密码。编辑态留空 =
 * 不改密码(既有语义)。
 *
 * 长度用自定义校验而非 min/max 规则:后者对空串同样判长度不足,会把「留空」判成错。
 */
const passwordRules = computed(() => [
  {
    validator: (_rule: unknown, value: string) =>
      !value || (value.length >= 6 && value.length <= 72)
        ? Promise.resolve()
        : Promise.reject(new Error(t('users.passwordLength'))),
  },
])

// 打开时按 record 回填(create 态清空);password 恒清空(edit 态留空=不改)。
watch(
  () => [props.open, props.record] as const,
  ([open]) => {
    if (!open) return
    const r = props.record
    form.username = r?.username ?? ''
    form.nickname = r?.nickname ?? ''
    form.email = r?.email ?? ''
    form.phone = r?.phone ?? ''
    form.password = ''
    formRef.value?.clearValidate()
  },
  { immediate: true },
)

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    return // 校验失败,ant 已内联提示
  }
  saving.value = true
  try {
    // edit 态密码留空则不发送,避免被后端置空/重置
    const payload: UserSaveInput = {
      username: form.username,
      nickname: form.nickname,
      email: form.email,
      phone: form.phone,
    }
    if (form.password) payload.password = form.password

    if (isEdit.value && props.record) {
      await userApi.update(props.record.id, payload)
      message.success(t('users.updateSuccess'))
    } else {
      await userApi.create(payload)
      message.success(t('users.createSuccess'))
    }
    emit('saved')
    emit('close')
  } catch {
    message.error(t('users.saveFail'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.ued-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
