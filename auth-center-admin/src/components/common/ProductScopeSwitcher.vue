<!--
  ProductScopeSwitcher — 顶栏产品切换器。
  管理台各页的取数口径由它决定;清单来自后端 auth_system 注册表，前端不硬编码。
  状态在 useProductScope(模块级单例),切换即全局生效。
-->
<template>
  <div class="product-scope">
    <AppstoreOutlined class="product-scope__icon" />
    <a-select
      :value="current"
      class="product-scope__select"
      size="small"
      :bordered="false"
      :options="options"
      @change="onChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { AppstoreOutlined } from '@ant-design/icons-vue'

import {
  ALL_PRODUCTS,
  GLOBAL_SCOPE,
  supportsGlobalScope,
  useProductScope,
} from '@/composables/useProductScope'

const { t } = useI18n()
const route = useRoute()
const { products, current, loadProducts, setCurrent } = useProductScope()

// 「全局」只在接受它的页上摆出来:日志与事件类的页选它恒得 0 条(见 GLOBAL_SCOPE_PAGES)。
const options = computed(() => [
  ...products.value
    .filter((p) => p.code !== GLOBAL_SCOPE || supportsGlobalScope(route.path))
    .map((p) => ({ value: p.code, label: p.name || p.code })),
  { value: ALL_PRODUCTS, label: t('product.all') },
])

function onChange(value: unknown): void {
  setCurrent(String(value ?? ALL_PRODUCTS))
}

onMounted(loadProducts)
</script>

<style scoped lang="scss">
.product-scope {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border-left: 1px solid var(--ds-border);
  color: var(--ds-text-muted);
}

.product-scope__icon {
  font-size: 13px;
}

.product-scope__select {
  min-width: 104px;
}
</style>
