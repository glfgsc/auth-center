<!--
  PermSetEditorDrawer — 新增 / 编辑权限集抽屉。
  能力全集自愈:该系统的后端能力目录 ∪ 该系统所有权限集实际用到的能力 ∪ 当前已选 ——
  即便后端注册表不全,已在用的能力也不丢、create 也完整。按资源(能力码冒号前缀)分组。
  条目标签优先用后端 label(取自全系统能力目录,与当前编辑的系统无关,见 labelCatalog),
  否则本地化动作名(users.capAct.*,兜底人性化码)。
  create 态:编码 / 所属系统可编辑;edit 态:两者禁改(后端亦强制保留原值)。
-->
<template>
  <a-drawer
    :open="open"
    :title="isEdit ? $t('users.editPsTitle') : $t('users.createPsTitle')"
    :width="520"
    @close="$emit('close')"
  >
    <a-form ref="formRef" :model="form" layout="vertical">
      <a-form-item
        :label="$t('users.psName')"
        name="name"
        :rules="[{ required: true, message: $t('users.psNameRequired') }]"
      >
        <a-input v-model:value="form.name" allow-clear />
      </a-form-item>

      <div class="pse-row">
        <a-form-item
          class="pse-row__col"
          :label="$t('users.code')"
          name="code"
          :rules="[{ required: true, message: $t('users.psCodeRequired') }]"
          :extra="isEdit ? '' : $t('users.psCodeHint')"
        >
          <a-input v-model:value="form.code" :disabled="isEdit" allow-clear placeholder="bi_editor" />
        </a-form-item>

        <a-form-item
          class="pse-row__col"
          :label="$t('users.systemLabel')"
          name="systemCode"
          :rules="[{ required: true, message: $t('users.psSystemRequired') }]"
        >
          <a-select v-model:value="form.systemCode" :disabled="isEdit">
            <a-select-option v-for="s in systemCodes" :key="s" :value="s">
              {{ systemLabel(s) }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </div>

      <a-form-item :label="$t('users.description')" name="description">
        <a-textarea v-model:value="form.description" :rows="2" allow-clear />
      </a-form-item>

      <div class="pse-caps-head">
        <span class="pse-caps-title">{{ $t('users.capabilities') }}</span>
        <span class="pse-caps-count">{{ $t('users.capCount', { n: selected.length }) }}</span>
        <a-button type="link" size="small" :disabled="!allCodes.length" @click="selectAll">
          {{ $t('users.selectAll') }}
        </a-button>
        <a-button type="link" size="small" :disabled="!selected.length" @click="clearAll">
          {{ $t('users.clearAll') }}
        </a-button>
      </div>

      <a-spin :spinning="capLoading">
        <div v-if="groups.length" class="pse-groups">
          <div v-for="g in groups" :key="g.cat" class="pse-group">
            <label class="pse-group__head">
              <a-checkbox
                :checked="groupAllOn(g)"
                :indeterminate="groupSomeOn(g)"
                @change="toggleGroup(g)"
              />
              <span class="pse-group__label">{{ g.label }}</span>
              <span class="pse-group__frac">{{ groupSelectedCount(g) }}/{{ g.caps.length }}</span>
            </label>
            <div class="pse-caps">
              <label
                v-for="c in g.caps"
                :key="c.code"
                class="pse-cap"
                :class="{ 'is-on': has(c.code) }"
                :title="c.code"
              >
                <a-checkbox :checked="has(c.code)" @change="toggle(c.code)" />
                <span class="pse-cap__label">{{ c.label }}</span>
              </label>
            </div>
          </div>
        </div>
        <div v-else-if="!capLoading" class="pse-caps-empty">{{ $t('users.noCaps') }}</div>
      </a-spin>
    </a-form>

    <template #footer>
      <div class="pse-footer">
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

import {
  capabilityApi, permissionSetApi,
  type PermissionSetDef, type PermissionSetSaveInput, type SystemCapabilityDef,
} from '@/api'
import { parseCaps } from '../permsetMeta'
import { useCapabilityGroups, type CapGroup } from '../composables/useCapabilityGroups'

const props = defineProps<{
  open: boolean
  record: PermissionSetDef | null
  systemCodes: readonly string[]
  permissionSets: PermissionSetDef[]
  defaultSystem?: string
}>()
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const saving = ref(false)
const capLoading = ref(false)
const catalog = ref<SystemCapabilityDef[]>([])
/**
 * 全系统能力目录 —— 只供取 label,不参与「有哪些条目可勾」的判定。
 *
 * 能力码的显示名是全局事实,不随正在编辑哪个权限集而变;而跨系统超管权限集的
 * systemCode 是 'global'(不是真实注册系统,`?systemCode=global` 恒返回空目录),
 * 若 label 也跟着按系统取,它持有的能力码就会全部拿不到 label、退到 i18n 兜底。
 */
const labelCatalog = ref<SystemCapabilityDef[]>([])
const selected = ref<string[]>([])
const form = reactive<{ name: string; code: string; systemCode: string; description: string }>({
  name: '', code: '', systemCode: 'bi', description: '',
})

const isEdit = computed(() => !!props.record)

/** 能力全集派生与勾选 —— 见 useCapabilityGroups(含注册表不全时的自愈口径)。 */
const {
  groups,
  allCodes,
  has,
  toggle,
  groupSelectedCount,
  groupAllOn,
  groupSomeOn,
  toggleGroup,
  selectAll,
  clearAll,
} = useCapabilityGroups(
  catalog,
  computed(() => props.permissionSets),
  computed(() => form.systemCode),
  selected,
  labelCatalog,
)

/** 所属系统展示名;缺 i18n 时回退系统编码。 */
function systemLabel(code: string): string {
  const key = `users.system.${code}`
  const v = t(key)
  return v === key ? code : v
}

async function loadCatalog(systemCode: string): Promise<void> {
  capLoading.value = true
  try {
    const res = await capabilityApi.list(systemCode)
    catalog.value = res.data ?? []
  } catch (e) {
    // 退化为「本系统在用 ∪ 当前已选」,仍可编辑;但要留痕 —— 否则「注册表里的新能力没出现」
    // 和「后端根本没返回」在界面上长得一样。
    console.warn('[PermSetEditor] 能力注册表拉取失败,退化为在用能力集', e)
    catalog.value = []
  } finally {
    capLoading.value = false
  }
}

/** 拉全系统能力目录供取 label —— 失败则退到 i18n 兜底,不影响勾选。 */
async function loadLabelCatalog(): Promise<void> {
  try {
    const res = await capabilityApi.list()
    labelCatalog.value = res.data ?? []
  } catch {
    labelCatalog.value = []
  }
}

// 打开时回填 + 拉取该系统能力目录。
watch(
  () => [props.open, props.record] as const,
  ([open]) => {
    if (!open) return
    const r = props.record
    form.name = r?.name ?? ''
    form.code = r?.code ?? ''
    form.systemCode = r?.systemCode ?? props.defaultSystem ?? props.systemCodes[0] ?? 'bi'
    form.description = r?.description ?? ''
    selected.value = r ? parseCaps(r.capabilities) : []
    formRef.value?.clearValidate()
    void loadCatalog(form.systemCode)
    void loadLabelCatalog()
  },
  { immediate: true },
)

// create 态切换所属系统时换目录(edit 态系统禁改,不触发)。
watch(
  () => form.systemCode,
  (sys) => {
    if (props.open && !isEdit.value && sys) void loadCatalog(sys)
  },
)

async function submit(): Promise<void> {
  try {
    await formRef.value?.validate()
  } catch {
    // 表单校验未过 —— 错误已由 a-form 逐字段红字呈现,这里只需中止提交。
    return
  }
  saving.value = true
  try {
    const payload: PermissionSetSaveInput = {
      code: form.code,
      systemCode: form.systemCode,
      name: form.name,
      description: form.description,
      capabilities: JSON.stringify(selected.value),
    }
    if (isEdit.value && props.record) {
      await permissionSetApi.update(props.record.id, payload)
      message.success(t('users.updatePsSuccess'))
    } else {
      await permissionSetApi.create(payload)
      message.success(t('users.createPsSuccess'))
    }
    emit('saved')
    emit('close')
  } catch (e) {
    console.error('[PermSetEditor] 权限集保存失败', e)
    message.error(t('users.saveFail'))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.pse-row {
  display: flex;
  gap: 12px;
}
.pse-row__col {
  flex: 1;
  min-width: 0;
}

.pse-caps-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 10px;
}
.pse-caps-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-text);
}
.pse-caps-count {
  font-size: 12px;
  color: var(--ds-text-faint);
  margin-right: auto;
}

.pse-groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.pse-group__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  cursor: pointer;
}
.pse-group__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--ds-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}
.pse-group__frac {
  font-size: 12px;
  color: var(--ds-text-faint);
}

.pse-caps {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 4px 10px;
  padding-left: 24px;
}
.pse-cap {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 4px;
  border-radius: var(--ds-radius-sm);
  cursor: pointer;
  min-width: 0;

  &:hover {
    background: var(--ds-hover-bg);
  }
  &.is-on .pse-cap__label {
    color: var(--ds-text);
  }
}
.pse-cap__label {
  font-size: 12.5px;
  color: var(--ds-text-soft);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pse-caps-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ds-text-faint);
}

.pse-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
