<!--
  单个权限集卡片 —— 名称 / 类型标签 / 描述 / 用户数与能力数 / 能力点阵缩略。
  点击进详情由父层处理;编辑与删除以事件上抛,本组件不直接调 API。
-->
<template>
  <div
    class="psg-item"
    role="button"
    tabindex="0"
    :style="{ '--accent': psHex(ps.code) }"
    @click="emit('select', ps)"
    @keydown.enter="emit('select', ps)"
  >
    <div class="psg-item__actions" @click.stop>
      <a-tooltip :title="$t('common.edit')">
        <a-button type="text" size="small" @click="emit('edit', ps)"><EditOutlined /></a-button>
      </a-tooltip>
      <a-tooltip :title="ps.isSystem ? $t('users.cantDeleteSystemPs') : $t('common.delete')">
        <a-button
          type="text"
          size="small"
          danger
          :disabled="ps.isSystem === 1"
          @click="emit('remove', ps)"
        >
          <DeleteOutlined />
        </a-button>
      </a-tooltip>
    </div>
    <div class="psg-item__header">
      <span class="psg-item__icon">
        <component :is="psIcon(ps.code)" />
      </span>
      <span class="psg-item__name">{{ ps.name }}</span>
      <span class="tn-pill" :class="ps.isSystem ? 'tn-pill-neutral' : 'tn-pill-info'">
        {{ ps.isSystem ? $t('users.systemTag') : $t('users.customTag') }}
      </span>
    </div>
    <div class="psg-item__desc">{{ ps.description || '—' }}</div>
    <div class="psg-item__footer">
      <span class="psg-item__stat">
        <UserOutlined /> {{ $t('users.userCount', { n: userCount }) }}
      </span>
      <span class="psg-item__stat">
        <ThunderboltOutlined /> {{ $t('users.capCount', { n: capCount }) }}
      </span>
    </div>
    <div class="psg-item__matrix">
      <div v-for="row in matrix" :key="row.cat" class="psg-matrix-row" :title="catLabel(row.cat)">
        <component :is="categoryIcon(row.cat)" class="psg-matrix-row__icon" />
        <span class="psg-matrix-dots">
          <span
            v-for="c in row.caps"
            :key="c"
            class="psg-matrix-dot"
            :class="{ 'is-on': row.owned.has(c) }"
          />
        </span>
      </div>
      <div v-if="!matrix.length" class="psg-matrix-empty">{{ $t('users.noCaps') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { DeleteOutlined, EditOutlined, ThunderboltOutlined, UserOutlined } from '@ant-design/icons-vue'
import type { PermissionSetDef } from '@/api'
import { categoryIcon, parseCaps, psHex, psIcon } from '../permsetMeta'

/** 能力点阵的一行:某分类下的能力全集 + 本权限集实际拥有的那些。 */
export interface MatrixRow {
  cat: string
  caps: string[]
  owned: Set<string>
}

const props = defineProps<{
  /** 权限集定义。 */
  ps: PermissionSetDef
  /** 持有该权限集的用户数。 */
  userCount: number
  /** 能力点阵行(由父层按能力全集派生)。 */
  matrix: MatrixRow[]
}>()

const emit = defineEmits<{
  select: [ps: PermissionSetDef]
  edit: [ps: PermissionSetDef]
  remove: [ps: PermissionSetDef]
}>()

const { t } = useI18n()

const capCount = computed(() => parseCaps(props.ps.capabilities).length)

/** 分类展示名(与权限集编辑器共用 capRes 资源名;缺 i18n 时回退分类编码,供矩阵行 hover 提示)。 */
function catLabel(cat: string): string {
  const key = `users.capRes.${cat}`
  const v = t(key)
  return v === key ? cat : v
}
</script>

<style scoped lang="scss">
.psg-item {
  position: relative;
  text-align: left;
  border: 1px solid var(--ds-border);
  border-top: 2px solid var(--accent, var(--ds-border));
  border-radius: var(--ds-radius);
  padding: 16px;
  background: var(--ds-card-bg);
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;

  &:hover {
    border-color: var(--accent, var(--ds-primary));
    border-top-color: var(--accent, var(--ds-primary));
    box-shadow: var(--ds-shadow);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px var(--ds-primary-soft-border);
  }

  &:hover .psg-item__actions {
    opacity: 1;
  }
}

.psg-item__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.psg-item__header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  padding-right: 56px;
  min-width: 0;
}

.psg-item__icon {
  width: 32px;
  height: 32px;
  border-radius: var(--ds-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--accent, #3370ff) 10%, transparent);
  color: var(--accent, #3370ff);
  font-size: 16px;
  flex-shrink: 0;
}

.psg-item__name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.psg-item__desc {
  font-size: 12px;
  color: var(--ds-text-muted);
  margin-bottom: 10px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.psg-item__footer {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--ds-text-soft);
  margin-bottom: 10px;
}

.psg-item__stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

/* ── 能力点阵 ── */
.psg-item__matrix {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.psg-matrix-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.psg-matrix-row__icon {
  font-size: 12px;
  color: var(--ds-text-faint);
  width: 14px;
  flex-shrink: 0;
}

/* 域内能力可能较多(如智能体 12 个),超出卡片宽度时换行,不裁切。 */
.psg-matrix-dots {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 3px;
}

.psg-matrix-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: var(--ds-border);

  &.is-on {
    background: var(--accent, var(--ds-primary));
  }
}

.psg-matrix-empty {
  font-size: 11px;
  color: var(--ds-text-faint);
  padding: 2px 0;
}
</style>
