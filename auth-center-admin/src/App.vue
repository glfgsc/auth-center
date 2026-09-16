<!--
  Auth Center 管理控制台 — 根组件。
  壳布局复用共享设计系统 @loom/shared-ui/layout 的 AppShell(顶栏 + 左侧导航栏 + 内容区),
  主题切换 / 语言切换由顶栏内置(useTheme + LanguageSwitcher),本组件只注入品牌与用户菜单。
  登录页独立全屏,不套壳布局。Ant 组件深色算法经 a-config-provider 跟随共享 useTheme 的 isDark。
-->
<template>
  <a-config-provider :theme="antThemeConfig">
    <!-- 登录页独立全屏,不套壳布局 -->
    <router-view v-if="isLoginPage" />

    <AppShell v-else :brand="brand" :nav-items="navItems">
      <!-- 品牌区:保留认证中心定制 logo + 「数织 · 中心」多段彩色名 -->
      <template #brand>
        <div class="topnav-logo">
          <svg class="logo-icon" width="28" height="28" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <defs>
              <linearGradient id="logo-grad" x1="0" y1="0" x2="40" y2="40" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stop-color="#0891b2" />
                <stop offset="100%" stop-color="#6366f1" />
              </linearGradient>
            </defs>
            <rect width="40" height="40" rx="10" fill="url(#logo-grad)" />
            <line x1="14" y1="11" x2="14" y2="29" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity="0.9" />
            <line x1="26" y1="11" x2="26" y2="29" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity="0.9" />
            <line x1="11" y1="14" x2="29" y2="14" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity="0.9" />
            <line x1="11" y1="26" x2="29" y2="26" stroke="#fff" stroke-width="2" stroke-linecap="round" opacity="0.9" />
            <circle cx="20" cy="20" r="3" fill="#fbbf24" />
          </svg>
          <span class="logo-text">
            <span class="logo-text-brand">数织</span>
            <span class="logo-text-divider">&middot;</span>
            <span class="logo-text-product">{{ t('app.title') }}</span>
          </span>
        </div>
      </template>

      <!-- 产品切换器:管理台各页的取数口径,切换即全局生效 -->
      <template #topbar-start>
        <ProductScopeSwitcher />
      </template>

      <!-- 用户头像 + 下拉(用户信息 / 登出) -->
      <template #user>
        <a-dropdown :trigger="['click']">
          <a-avatar :size="28" class="user-avatar" style="cursor: pointer;">
            {{ avatarChar }}
          </a-avatar>
          <template #overlay>
            <a-menu @click="onUserMenuClick">
              <a-menu-item key="info" disabled>
                <UserOutlined style="margin-right: 6px;" />
                {{ userName }}
              </a-menu-item>
              <a-menu-divider />
              <a-menu-item key="logout">
                <LogoutOutlined style="margin-right: 6px;" />
                {{ t('common.logout') }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </template>

      <router-view />
    </AppShell>
  </a-config-provider>
</template>

<script setup lang="ts">
import { computed, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { theme } from 'ant-design-vue'
import { ApiOutlined, AuditOutlined, BellOutlined, BarChartOutlined, DesktopOutlined, SafetyOutlined, SettingOutlined, TeamOutlined, UserOutlined, LogoutOutlined } from '@ant-design/icons-vue'
import { AppShell, type ShellNavItem } from '@loom/shared-ui/layout'
import { useTheme } from '@loom/shared-ui/theme'
import { canEnterPath } from '@/router/navAccess'
import { useCapabilities } from '@/composables/useCapabilities'
import { clearAdminSession } from '@/utils/session'
import ProductScopeSwitcher from '@/components/common/ProductScopeSwitcher.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const { can, isAdmin } = useCapabilities()

/** 该路径当前登录者能不能进 —— 判据表见 router/navAccess。 */
const allowsPath = (path: string): boolean => canEnterPath(path, can, isAdmin.value)

// 主题:复用共享 useTheme(data-theme + localStorage key `theme` + 跨实例同步)。
// 顶栏内置切换按钮,此处仅消费 isDark 驱动 Ant 深色算法。
const { isDark } = useTheme()

const isLoginPage = computed(() => route.name === 'Login')

/** 应用壳品牌(顶栏左侧默认值;本 app 经 #brand 槽自定义渲染,name 仍作无障碍/兜底)。 */
const brand = computed(() => ({ name: `数织 · ${t('app.title')}` }))

/**
 * 左栏导航项 —— 只渲染当前登录者有权进入的页。
 *
 * 每项的可见性判据来自 {@link NAV_ACCESS}(与路由守卫同一张表,不允许两处各写一份);
 * 判据本身与后端各控制器的 @PreAuthorize 逐条对齐:已切到细粒度能力码的页按能力码,
 * 其余仍是 isAdmin() 粗判的页按「是不是 admin」。对齐的意义是不出现「看得见、点进去 403」。
 */
const navItems = computed<ShellNavItem[]>(() =>
  [
    { key: 'sso', label: t('nav.sso'), to: '/sso', icon: markRaw(SafetyOutlined) },
    { key: 'users', label: t('nav.users'), to: '/users', icon: markRaw(TeamOutlined) },
    { key: 'sessions', label: t('nav.sessions'), to: '/sessions', icon: markRaw(DesktopOutlined) },
    { key: 'externalApps', label: t('nav.externalApps'), to: '/external-apps', icon: markRaw(ApiOutlined) },
    {
      key: 'notificationChannels',
      label: t('nav.notificationChannels'),
      to: '/notification-channels',
      icon: markRaw(BellOutlined),
    },
    { key: 'audit', label: t('nav.audit'), to: '/audit', icon: markRaw(AuditOutlined) },
    { key: 'modelUsage', label: t('nav.modelUsage'), to: '/model-usage', icon: markRaw(BarChartOutlined) },
    { key: 'settings', label: t('nav.settings'), to: '/settings', icon: markRaw(SettingOutlined) },
  ].filter((item) => allowsPath(item.to)),
)

/** Ant 组件主题:深色/浅色算法跟随共享 isDark + 认证中心品牌主色 / 圆角 / 字号。 */
const antThemeConfig = computed(() => ({
  algorithm: isDark.value ? theme.darkAlgorithm : theme.defaultAlgorithm,
  token: {
    colorPrimary: '#4361EE',
    borderRadius: 8,
    fontSize: 13,
    controlHeight: 36,
  },
}))

// ── 用户信息 ──

const avatarChar = computed(() => {
  try {
    const info = localStorage.getItem('userInfo')
    if (info) {
      const u = JSON.parse(info)
      return (u.nickname || u.username || '?').charAt(0).toUpperCase()
    }
  } catch (e) {
    console.warn('[App] parse userInfo for avatar failed', e)
  }
  return 'A'
})

const userName = computed(() => {
  try {
    const info = localStorage.getItem('userInfo')
    if (info) {
      const u = JSON.parse(info)
      return u.nickname || u.username || ''
    }
  } catch (e) {
    console.warn('[App] parse userInfo for name failed', e)
  }
  return ''
})

function onUserMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    clearAdminSession()
    router.push({ name: 'Login' })
  }
}
</script>

<style scoped lang="scss">
/* 品牌区(注入顶栏 #brand 槽):认证中心定制 logo + 多段彩色名。 */
.topnav-logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-icon {
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 15px;
  font-weight: 600;
  white-space: nowrap;
}

.logo-text-brand { color: var(--ds-text); }
.logo-text-divider { color: var(--ds-text-muted); }
.logo-text-product { color: var(--ds-primary); }

.user-avatar {
  background: var(--ds-primary);
  color: #ffffff;
  margin-left: 8px;
}
</style>
