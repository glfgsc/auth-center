<!--
  PermSetGallery — 权限集 Tab:卡片内工具栏(搜索/筛选 + 新增)+ 按系统分组的权限集卡片画廊。
  卡片含能力点阵缩略,点击进详情抽屉(父层控制);hover 出现编辑/删除;系统预设集不可删。
  权限集增删改自持编辑抽屉 + 删除确认,变更后 emit reload。
-->
<template>
  <div class="tn-card psg-card">
    <div class="psg-toolbar">
      <a-input
        v-model:value="search"
        :placeholder="$t('users.psSearch')"
        allow-clear
        class="psg-search"
      >
        <template #prefix><SearchOutlined class="psg-search__icon" /></template>
      </a-input>
      <a-select
        v-model:value="typeFilter"
        :placeholder="$t('users.filterType')"
        allow-clear
        class="psg-filter psg-filter--narrow"
      >
        <a-select-option value="system">{{ $t('users.systemTag') }}</a-select-option>
        <a-select-option value="custom">{{ $t('users.customTag') }}</a-select-option>
      </a-select>
      <a-select
        v-model:value="systemFilter"
        :placeholder="$t('users.filterAll')"
        allow-clear
        class="psg-filter"
      >
        <a-select-option v-for="s in systemList" :key="s" :value="s">
          {{ systemLabel(s) }}
        </a-select-option>
      </a-select>
      <a-button type="primary" class="psg-add" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        {{ $t('users.addPermSet') }}
      </a-button>
    </div>

    <div class="psg-body">
      <template v-for="[sys, list] in grouped" :key="sys">
        <div class="psg-section">
          <span class="psg-section__dot" :style="{ background: systemHex(sys) }" />
          <span class="psg-section__label">{{ systemLabel(sys) }}</span>
          <span class="psg-section__count">{{ list.length }}</span>
          <span class="psg-section__line" />
        </div>
        <div class="psg-grid">
          <PermSetCard
            v-for="ps in list"
            :key="ps.id"
            :ps="ps"
            :user-count="userCount(ps.code)"
            :matrix="matrixOf(ps)"
            @select="$emit('select', $event)"
            @edit="openEdit"
            @remove="confirmDelete"
          />
        </div>
      </template>

      <div v-if="grouped.length === 0" class="psg-empty">
        {{ $t('users.noPermSets') }}
      </div>
    </div>

    <PermSetEditorDrawer
      :open="editorOpen"
      :record="editorRecord"
      :system-codes="systemList"
      :permission-sets="permissionSets"
      :default-system="defaultSystem"
      @close="editorOpen = false"
      @saved="$emit('reload')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, createVNode, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import { ExclamationCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons-vue'

import { permissionSetApi, type AuthUser, type PermissionSetDef } from '@/api'
import {
  KNOWN_SYSTEMS,
  ownedCategories,
  parseCaps,
  systemHex,
  type CapCategoryView,
} from '../permsetMeta'
import PermSetCard, { type MatrixRow } from './PermSetCard.vue'
import PermSetEditorDrawer from './PermSetEditorDrawer.vue'

const props = defineProps<{
  permissionSets: PermissionSetDef[]
  users: AuthUser[]
  systems: string[]
  /** 能力全集矩阵(父层按所有权限集能力并集派生);卡片矩阵按此渲染真实分布。 */
  capUniverse: CapCategoryView[]
}>()

const emit = defineEmits<{ select: [ps: PermissionSetDef]; reload: [] }>()

const { t } = useI18n()

const search = ref('')
const typeFilter = ref<string | undefined>(undefined)
const systemFilter = ref<string | undefined>(undefined)
const editorOpen = ref(false)
const editorRecord = ref<PermissionSetDef | null>(null)

/** 系统分组顺序:优先后端清单,为空时回退兜底常量。 */
const systemList = computed<readonly string[]>(() =>
  props.systems.length ? props.systems : KNOWN_SYSTEMS,
)

