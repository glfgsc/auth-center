<!--
  用户表的「权限集」单元格 —— 徽章平铺(超量折叠为 +N)+ 点击弹出按系统分组的分配面板。
  勾选/取消以事件上抛给父层,本组件不直接调 API。
-->
<template>
  <a-popover trigger="click" placement="bottomLeft">
    <div class="udp-ps-cell">
      <template v-if="user.permissionSets?.length">
        <a-tag
          v-for="ps in visibleTags"
          :key="`${ps.systemCode}-${ps.id}`"
          :color="systemColor(ps.systemCode)"
          class="udp-ps-tag"
        >
          <component :is="psIcon(ps.code)" class="udp-ps-tag__icon" />
          {{ ps.name || ps.code }}
        </a-tag>
        <span v-if="hiddenCount > 0" class="udp-ps-more">
          {{ $t('users.moreCount', { n: hiddenCount }) }}
        </span>
      </template>
      <span v-else class="udp-muted">—</span>
      <span class="udp-ps-edit"><EditOutlined /></span>
    </div>

    <template #content>
      <div class="udp-picker">
        <div class="udp-picker__head">{{ $t('users.assignTitle') }}</div>
        <template v-for="sys in systemList" :key="sys">
          <div v-if="systemPermSets(sys).length > 0" class="udp-picker__section">
            <div class="udp-picker__title">
              <span class="udp-picker__dot" :style="{ background: systemHex(sys) }" />
              {{ systemLabel(sys) }}
            </div>
            <div
              v-for="opt in systemPermSets(sys)"
              :key="opt.code"
              class="udp-picker__option"
              :class="{ 'is-active': hasUserPs(sys, opt.code) }"
              @click="emit('assign', user, opt.code)"
            >
              <component
                :is="psIcon(opt.code)"
                :style="{ color: psHex(opt.code), fontSize: '15px' }"
              />
              <span class="udp-picker__name">{{ opt.name }}</span>
              <CheckOutlined v-if="hasUserPs(sys, opt.code)" class="udp-picker__check" />
            </div>
          </div>
        </template>
      </div>
    </template>
  </a-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckOutlined, EditOutlined } from '@ant-design/icons-vue'
import type { AuthUser, PermissionSetDef, UserPermissionSetInfo } from '@/api'
import { psHex, psIcon, systemColor, systemHex } from '../permsetMeta'

/** 权限集徽章最多平铺个数,超出折叠为 +N(点击单元格仍可在分配面板查看全部)。 */
const MAX_VISIBLE_TAGS = 3

const props = defineProps<{
  /** 该行用户。 */
  user: AuthUser
  /** 全部权限集定义(分配面板按系统分组展示)。 */
  permissionSets: PermissionSetDef[]
  /** 系统清单(决定分组顺序)。 */
  systemList: readonly string[]
}>()

const emit = defineEmits<{ assign: [user: AuthUser, code: string] }>()

const { t } = useI18n()

const visibleTags = computed<UserPermissionSetInfo[]>(() =>
  (props.user.permissionSets ?? []).slice(0, MAX_VISIBLE_TAGS),
)

const hiddenCount = computed(() =>
  Math.max(0, (props.user.permissionSets?.length ?? 0) - MAX_VISIBLE_TAGS),
)

function systemLabel(code: string): string {
  const key = `users.system.${code}`
  const v = t(key)
  return v === key ? code : v
}

function systemPermSets(systemCode: string): PermissionSetDef[] {
  return props.permissionSets.filter((ps) => ps.systemCode === systemCode)
}

function hasUserPs(systemCode: string, psCode: string): boolean {
  return (
    props.user.permissionSets?.some(
      (ps) => ps.systemCode === systemCode && ps.code === psCode,
    ) ?? false
  )
}
</script>

<style scoped lang="scss">
.udp-muted {
  color: var(--ds-text-faint);
}

/* ── 权限集列(悬停出现编辑入口) ── */
.udp-ps-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  cursor: pointer;
  min-height: 24px;
  border-radius: var(--ds-radius-sm);
}

.udp-ps-tag {
  cursor: pointer;
  margin-inline-end: 0 !important;
}

.udp-ps-tag__icon {
  margin-right: 4px;
}

.udp-ps-more {
  font-size: 12px;
  color: var(--ds-text-muted);
  background: var(--ds-neutral-soft);
  padding: 0 7px;
  line-height: 20px;
  border-radius: 999px;
}

.udp-ps-edit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: var(--ds-radius-sm);
  color: var(--ds-text-faint);
  opacity: 0;
  transition: opacity 0.15s, background 0.15s;
  font-size: 12px;
}

:deep(.ant-table-row:hover) .udp-ps-edit {
  opacity: 1;
}

.udp-ps-cell:hover .udp-ps-edit {
  background: var(--ds-hover-bg);
  color: var(--ds-text-soft);
}

.udp-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
}
/* ── 分配面板 ── */
.udp-picker {
  min-width: 240px;
  max-width: 320px;
  max-height: 420px;
  overflow-y: auto;
}

.udp-picker__head {
  font-size: 13px;
  font-weight: 600;
  color: var(--ds-text);
  padding-bottom: 8px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--ds-border-soft);
}

.udp-picker__section {
  &:not(:last-child) {
    margin-bottom: 8px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--ds-border-soft);
  }
}

.udp-picker__title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: var(--ds-text-faint);
  margin-bottom: 4px;
  letter-spacing: 0.04em;
}

.udp-picker__dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.udp-picker__option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--ds-radius-sm);
  cursor: pointer;
  transition: background 0.15s;

  &:hover {
    background: var(--ds-hover-bg);
  }

  &.is-active {
    background: var(--ds-active-bg);
  }
}

.udp-picker__name {
  flex: 1;
  font-size: 13px;
  font-weight: 500;
  color: var(--ds-text);
}

.udp-picker__check {
  color: var(--ds-primary);
  font-size: 12px;
}
</style>
