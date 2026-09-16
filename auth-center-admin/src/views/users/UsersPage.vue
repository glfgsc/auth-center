<!--
  UsersView — 用户与权限。
  页头 + 下划线 Tab;Tab1 用户目录(UserDirectoryPanel),Tab2 权限集画廊(PermSetGallery)+
  详情抽屉(PermSetDrawer),Tab3 用户组(GroupsPanel)。各面板自持增删改抽屉,变更后 emit reload。
-->
<template>
  <div class="ac-page up-page">
    <PageHeader :title="$t('users.title')" />

    <nav class="up-tabs" role="tablist">
      <button
        v-for="td in tabDefs"
        :key="td.key"
        type="button"
        role="tab"
        class="up-tab"
        :class="{ 'up-tab--active': tab === td.key }"
        :aria-selected="tab === td.key"
        @click="tab = td.key"
      >
        <component :is="td.icon" class="up-tab__icon" />
        {{ td.label }}
        <span class="up-tab__count">{{ td.count }}</span>
      </button>
    </nav>

    <UserDirectoryPanel
      v-if="tab === 'users'"
      :users="users"
      :permission-sets="allPermissionSets"
      :systems="systemCodes"
      :loading="loading"
      @assign="assignPs"
      @reload="load"
    />

    <PermSetGallery
      v-else-if="tab === 'permSets'"
      :permission-sets="allPermissionSets"
      :users="users"
      :systems="systemCodes"
      :cap-universe="capUniverse"
      @select="selectedPs = $event"
      @reload="load"
    />

    <GroupsPanel
      v-else
      :groups="groups"
      :loading="loading"
      @reload="load"
    />

    <PermSetDrawer
      :perm-set="selectedPs"
      :user-count="selectedPsUserCount"
      :cap-universe="capUniverse"
      @close="selectedPs = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { message } from 'ant-design-vue'
import { KeyOutlined, TeamOutlined, UsergroupAddOutlined } from '@ant-design/icons-vue'

import {
  groupApi, systemApi, userApi,
  type AuthGroup, type AuthSystemDef, type AuthUser, type PermissionSetDef,
} from '@/api'
import { useProductScope } from '@/composables/useProductScope'
import PageHeader from '@/components/common/PageHeader.vue'
import UserDirectoryPanel from './components/UserDirectoryPanel.vue'
import PermSetGallery from './components/PermSetGallery.vue'
import PermSetDrawer from './components/PermSetDrawer.vue'
import GroupsPanel from './components/GroupsPanel.vue'
import { buildCapUniverse } from './permsetMeta'

const { t } = useI18n()
const { current } = useProductScope()

const tab = ref<'users' | 'permSets' | 'groups'>('users')
const loading = ref(false)
const users = ref<AuthUser[]>([])
const allPermissionSets = ref<PermissionSetDef[]>([])
const systems = ref<AuthSystemDef[]>([])
const groups = ref<AuthGroup[]>([])
const selectedPs = ref<PermissionSetDef | null>(null)

/** 系统分组顺序:后端 auth_system 清单驱动;组件在其为空时回退 KNOWN_SYSTEMS。 */
const systemCodes = computed(() => systems.value.map((s) => s.code))

/** 能力全集矩阵:所有权限集能力的并集按域前缀派生(卡片矩阵 + 详情抽屉共用,反映系统实际权限分布)。 */
const capUniverse = computed(() => buildCapUniverse(allPermissionSets.value))

const tabDefs = computed(() => [
  { key: 'users' as const, label: t('users.tabUsers'), icon: markRaw(TeamOutlined), count: users.value.length },
  { key: 'permSets' as const, label: t('users.tabPermSets'), icon: markRaw(KeyOutlined), count: allPermissionSets.value.length },
  { key: 'groups' as const, label: t('users.tabGroups'), icon: markRaw(UsergroupAddOutlined), count: groups.value.length },
])

const selectedPsUserCount = computed(() => {
  const code = selectedPs.value?.code
  if (!code) return 0
  return users.value.filter((u) => u.permissionSets?.some((ps) => ps.code === code)).length
})

async function load(): Promise<void> {
  loading.value = true
  try {
    // 各端点独立降级:任一失败不清空其余数据(见 systems 端点缺失历史)。
    // 用户按「在该产品下持有权限集」收窄;systems 是产品注册表本身，不收窄。
    const scope = current.value || undefined
    const [usersRes, psRes, sysRes, groupsRes] = await Promise.allSettled([
      userApi.list(scope),
      userApi.listPermissionSets(scope),
      systemApi.list(),
      groupApi.list(scope),
    ])
    users.value = usersRes.status === 'fulfilled' ? usersRes.value.data ?? [] : []
    allPermissionSets.value = psRes.status === 'fulfilled' ? psRes.value.data ?? [] : []
    systems.value = sysRes.status === 'fulfilled' ? sysRes.value.data ?? [] : []
    groups.value = groupsRes.status === 'fulfilled' ? groupsRes.value.data ?? [] : []
  } finally {
    loading.value = false
  }
}

async function assignPs(record: AuthUser, code: string): Promise<void> {
  try {
    await userApi.assignPermissionSet(record.id, code)
    message.success(`${t('users.assignSuccess')} ${t('users.permEffectHint')}`)
    await load()
  } catch {
    message.error(t('users.assignFail'))
  }
}

watch(current, load)

onMounted(load)
</script>

<style scoped lang="scss">
/* ── 下划线 Tabs ── */
.up-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--ds-border);
}

.up-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 14px;
  margin-bottom: -1px;
  border: none;
  background: none;
  cursor: pointer;
  font: inherit;
  font-size: 13.5px;
  color: var(--ds-text-muted);
  border-bottom: 2px solid transparent;
  transition: color 0.15s, border-color 0.15s;

  &:hover {
    color: var(--ds-text);
  }

  &--active {
    color: var(--ds-primary);
    font-weight: 600;
    border-bottom-color: var(--ds-primary);
  }
}

.up-tab__icon {
  font-size: 14px;
}

.up-tab__count {
  font-size: 11px;
  line-height: 18px;
  padding: 0 7px;
  border-radius: 999px;
  background: var(--ds-neutral-soft);
  color: var(--ds-text-muted);

  .up-tab--active & {
    background: var(--ds-primary-soft);
    color: var(--ds-primary-soft-text);
  }
}
</style>
