<!-- PermSetDrawer — 权限集详情抽屉:基本信息 + 使用统计 + 分组能力清单。 -->
<template>
  <a-drawer
    :open="!!permSet"
    :title="$t('users.psDetail')"
    :width="480"
    @close="$emit('close')"
  >
    <template v-if="permSet">
      <div class="psd-header">
        <div class="psd-header__icon" :style="{ '--accent': psHex(permSet.code) }">
          <component :is="psIcon(permSet.code)" />
        </div>
        <div class="psd-header__text">
          <div class="psd-header__name">{{ permSet.name }}</div>
          <div class="psd-header__tags">
            <a-tag :color="systemColor(permSet.systemCode)" size="small">
              {{ systemLabel(permSet.systemCode) }}
            </a-tag>
            <a-tag v-if="permSet.isSystem" size="small" color="default">
              {{ $t('users.systemTag') }}
            </a-tag>
            <a-tag v-else size="small" color="blue">
              {{ $t('users.customTag') }}
            </a-tag>
          </div>
        </div>
      </div>

      <a-descriptions :column="1" size="small" class="psd-desc">
        <a-descriptions-item :label="$t('users.code')">
          <code class="psd-code">{{ permSet.code }}</code>
        </a-descriptions-item>
        <a-descriptions-item :label="$t('users.description')">
          {{ permSet.description || '—' }}
        </a-descriptions-item>
      </a-descriptions>

      <div class="psd-stats">
        <div class="psd-stat">
          <UserOutlined />
          {{ $t('users.userCount', { n: userCount }) }}
        </div>
        <div class="psd-stat">
          <ThunderboltOutlined />
          {{ $t('users.capCount', { n: parseCaps(permSet.capabilities).length }) }}
        </div>
      </div>

      <div class="psd-caps-title">{{ $t('users.capabilities') }}</div>
      <div class="psd-cap-groups">
        <div v-for="g in capGroups" :key="g.cat" class="psd-cap-group">
          <div class="psd-cap-head">
            <component :is="categoryIcon(g.cat)" class="psd-cap-head__icon" />
            {{ catLabel(g.cat) }}
            <span class="psd-cap-fraction">{{ g.ownedCount }}/{{ g.caps.length }}</span>
          </div>
          <div class="psd-cap-items">
            <span
              v-for="c in g.caps"
              :key="c"
              class="psd-cap-pill"
              :class="{ 'is-on': g.owned.has(c) }"
            >
              <span class="psd-cap-pill__dot" />
              {{ formatCapLabel(c) }}
            </span>
          </div>
        </div>
        <div v-if="!capGroups.length" class="psd-caps-empty">{{ $t('users.noCaps') }}</div>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ThunderboltOutlined, UserOutlined } from '@ant-design/icons-vue'

import type { PermissionSetDef } from '@/api'
import {
  categoryIcon, formatCapLabel, ownedCategories, parseCaps, psHex, psIcon, systemColor,
  type CapCategoryView,
} from '../permsetMeta'

const props = defineProps<{
  permSet: PermissionSetDef | null
  userCount: number
  /** 能力全集矩阵(父层按所有权限集能力并集派生);详情按此渲染真实分布。 */
  capUniverse: CapCategoryView[]
}>()

defineEmits<{ close: [] }>()

const { t } = useI18n()

/** 详情能力清单:该权限集实际触达的分类,域内能力全集 + 拥有高亮 + N/总数,忠实反映权限分布。 */
const capGroups = computed(() => {
  if (!props.permSet) return []
  const owned = new Set(parseCaps(props.permSet.capabilities))
  return ownedCategories(props.capUniverse, owned).map((c) => ({
    cat: c.cat,
    caps: c.caps,
    owned,
    ownedCount: c.caps.reduce((n, code) => n + (owned.has(code) ? 1 : 0), 0),
  }))
})

/** 分类展示名(与权限集编辑器共用 capRes 资源名;缺 i18n 时回退分类编码)。 */
function catLabel(cat: string): string {
  const key = `users.capRes.${cat}`
  const v = t(key)
  return v === key ? cat : v
}

function systemLabel(code: string): string {
  const key = `users.system.${code}`
  const v = t(key)
  return v === key ? code : v
}
</script>

<style scoped lang="scss">
.psd-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 20px;
}

.psd-header__icon {
  width: 48px;
  height: 48px;
  border-radius: var(--ds-radius);
  display: flex;
  align-items: center;
  justify-content: center;
  background: color-mix(in srgb, var(--accent, #3370ff) 12%, transparent);
  color: var(--accent, #3370ff);
  font-size: 24px;
  flex-shrink: 0;
}

.psd-header__name {
  font-size: 18px;
  font-weight: 700;
  color: var(--ds-text);
}

.psd-header__tags {
  display: flex;
  gap: 4px;
  margin-top: 4px;
}

.psd-code {
  font-size: 13px;
  padding: 2px 8px;
  border-radius: var(--ds-radius-sm);
  background: var(--ds-bg-soft);
  color: var(--ds-text-soft);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.psd-desc {
  margin-bottom: 16px;
}

.psd-stats {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: var(--ds-bg-soft);
  border-radius: var(--ds-radius);
}

.psd-stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ds-text-soft);
}

.psd-caps-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
  margin-bottom: 12px;
}

.psd-cap-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.psd-caps-empty {
  font-size: 13px;
  color: var(--ds-text-faint);
  padding: 8px 0;
}

.psd-cap-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--ds-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.psd-cap-head__icon {
  font-size: 14px;
}

.psd-cap-fraction {
  font-weight: 400;
  color: var(--ds-text-faint);
  margin-left: auto;
}

.psd-cap-items {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.psd-cap-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border: 1px solid var(--ds-border);
  border-radius: 999px;
  font-size: 12px;
  color: var(--ds-text-faint);
  text-transform: capitalize;

  &.is-on {
    border-color: var(--ds-primary-soft-border);
    color: var(--ds-primary-soft-text);
    background: var(--ds-primary-soft);
  }
}

.psd-cap-pill__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ds-border);

  .is-on & {
    background: var(--ds-primary);
  }
}
</style>
