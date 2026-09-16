<!--
  UserDirectoryPanel — 用户 Tab:卡片内工具栏(搜索/筛选 + 新增)+ 用户表。
  身份列合并头像/昵称/用户名;权限集列悬停出现编辑入口(点击弹分配面板),徽章超量折叠为 +N;
  行尾操作列编辑/删除。用户增删改自持编辑抽屉 + 删除确认,变更后 emit reload。
-->
<template>
  <div class="tn-card udp-card">
    <div class="udp-toolbar">
      <a-input
        v-model:value="search"
        :placeholder="$t('users.searchPlaceholder')"
        allow-clear
        class="udp-search"
      >
        <template #prefix><SearchOutlined class="udp-search__icon" /></template>
      </a-input>
      <a-select
        v-model:value="psFilter"
        :placeholder="$t('users.permissionSet')"
        allow-clear
        class="udp-filter"
      >
        <a-select-option v-for="ps in permissionSets" :key="ps.code" :value="ps.code">
          {{ ps.name }}
        </a-select-option>
      </a-select>
      <a-select
        v-model:value="systemFilter"
        :placeholder="$t('users.filterAll')"
        allow-clear
        class="udp-filter udp-filter--narrow"
      >
        <a-select-option v-for="s in systemList" :key="s" :value="s">
          {{ systemLabel(s) }}
        </a-select-option>
      </a-select>
      <span class="udp-count">{{ $t('users.totalCount', { n: filteredUsers.length }) }}</span>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        {{ $t('users.addUser') }}
      </a-button>
    </div>

    <a-table
      :data-source="filteredUsers"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="{ pageSize: 20, showSizeChanger: true, showTotal: (n: number) => $t('users.totalCount', { n }) }"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'identity'">
          <div class="udp-identity">
            <a-avatar :size="32" :style="{ background: avatarColor(record.username), flexShrink: 0 }">
              {{ (record.nickname || record.username || '?')[0].toUpperCase() }}
            </a-avatar>
            <div class="udp-identity__text">
              <div class="udp-identity__name">{{ record.nickname || record.username }}</div>
              <div class="udp-identity__sub">{{ record.username }}</div>
            </div>
          </div>
        </template>

        <template v-else-if="column.key === 'email'">
          <span class="udp-muted">{{ record.email || '—' }}</span>
        </template>

        <template v-else-if="column.key === 'permissionSets'">
          <UserPermSetCell
            :user="record"
            :permission-sets="permissionSets"
            :system-list="systemList"
            @assign="(u, code) => $emit('assign', u, code)"
          />
        </template>

        <template v-else-if="column.key === 'createTime'">
          <span class="udp-muted">{{ formatTime(record.createTime) }}</span>
        </template>

        <template v-else-if="column.key === 'actions'">
          <div class="udp-actions">
            <a-tooltip :title="$t('common.edit')">
              <a-button type="text" size="small" @click="openEdit(record)"><EditOutlined /></a-button>
            </a-tooltip>
            <a-tooltip :title="$t('common.delete')">
              <a-button type="text" size="small" danger @click="confirmDelete(record)"><DeleteOutlined /></a-button>
            </a-tooltip>
          </div>
        </template>
      </template>
    </a-table>

    <UserEditorDrawer
      :open="editorOpen"
      :record="editorRecord"
      @close="editorOpen = false"
      @saved="$emit('reload')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, createVNode, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  DeleteOutlined,
  EditOutlined,
  ExclamationCircleOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons-vue'

import { userApi, type AuthUser, type PermissionSetDef } from '@/api'
import { KNOWN_SYSTEMS, avatarColor, formatTime } from '../permsetMeta'
import UserEditorDrawer from './UserEditorDrawer.vue'
import UserPermSetCell from './UserPermSetCell.vue'

const props = defineProps<{
  users: AuthUser[]
  permissionSets: PermissionSetDef[]
  systems: string[]
  loading: boolean
}>()

const emit = defineEmits<{ assign: [user: AuthUser, code: string]; reload: [] }>()

const { t } = useI18n()

const search = ref('')
const psFilter = ref<string | undefined>(undefined)
const systemFilter = ref<string | undefined>(undefined)
const editorOpen = ref(false)
const editorRecord = ref<AuthUser | null>(null)

/** 系统分组顺序:优先后端清单,为空时回退兜底常量。 */
const systemList = computed<readonly string[]>(() =>
  props.systems.length ? props.systems : KNOWN_SYSTEMS,
)

const columns = computed(() => [
  { title: t('users.identity'), key: 'identity' },
  { title: t('users.email'), key: 'email', dataIndex: 'email' },
  { title: t('users.permissionSet'), key: 'permissionSets', width: 300 },
  { title: t('users.createdAt'), key: 'createTime', dataIndex: 'createTime', width: 170 },
  { title: t('users.actions'), key: 'actions', width: 90, align: 'center' as const },
])

const filteredUsers = computed(() => {
  let list = props.users
  if (search.value) {
    const q = search.value.toLowerCase()
    list = list.filter((u) =>
      (u.nickname || '').toLowerCase().includes(q) ||
      (u.username || '').toLowerCase().includes(q) ||
      (u.email || '').toLowerCase().includes(q),
    )
  }
  if (psFilter.value) {
    list = list.filter((u) => u.permissionSets?.some((ps) => ps.code === psFilter.value))
  }
  if (systemFilter.value) {
    list = list.filter((u) =>
      u.permissionSets?.some(
        (ps) => ps.systemCode === systemFilter.value || ps.systemCode === 'global',
      ),
    )
  }
  return list
})

function systemLabel(code: string): string {
  const key = `users.system.${code}`
  const v = t(key)
  return v === key ? code : v
}

function openCreate(): void {
  editorRecord.value = null
  editorOpen.value = true
}
function openEdit(record: AuthUser): void {
  editorRecord.value = record
  editorOpen.value = true
}
function confirmDelete(record: AuthUser): void {
  Modal.confirm({
    title: t('users.deleteUserConfirm', { name: record.nickname || record.username }),
    icon: createVNode(ExclamationCircleOutlined),
    okType: 'danger',
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await userApi.remove(record.id)
        message.success(t('users.deleteSuccess'))
        emit('reload')
      } catch (e) {
        console.error('[UserDirectory] 删除用户失败', e)
        message.error(t('users.deleteFail'))
      }
    },
  })
}
</script>

<style scoped lang="scss">
.udp-card {
  padding: 0;
  overflow: hidden;
}

/* ── 卡片内工具栏 ── */
.udp-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ds-border-soft);
  flex-wrap: wrap;
}

.udp-search {
  max-width: 260px;
}

.udp-search__icon {
  color: var(--ds-text-faint);
}

.udp-filter {
  width: 170px;
}

.udp-filter--narrow {
  width: 140px;
}

.udp-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--ds-text-faint);
  white-space: nowrap;
}

/* ── 身份列 ── */
.udp-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.udp-identity__text {
  min-width: 0;
}

.udp-identity__name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ds-text);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.udp-identity__sub {
  font-size: 12px;
  color: var(--ds-text-faint);
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.udp-muted {
  color: var(--ds-text-muted);
  font-size: 13px;
}

.udp-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}

</style>
