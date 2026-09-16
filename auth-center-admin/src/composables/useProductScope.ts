/**
 * useProductScope — 管理台的全局产品维度。
 *
 * 顶栏选中的产品是整个管理台的取数口径:各页按它收窄，而不是把所有产品的数据摆在一起。
 * 状态是模块级单例，任何一页切换即全局生效;选择持久化到 localStorage，刷新与换页都保持。
 *
 * 产品清单来自后端 {@code auth_system} 注册表(`GET /admin/systems`),新接入一个产品在那张表
 * 登记一行即自动出现 —— 前端不硬编码产品白名单。
 */
import { computed, ref } from 'vue'

import { systemApi, type AuthSystemDef } from '@/api'

/** 选中产品的持久化键。 */
const STORAGE_KEY = 'auth-center:product-scope'

/**
 * 「全部产品」档的取值 —— 空串。
 *
 * 各 API 的 systemCode 参数为空即不收窄，与后端「参数缺省不过滤」的口径一致，
 * 因此这一档不需要后端另做支持。它是显式选项而非默认值:默认按单个产品看。
 */
export const ALL_PRODUCTS = ''

/** 「全局」档的取值 —— 在 auth_system 注册表里占一行，但它不是产品。 */
export const GLOBAL_SCOPE = 'global'

/**
 * 各页是否接受「全局」档 —— 与 {@code NAV_ACCESS} 同样是这件事的唯一一张表。
 *
 * `global` 表示「不归属任何单个产品」:登录设置的兜底配置、跨产品的权限集 / 用户组 / 平台配置,都真的挂在它
 * 名下。但日志与事件类的页记的是「事情在哪个产品里发生」——不存在发生在「全局」的登录、审计事件或模型调用,
 * 给出这一档只会筛出 0 条,而 0 条会被读成「没有数据」而不是「这档在这页不适用」。故按页声明。
 *
 * | 页        | 产品维度落在哪                                              | 接受 |
 * | --------- | ----------------------------------------------------------- | ---- |
 * | 登录设置  | `auth_sso_config.system_code`                               | 是   |
 * | 用户目录  | `auth_permission_set` / `auth_user_permission_set` / `auth_group` | 是   |
 * | 系统设置  | `auth_platform_config.system_code`                          | 是   |
 * | 会话安全  | Redis 会话活跃度 + `auth_login_history.system_code`          | 否   |
 * | 外部应用  | `auth_connected_app.target_system`                          | 否   |
 * | 通知渠道  | `auth_notification_credential.target_system`                 | 否   |
 * | 审计      | `auth_audit_log.source_system` + `auth_ai_trust_log.system_code` | 否   |
 * | 模型用量  | `auth_ai_trust_log.system_code`                             | 否   |
 *
 * 表里没有的路径按接受处理 —— 那些页不消费产品口径，校正它反而会平白丢掉用户的选择。
 */
const GLOBAL_SCOPE_PAGES: Record<string, boolean> = {
  '/sso': true,
  '/users': true,
  '/settings': true,
  '/sessions': false,
  '/external-apps': false,
  '/notification-channels': false,
  '/audit': false,
  '/model-usage': false,
}

/**
 * 某页是否接受「全局」档。
 *
 * @param path 路由路径
 */
export function supportsGlobalScope(path: string): boolean {
  return GLOBAL_SCOPE_PAGES[path] ?? true
}

// ── 模块级单例状态 ──────────────────────────────────────────────────────────
const products = ref<AuthSystemDef[]>([])
const current = ref<string>(localStorage.getItem(STORAGE_KEY) ?? '')
/** 产品清单只拉一次;失败时复位以便下次进入重试。 */
const loaded = ref(false)
/** 尚未拉到清单前不发业务请求 —— 否则会先用一个待纠正的产品码白取一轮数。 */
const ready = ref(false)

export function useProductScope() {
  /** 拉取产品清单;已加载过则直接返回。 */
  async function loadProducts(): Promise<void> {
    if (loaded.value) return
    loaded.value = true
    try {
      const res = await systemApi.list()
      products.value = res.data ?? []
      const stored = localStorage.getItem(STORAGE_KEY)
      // stored 为 null = 从没选过 → 默认落到第一个产品(而不是「全部」):按产品看才是常态。
      // stored 为空串 = 用户主动选了「全部产品」，尊重它。
      if (stored === null) {
        setCurrent(products.value[0]?.code ?? ALL_PRODUCTS)
      } else if (stored !== ALL_PRODUCTS && !products.value.some((p) => p.code === stored)) {
        // 选中的产品已不在注册表里(下线或改了编码)——留着它会一直按一个不存在的码取数取到空。
        setCurrent(products.value[0]?.code ?? ALL_PRODUCTS)
      }
      ready.value = true
    } catch {
      loaded.value = false
    }
  }

  /** 切换当前产品。 */
  function setCurrent(code: string): void {
    current.value = code
    localStorage.setItem(STORAGE_KEY, code)
  }

  /**
   * 进入某页前校正口径 —— 该页不接受「全局」而当前正选着它时，落到「全部产品」。
   *
   * 不能留着不管:留着就会按一个该页永远匹配不上的码取一轮数，得到 0 条，
   * 而 0 条看起来就是「没有数据」。与上面「选中的产品已不在注册表里就复位」同一个道理。
   * 落「全部产品」而非某个具体产品:它是最不臆断的那一档(压根不收窄)，
   * 且切换器上明摆着，用户看得见口径被改了。
   *
   * @param path 即将进入的路由路径
   */
  function reconcileForPath(path: string): void {
    if (current.value === GLOBAL_SCOPE && !supportsGlobalScope(path)) {
      setCurrent(ALL_PRODUCTS)
    }
  }

  /** 当前产品的注册表项;选「全部产品」或清单未到时为 null。 */
  const currentProduct = computed(
    () => products.value.find((p) => p.code === current.value) ?? null,
  )

  /** 当前是否为「全部产品」档。 */
  const isAllProducts = computed(() => current.value === ALL_PRODUCTS)

  return {
    products,
    current,
    currentProduct,
    isAllProducts,
    ready,
    loadProducts,
    setCurrent,
    reconcileForPath,
  }
}
