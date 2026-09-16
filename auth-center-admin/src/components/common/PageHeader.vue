<!--
  PageHeader — 页级统一页头:标题(+ 内联元信息槽)+ 描述 + 右侧操作区。
  两个管理页共用,保证标题层级 / 间距 / 操作区节奏一致。
-->
<template>
  <header class="page-header">
    <div class="page-header__text">
      <h1 class="page-header__title">
        {{ title }}
        <slot name="meta" />
      </h1>
      <p v-if="description" class="page-header__desc">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped lang="scss">
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.page-header__text {
  min-width: 0;
}

.page-header__title {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--ds-text);
  line-height: 1.3;
}

.page-header__desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--ds-text-muted);
  line-height: 1.6;
  max-width: 640px;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  padding-top: 2px;
}
</style>
