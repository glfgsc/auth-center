<!--
  GroupsPanel — 用户组 Tab:卡片内工具栏(搜索 + 新建)+ 用户组表。
  成员计数按组懒加载;内含编辑抽屉与成员管理抽屉。组的增删改后 emit reload 供父层刷新。
-->
<template>
  <div class="tn-card grp-card">
    <div class="grp-toolbar">
      <a-input
        v-model:value="search"
        :placeholder="$t('groups.search')"
        allow-clear
        class="grp-search"
      >
        <template #prefix><SearchOutlined class="grp-search__icon" /></template>
      </a-input>
      <span class="grp-count">{{ $t('groups.totalCount', { n: filtered.length }) }}</span>
      <a-button type="primary" @click="openCreate">
        <template #icon><PlusOutlined /></template>
        {{ $t('groups.add') }}
      </a-button>
    </div>

    <a-table
      :data-source="filtered"
      :columns="columns"
      :loading="loading"
      row-key="id"
      :pagination="{ pageSize: 20, showSizeChanger: true }"
      size="middle"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'name'">
          <div class="grp-name">{{ record.name }}</div>
        </template>

        <template v-else-if="column.key === 'code'">
          <code class="grp-code">{{ record.code }}</code>
          <a-tag v-if="record.isSystem" color="default" class="grp-systag">{{ $t('groups.systemTag') }}</a-tag>
        </template>

        <template v-else-if="column.key === 'members'">
          <a-button type="link" size="small" class="grp-members" @click="openMembers(record)">
            <TeamOutlined />
            {{ $t('groups.memberCount', { n: counts[record.id] ?? 0 }) }}
          </a-button>
        </template>

        <template v-else-if="column.key === 'createdAt'">
          <span class="grp-muted">{{ formatTime(record.createdAt) }}</span>
        </template>

        <template v-else-if="column.key === 'actions'">
          <div class="grp-actions">
            <a-tooltip :title="$t('common.edit')">
              <a-button type="text" size="small" @click="openEdit(record)">
                <EditOutlined />
              </a-button>
            </a-tooltip>
            <a-tooltip :title="$t('common.delete')">
              <a-button type="text" size="small" danger :disabled="record.isSystem === 1" @click="confirmDelete(record)">
                <DeleteOutlined />
              </a-button>
            </a-tooltip>
          </div>
        </template>
      </template>
    </a-table>

    <GroupEditorDrawer
      :open="editorOpen"
      :record="editorRecord"
      @close="editorOpen = false"
      @saved="onSaved"
    />
    <GroupMembersDrawer
      :open="membersOpen"
      :group="membersGroup"
      @close="membersOpen = false"
      @changed="reloadCounts"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, createVNode, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Modal, message } from 'ant-design-vue'
import {
  DeleteOutlined, EditOutlined, ExclamationCircleOutlined, PlusOutlined, SearchOutlined, TeamOutlined,
} from '@ant-design/icons-vue'

import { groupApi, type AuthGroup } from '@/api'
import { formatTime } from '../permsetMeta'
import GroupEditorDrawer from './GroupEditorDrawer.vue'
import GroupMembersDrawer from './GroupMembersDrawer.vue'

const props = defineProps<{ groups: AuthGroup[]; loading: boolean }>()
const emit = defineEmits<{ reload: [] }>()

const { t } = useI18n()

const search = ref('')
const counts = ref<Record<number, number>>({})
const editorOpen = ref(false)
const editorRecord = ref<AuthGroup | null>(null)
const membersOpen = ref(false)
const membersGroup = ref<AuthGroup | null>(null)

const columns = computed(() => [
  { title: t('groups.name'), key: 'name' },
  { title: t('groups.code'), key: 'code', width: 220 },
  { title: t('groups.members'), key: 'members', width: 120 },
  { title: t('groups.createdAt'), key: 'createdAt', width: 170 },
  { title: t('groups.actions'), key: 'actions', width: 90, align: 'center' as const },
])

const filtered = computed(() => {
  const q = search.value.trim().toLowerCase()
  if (!q) return props.groups
  return props.groups.filter(
    (g) => g.name.toLowerCase().includes(q) || g.code.toLowerCase().includes(q),
  )
})

// 组清单变化时懒加载各组成员计数(组数量少,N 次并发可接受)。
watch(
  () => props.groups.map((g) => g.id).join(','),
  () => reloadCounts(),
  { immediate: true },
)

async function reloadCounts(): Promise<void> {
  const results = await Promise.allSettled(props.groups.map((g) => groupApi.listMembers(g.id)))
  const next: Record<number, number> = {}
  props.groups.forEach((g, i) => {
    const r = results[i]
    next[g.id] = r.status === 'fulfilled' ? (r.value.data?.length ?? 0) : 0
  })
  counts.value = next
}

function openCreate(): void {
  editorRecord.value = null
  editorOpen.value = true
}
function openEdit(record: AuthGroup): void {
  editorRecord.value = record
  editorOpen.value = true
}
function openMembers(record: AuthGroup): void {
  membersGroup.value = record
  membersOpen.value = true
}
function onSaved(): void {
  emit('reload')
}

function confirmDelete(record: AuthGroup): void {
  Modal.confirm({
    title: t('groups.deleteConfirm', { name: record.name }),
    icon: createVNode(ExclamationCircleOutlined),
    okType: 'danger',
    okText: t('common.delete'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      try {
        await groupApi.remove(record.id)
        message.success(t('groups.deleteSuccess'))
        emit('reload')
      } catch {
        message.error(t('groups.deleteFail'))
      }
    },
  })
}
</script>

<style scoped lang="scss">
.grp-card {
  padding: 0;
  overflow: hidden;
}

.grp-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ds-border-soft);
}

.grp-search {
  max-width: 260px;
}
.grp-search__icon {
  color: var(--ds-text-faint);
}

.grp-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--ds-text-faint);
  white-space: nowrap;
}

.grp-name {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ds-text);
}

.grp-code {
  font-size: 12px;
  padding: 1px 6px;
  border-radius: var(--ds-radius-sm);
  background: var(--ds-bg-soft);
  color: var(--ds-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.grp-systag {
  margin-left: 6px;
}

.grp-members {
  padding: 0;
}

.grp-muted {
  color: var(--ds-text-muted);
  font-size: 13px;
}

.grp-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
</style>
