<!--
  用量看板筛选工具条 —— 只管时间范围，三块面板共用同一份条件。
  产品口径由顶栏产品切换器统一决定，此处不再另设下拉：页内再摆一个是冗余，
  且它当初的选项是从汇总结果派生的，选中一个没有用量的产品就会退化成显示原始编码。
  要切产品用顶栏，或直接点下方的产品卡片。
-->
<template>
  <div class="usage-toolbar">
    <FilterOutlined class="usage-toolbar__lead" />
    <div class="usage-ranges">
      <button
        v-for="r in RANGES"
        :key="r.days"
        type="button"
        class="usage-range"
        :class="{ 'usage-range--active': rangeDays === r.days }"
        @click="emit('pick-range', r.days)"
      >
        {{ t(r.labelKey) }}
      </button>
    </div>
    <span class="usage-toolbar__range">{{ rangeText }}</span>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { FilterOutlined } from '@ant-design/icons-vue'
import { RANGES } from '../composables/useModelUsage'

defineProps<{
  /** 当前时间范围(天)。 */
  rangeDays: number
  /** 当前时间窗的可读文本(from ~ to)。 */
  rangeText: string
}>()

const emit = defineEmits<{
  'pick-range': [days: number]
}>()

const { t } = useI18n()
</script>

<style scoped lang="scss">
/* ── 筛选工具条(与审计台同构) ── */
.usage-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 16px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: var(--ds-radius);
}
.usage-toolbar__lead {
  color: var(--ds-text-faint);
  font-size: 14px;
}
.usage-toolbar__range {
  margin-left: auto;
  font-size: 12px;
  color: var(--ds-text-faint);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.usage-ranges {
  display: inline-flex;
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius-sm);
  overflow: hidden;
}
.usage-range {
  padding: 4px 12px;
  border: none;
  background: var(--ds-card-bg);
  color: var(--ds-text-muted);
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;

  & + & {
    border-left: 1px solid var(--ds-border);
  }
  &:hover {
    color: var(--ds-text);
  }
  &--active {
    background: var(--ds-primary-soft);
    color: var(--ds-primary-soft-text);
    font-weight: 600;
  }
}
</style>
