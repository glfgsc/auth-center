<!--
  无可访问页说明页 —— 账号登录成功但一个管理页都进不去时的落脚点。
  没有它，路由守卫会因为「无处可去」在根路径上打转或渲染空白壳。
-->
<template>
  <div class="no-access">
    <a-result status="403" :title="t('noAccess.title')" :sub-title="t('noAccess.body')">
      <template #extra>
        <a-button type="primary" @click="onLogout">
          {{ t('noAccess.logout') }}
        </a-button>
      </template>
    </a-result>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { clearAdminSession } from '@/utils/session'

const { t } = useI18n()
const router = useRouter()

function onLogout() {
  clearAdminSession()
  void router.push({ name: 'Login' })
}
</script>

<style scoped>
.no-access {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
}
</style>
