/**
 * 当前登录者的能力码 —— 管理台导航与路由的显隐依据。
 *
 * 这不是安全边界。能力码存在 localStorage、可被篡改，只决定「看得见什么」；真正拦住越权的是 auth-center 各控制器
 * 上的 {@code @PreAuthorize}（能力码来自 JWT 快照，客户端改不动）。所以本模块判错最多让人看到一个点进去 403 的菜单。
 *
 * 两级数据来源：
 *   1. localStorage 快照 —— 同步可读，刷新页面时导航不闪；登录时写入。
 *   2. `GET /permission-set` —— 实时从库解析（非 token 快照），启动后异步刷新。因此权限刚被管理员改过也能立刻反映到导航；
 *      但后端强制用的是 token 里的快照，会有「导航已放行、后端仍 403」的窗口，等 access token 刷新（≤15min）或重登即消。
 */
import { computed, ref } from 'vue'
import { myPermissionApi } from '@/api'

/** localStorage 键：能力码 JSON 数组字符串。 */
const STORAGE_KEY_CAPS = 'capabilities'

/** localStorage 键：权限集编码。 */
const STORAGE_KEY_PS = 'permissionSet'

/** admin 权限集编码 —— 尚无专属能力码的管理页按「是不是 admin」显隐，与后端 isAdmin() 同口径。 */
const ADMIN_PERMISSION_SET = 'admin'

const caps = ref<Set<string>>(new Set())
const permissionSet = ref<string>('')

/**
 * 把能力码字符串解析成集合，容忍 JSON 数组与逗号分隔两种历史格式
 * （与后端 SystemPermissionResolver.parseCaps 同口径）。
 */
function parseCaps(raw: string | null | undefined): Set<string> {
  const out = new Set<string>()
  if (!raw) return out
  let text = raw.trim()
  if (text.startsWith('[')) text = text.slice(1)
  if (text.endsWith(']')) text = text.slice(0, -1)
  for (const part of text.split(',')) {
    const code = part.replace(/"/g, '').trim()
    if (code) out.add(code)
  }
  return out
}

/** 从 localStorage 读快照（同步，供首屏渲染用）。 */
function seedFromStorage(): void {
  caps.value = parseCaps(localStorage.getItem(STORAGE_KEY_CAPS))
  permissionSet.value = localStorage.getItem(STORAGE_KEY_PS) ?? ''
}

/** 写入快照 —— 登录成功后调用。 */
export function storePermissionSnapshot(
  capabilities: string | null | undefined,
  ps: string | null | undefined,
): void {
  localStorage.setItem(STORAGE_KEY_CAPS, capabilities ?? '[]')
  localStorage.setItem(STORAGE_KEY_PS, ps ?? '')
  seedFromStorage()
}

/** 清除快照 —— 登出时调用，避免下一个人沿用上一个人的导航。 */
export function clearPermissionSnapshot(): void {
  localStorage.removeItem(STORAGE_KEY_CAPS)
  localStorage.removeItem(STORAGE_KEY_PS)
  caps.value = new Set()
  permissionSet.value = ''
}

/**
 * 向后端拉一次实时权限并覆盖快照。
 *
 * 失败时保留现有快照 —— 网络抖动不该让整个导航塌成空。
 */
export async function refreshCapabilities(): Promise<void> {
  try {
    const res = await myPermissionApi.get()
    const data = res.data
    if (data) {
      storePermissionSnapshot(data.capabilities, data.permissionSet)
    }
  } catch {
    // 保留 localStorage 快照
  }
}

seedFromStorage()

/** 当前登录者的权限视图。 */
export function useCapabilities() {
  const isAdmin = computed(() => permissionSet.value === ADMIN_PERMISSION_SET)

  /** 是否持有某能力码。 */
  function can(code: string): boolean {
    return caps.value.has(code)
  }

  /**
   * 某个受权限控制的目标是否可见。
   *
   * @param cap 专属能力码；缺省表示该目标后端仍是 isAdmin() 粗判，按 admin 显隐
   */
  function allows(cap?: string): boolean {
    return cap ? can(cap) : isAdmin.value
  }

  return { caps, permissionSet, isAdmin, can, allows }
}