/** 新建默认所属系统:当前系统筛选 > 首个非全局系统 > bi。 */
const defaultSystem = computed<string>(() =>
  systemFilter.value || systemList.value.find((s) => s !== 'global') || 'bi',
)

const filtered = computed(() => {
  let list = props.permissionSets
  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter((ps) => ps.name.toLowerCase().includes(q) || ps.code.toLowerCase().includes(q))
  }
  if (typeFilter.value === 'system') list = list.filter((ps) => ps.isSystem === 1)
  if (typeFilter.value === 'custom') list = list.filter((ps) => !ps.isSystem || ps.isSystem === 0)
  if (systemFilter.value) list = list.filter((ps) => ps.systemCode === systemFilter.value)
  return list
})

/** 按系统分组(仅含有条目的系统,顺序跟后端系统清单)。 */
const grouped = computed(() => {
  const groups: [string, PermissionSetDef[]][] = []
  for (const sys of systemList.value) {
    const list = filtered.value.filter((ps) => ps.systemCode === sys)
    if (list.length > 0) groups.push([sys, list])
  }
  return groups
})

function systemLabel(code: string): string {
  const key = `users.system.${code}`
  const v = t(key)
  return v === key ? code : v
}

function userCount(code: string): number {
  return props.users.filter((u) => u.permissionSets?.some((ps) => ps.code === code)).length
}

/** 该权限集实际触达的分类行:域内能力全集(拥有则高亮),只显示有能力的分类,忠实反映权限范围。 */
function matrixOf(ps: PermissionSetDef): MatrixRow[] {
  const owned = new Set(parseCaps(ps.capabilities))
  return ownedCategories(props.capUniverse, owned).map((c) => ({ cat: c.cat, caps: c.caps, owned }))
}

function openCreate(): void {
  editorRecord.value = null
  editorOpen.value = true
}
function openEdit(ps: PermissionSetDef): void {
  editorRecord.value = ps
  editorOpen.value = true
}
function confirmDelete(ps: PermissionSetDef): void {
  if (ps.isSystem === 1) {
    message.warning(t('users.cantDeleteSystemPs'))
    return
  }
  Modal.confirm({
    title: t('users.deletePsConfirm', { name: ps.name }),
    icon: createVNode(ExclamationCircleOutlined),
    okType: 'danger',
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await permissionSetApi.remove(ps.id)
        message.success(t('users.deletePsSuccess'))
        emit('reload')
      } catch (e) {
        console.error('[PermSetGallery] 删除权限集失败', e)
        message.error(t('users.deleteFail'))
      }
    },
  })
}
</script>

<style scoped lang="scss">
.psg-card {
  padding: 0;
}

.psg-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ds-border-soft);
  flex-wrap: wrap;
}

.psg-search {
  max-width: 260px;
}

.psg-search__icon {
  color: var(--ds-text-faint);
}

.psg-filter {
  width: 150px;
}

.psg-filter--narrow {
  width: 120px;
}

.psg-add {
  margin-left: auto;
}

.psg-body {
  padding: 16px;
}

/* ── 系统分组标题 ── */
.psg-section {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 20px 0 12px;

  &:first-child {
    margin-top: 0;
  }
}

.psg-section__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.psg-section__label {
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-text);
  white-space: nowrap;
}

.psg-section__count {
  font-size: 12px;
  color: var(--ds-text-faint);
  background: var(--ds-neutral-soft);
  padding: 0 8px;
  border-radius: 999px;
  line-height: 20px;
  flex-shrink: 0;
}

.psg-section__line {
  flex: 1;
  height: 1px;
  background: var(--ds-border-soft);
}

/* ── 卡片画廊 ── */
.psg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.psg-empty {
  padding: 48px 0;
  text-align: center;
  font-size: 13px;
  color: var(--ds-text-faint);
}
</style>
