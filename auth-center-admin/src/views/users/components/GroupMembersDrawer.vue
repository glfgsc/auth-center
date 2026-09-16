<!--
  GroupMembersDrawer — 用户组成员管理抽屉。
  顶部远程搜人(userSearchApi,300ms 防抖)添加,下方成员列表逐条移除。
  成员变更后 emit changed 供父层刷新成员计数。
-->
<template>
  <a-drawer
    :open="open"
    :title="group ? $t('groups.membersTitle', { name: group.name }) : ''"
    :width="480"
    @close="$emit('close')"
  >
    <a-select
      class="gmd-search"
      show-search
      :value="undefined"
      :placeholder="$t('groups.searchUserPh')"
      :filter-option="false"
      :not-found-content="searching ? undefined : null"
      :options="searchOptions"
      @search="onSearch"
      @select="onAdd"
    >
      <template v-if="searching" #notFoundContent>
        <a-spin size="small" />
      </template>
    </a-select>

    <a-list
      class="gmd-list"
      :data-source="members"
      :loading="loading"
      item-layout="horizontal"
      :locale="{ emptyText: $t('groups.noMembers') }"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <template #actions>
            <a-button
              type="text"
              danger
              size="small"
              :loading="removingId === item.userId"
              @click="onRemove(item)"
            >
              {{ $t('groups.removeMember') }}
            </a-button>
          </template>
          <a-list-item-meta>
            <template #avatar>
              <a-avatar :style="{ background: avatarColor(item.username || String(item.userId)) }">
                {{ (item.nickname || item.username || '?')[0].toUpperCase() }}
              </a-avatar>
            </template>
            <template #title>{{ item.nickname || item.username }}</template>
            <template #description>
              <span class="gmd-sub">{{ item.username }}{{ item.email ? ` · ${item.email}` : '' }}</span>
            </template>
          </a-list-item-meta>
        </a-list-item>
      </template>
    </a-list>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'

import { groupApi, userSearchApi, type AuthGroup, type GroupMember, type UserSearchResult } from '@/api'
import { avatarColor } from '../permsetMeta'

const props = defineProps<{ open: boolean; group: AuthGroup | null }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const { t } = useI18n()

const members = ref<GroupMember[]>([])
const loading = ref(false)
const searchResults = ref<UserSearchResult[]>([])
const searching = ref(false)
const removingId = ref<number | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 搜索结果排除已是成员的用户。
const searchOptions = computed(() =>
  searchResults.value
    .filter((u) => !members.value.some((m) => m.userId === u.id))
    .map((u) => ({ value: u.id, label: `${u.nickname || u.username} (${u.username})` })),
)

watch(
  () => [props.open, props.group?.id] as const,
  ([open]) => {
    if (open && props.group) {
      searchResults.value = []
      void loadMembers()
    }
  },
  { immediate: true },
)

async function loadMembers(): Promise<void> {
  if (!props.group) return
  loading.value = true
  try {
    const res = await groupApi.listMembers(props.group.id)
    members.value = res.data ?? []
  } catch {
    members.value = []
  } finally {
    loading.value = false
  }
}

function onSearch(q: string): void {
  if (searchTimer) clearTimeout(searchTimer)
  const kw = q.trim()
  if (!kw) {
    searchResults.value = []
    searching.value = false
    return
  }
  searching.value = true
  searchTimer = setTimeout(async () => {
    try {
      const res = await userSearchApi.search(kw, 10)
      searchResults.value = res.data ?? []
    } catch {
      searchResults.value = []
    } finally {
      searching.value = false
    }
  }, 300)
}

async function onAdd(userId: number): Promise<void> {
  if (!props.group) return
  try {
    await groupApi.addMembers(props.group.id, [userId])
    message.success(t('groups.memberAddSuccess'))
    searchResults.value = []
    await loadMembers()
    emit('changed')
  } catch {
    message.error(t('groups.memberOpFail'))
  }
}

async function onRemove(item: GroupMember): Promise<void> {
  if (!props.group) return
  removingId.value = item.userId
  try {
    await groupApi.removeMembers(props.group.id, [item.userId])
    message.success(t('groups.memberRemoveSuccess'))
    await loadMembers()
    emit('changed')
  } catch {
    message.error(t('groups.memberOpFail'))
  } finally {
    removingId.value = null
  }
}
</script>

<style scoped lang="scss">
.gmd-search {
  width: 100%;
  margin-bottom: 12px;
}

.gmd-sub {
  font-size: 12px;
  color: var(--ds-text-faint);
}
</style>
