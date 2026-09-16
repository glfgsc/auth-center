import { createRouter, createWebHistory } from 'vue-router'
import { refreshCapabilities, useCapabilities } from '@/composables/useCapabilities'
import { useProductScope } from '@/composables/useProductScope'
import { canEnterPath, firstAccessiblePath } from './navAccess'

export const router = createRouter({
  history: createWebHistory('/admin/'),
  routes: [
    {
      // 落脚点因人而异 —— 没有 admin:manage_idp 的人进不了 /sso，故由 beforeEach 动态定向
      path: '/',
      name: 'Root',
      component: () => import('@/views/sso/SsoPage.vue'),
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginPage.vue'),
      meta: { public: true },
    },
    {
      path: '/sso',
      name: 'Sso',
      component: () => import('@/views/sso/SsoPage.vue'),
    },
    {
      path: '/users',
      name: 'Users',
      component: () => import('@/views/users/UsersPage.vue'),
    },
    {
      path: '/sessions',
      name: 'Sessions',
      component: () => import('@/views/sessions/SessionsPage.vue'),
    },
    {
      path: '/external-apps',
      name: 'ExternalApps',
      component: () => import('@/views/externalApps/ExternalAppsPage.vue'),
    },
    {
      path: '/notification-channels',
      name: 'NotificationChannels',
      component: () => import('@/views/notificationChannels/NotificationChannelsPage.vue'),
    },
    {
      path: '/audit',
      name: 'Audit',
      component: () => import('@/views/audit/AuditTrailPage.vue'),
    },
    {
      path: '/model-usage',
      name: 'ModelUsage',
      component: () => import('@/views/modelUsage/ModelUsagePage.vue'),
    },
    {
      path: '/settings',
      name: 'Settings',
      component: () => import('@/views/settings/PlatformSettingsPage.vue'),
    },
    {
      path: '/no-access',
      name: 'NoAccess',
      component: () => import('@/views/NoAccessPage.vue'),
    },
  ],
})

/** 是否已向后端要过一次实时权限 —— 每次页面加载拉一次即可。 */
let capabilitiesLoaded = false

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token')
  if (!token) {
    return to.meta.public === true ? true : { name: 'Login', query: { redirect: to.fullPath } }
  }
  if (to.meta.public === true) return true

  // 口径校正必须早于页面取数:目标页不接受「全局」时先落回「全部产品」,
  // 否则该页会按一个它永远匹配不上的产品码取一轮空数。
  useProductScope().reconcileForPath(to.path)

  // 首次进入先取实时权限，否则刚被改过权限的用户会按旧快照定向。
  // 失败时 refreshCapabilities 内部保留 localStorage 快照，不阻断导航。
  if (!capabilitiesLoaded) {
    capabilitiesLoaded = true
    await refreshCapabilities()
  }

  const { can, isAdmin } = useCapabilities()
  const fallback = firstAccessiblePath(can, isAdmin.value)

  // 根路径：定向到该用户第一个进得去的页
  if (to.path === '/') {
    return fallback ? { path: fallback, replace: true } : { name: 'NoAccess', replace: true }
  }

  if (canEnterPath(to.path, can, isAdmin.value)) return true

  // 无权进入目标页：有别的页可去就去，一个都进不去则落到说明页（而不是空白或死循环）
  return fallback ? { path: fallback, replace: true } : { name: 'NoAccess', replace: true }
})
