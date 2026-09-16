<!--
  LoginView — auth-center 管理控制台登录页。
  分屏外壳复用共享 AuthShell(@loom/shared-ui/layout,与 bi / agent 登录页同源);
  本页只提供 auth-center 特有的表单 + 提交逻辑(RSA 加密 / 超管门 / 跳转)。
-->
<template>
  <AuthShell
    brand-name="数织"
    brand-sub="· 中心"
    :tagline="$t('app.subtitle')"
    :features="[$t('login.feature1'), $t('login.feature2'), $t('login.feature3')]"
    footer="© 2026 数织 · 中心（Loom Center）"
    :welcome="$t('login.welcome')"
    :subtitle="$t('login.subtitle')"
  >
    <a-form :model="form" layout="vertical" @finish="handleLogin">
      <a-alert
        v-if="errorMessage"
        :message="errorMessage"
        type="error"
        show-icon
        closable
        class="form-error"
        @close="errorMessage = ''"
      />
      <a-form-item name="username" :rules="[{ required: true, message: $t('login.inputUsername') }]">
        <a-input
          v-model:value="form.username"
          :placeholder="$t('login.usernamePh')"
          size="large"
          class="form-input"
          @pressEnter="handleLogin"
        >
          <template #prefix><UserOutlined style="color: var(--ds-text-faint)" /></template>
        </a-input>
      </a-form-item>
      <a-form-item name="password" :rules="[{ required: true, message: $t('login.inputPassword') }]">
        <a-input-password
          v-model:value="form.password"
          :placeholder="$t('login.passwordPh')"
          size="large"
          class="form-input"
          @pressEnter="handleLogin"
        >
          <template #prefix><LockOutlined style="color: var(--ds-text-faint)" /></template>
        </a-input-password>
      </a-form-item>
      <a-button
        type="primary"
        html-type="submit"
        size="large"
        block
        :loading="loading"
        class="login-btn"
      >
        {{ $t('login.submit') }}
      </a-button>
    </a-form>
  </AuthShell>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { AuthShell } from '@loom/shared-ui/layout'
import axios from 'axios'
import { encryptLoginPassword } from '@loom/shared-ui/auth'
import { storePermissionSnapshot } from '@/composables/useCapabilities'
import { PRODUCT_CODE } from '@/api'

const router = useRouter()
const route = useRoute()
const { t } = useI18n()

const form = reactive({ username: '', password: '' })
const loading = ref(false)
const errorMessage = ref('')

/**
 * 归一登录后跳转路径。redirect 可能来自两处且格式不一:router beforeEach 存的是基座相对路径
 * (/sso),api 拦截器存的是含 base 的浏览器路径(/admin/sso)。router.replace 会自动加 base,
 * 故必须去掉 /admin 前缀,否则重复成 /admin/admin/* 匹配不到路由 → 空白页。同时只接受应用内
 * 绝对路径(挡 //evil.com 开放重定向),非法/缺省回退 /sso。
 */
function normalizeRedirect(raw: string): string {
  const p = (raw || '').trim()
  if (!p.startsWith('/') || p.startsWith('//')) return '/sso'
  const stripped = p.startsWith('/admin/') ? p.slice('/admin'.length) : p === '/admin' ? '/' : p
  return stripped || '/sso'
}

async function handleLogin() {
  if (!form.username || !form.password) return
  loading.value = true
  errorMessage.value = ''
  try {
    // 登录密码应用层加密:取公钥 + serverTime,RSA-OAEP 加密后提交,不以明文入请求体。
    const keyRes = await axios.get('/api/auth/public-key')
    const keyData = keyRes.data?.data ?? keyRes.data
    const encrypted = await encryptLoginPassword(
      keyData.publicKey,
      keyData.serverTime,
      form.password,
    )
    const res = await axios.post('/api/auth/login', {
      username: form.username,
      password: encrypted,
      system: PRODUCT_CODE,
    })
    const data = res.data?.data ?? res.data
    if (data?.token) {
      // 超管登录门禁:global 全局管理员或 auth_center 系统权限方可访问
      const sp = data.systemPermissions as Record<string, unknown> | undefined
      if (!sp?.auth_center && !sp?.global) {
        errorMessage.value = t('login.superAdminOnly')
        return
      }
      localStorage.setItem('token', data.token)
      if (data.user) {
        localStorage.setItem('userInfo', JSON.stringify(data.user))
      }
      // 能力快照 —— 导航与路由据此显隐;真正的拦截在后端 @PreAuthorize。
      storePermissionSnapshot(data.capabilities, data.permissionSet)
      router.replace(normalizeRedirect((route.query.redirect as string) || ''))
    } else {
      errorMessage.value = res.data?.message || t('login.loginFail')
    }
  } catch (err: unknown) {
    // 这一个 catch 罩着两次请求(取公钥 + 登录),一律说「请检查用户名和密码」会把连不上后端
    // 说成凭据错误 —— port-forward 掉了时 /api/auth/public-key 被 Vite 代理兜底成 500 空 body,
    // 页面却让人反复核对密码。凭据错误只有服务端说了才算。
    const e = err as { response?: { status?: number; data?: { message?: string } } }
    const msg = e?.response?.data?.message
    if (msg) {
      errorMessage.value = msg
    } else if (!e?.response) {
      errorMessage.value = t('login.serviceUnreachable')
    } else if ((e.response.status ?? 0) >= 500) {
      errorMessage.value = t('login.serviceError', { status: e.response.status })
    } else {
      errorMessage.value = t('login.loginFailHint')
    }
  } finally {
    loading.value = false
  }
}
</script>
