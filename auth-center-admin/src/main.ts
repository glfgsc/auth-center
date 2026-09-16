import { createApp } from 'vue'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import '@loom/shared-ui/theme/tokens.css'
import './assets/theme.css'
import { initTheme, initSpinIndicator } from '@loom/shared-ui/theme'

import App from './App.vue'
import { router } from './router'
import { i18n } from './i18n'

// 「数织」双弧编织加载动画指示器(样式由共享 tokens.css 的 .loom-spin 提供)。
initSpinIndicator()

// 首屏前按持久化/系统偏好定 data-theme,避免闪烁(与 bi-front / agent-console 同机制、同 key)。
initTheme()

const app = createApp(App)
app.use(Antd)
app.use(router)
app.use(i18n)

// 等首次路由解析完再挂载。首帧 route.name 仍是 undefined,App.vue 据它选布局会判成「非登录页」→
// 登录页也套上应用壳 → 壳里的产品切换器 onMounted 就把只有管理员能调的产品清单打出去,
// 未登录时必得 401,而 401 兜底是跳登录页 —— 于是登录页把自己踢回登录页,无限重定向。
router.isReady().then(() => app.mount('#app'))
