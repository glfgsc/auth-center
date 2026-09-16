<!--
  SectionCard — 设置分区卡:统一的卡片头(标题 + 可选描述 + 可选图标)与内容区。
  图标统一为品牌软色小方块,克制单色,不做彩虹分区。
-->
<template>
  <section class="section-card">
    <header class="section-card__head">
      <span v-if="icon" class="section-card__chip">
        <component :is="icon" />
      </span>
      <div class="section-card__heading">
        <h2 class="section-card__title">{{ title }}</h2>
        <p v-if="description" class="section-card__desc">{{ description }}</p>
      </div>
      <div v-if="$slots.extra" class="section-card__extra">
        <slot name="extra" />
      </div>
    </header>
    <div class="section-card__body">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{
  title: string
  description?: string
  icon?: Component
}>()
</script>

<style scoped lang="scss">
.section-card {
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: var(--ds-radius);
  box-shadow: var(--ds-shadow-sm);
}

.section-card__head {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 16px 20px 0;
}

.section-card__chip {
  width: 28px;
  height: 28px;
  border-radius: var(--ds-radius-sm);
  background: var(--ds-primary-soft);
  color: var(--ds-primary-soft-text);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.section-card__heading {
  flex: 1;
  min-width: 0;
}

.section-card__title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
  line-height: 28px;
}

.section-card__desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--ds-text-faint);
  line-height: 1.5;
}

.section-card__extra {
  flex-shrink: 0;
}

.section-card__body {
  padding: 16px 20px 20px;
}
</style>
