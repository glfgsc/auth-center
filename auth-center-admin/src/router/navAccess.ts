/**
 * 管理台各页的访问判据 —— 导航显隐与路由守卫共用的唯一一张表，与该页后端控制器的 {@code @PreAuthorize}
 * 逐条对齐，避免「导航里看得见、点进去 403」：
 *
 * | 页            | 后端控制器                              | 判据                          |
 * | ------------- | --------------------------------------- | ----------------------------- |
 * | 登录设置      | SsoAdminController                      | {@code admin:manage_idp}      |
 * | 用户目录      | UserAdminController / GroupAdminController / PermissionSetController | {@code admin:manage_user} 或 {@code admin:manage_permission_set} |
 * | 会话安全      | SessionAdminController                  | isAdmin()                     |
 * | 外部应用      | ConnectedAppController                  | isAdmin()                     |
 * | 审计          | AuditController                         | isAdmin()                     |
 * | 模型用量      | AiUsageController / AiTrustController   | isAdmin()                     |
 * | 系统设置      | PlatformConfigController                | isAdmin()                     |
 *
 * 判据为空数组 = 该页后端仍是 isAdmin() 粗判，故按「是不是 admin」显隐。
 *
 * 用户目录一页承载三块（用户 / 用户组 / 权限集），后端分属两个能力码，故任一即可进入；页内各块自身的可用性仍由
 * 各自接口的 403 决定。
 */

/** 路径 -> 可进入所需的能力码（任一即可）；空数组表示按 admin 判定。 */
const NAV_ACCESS: Record<string, string[]> = {
  '/sso': ['admin:manage_idp'],
  '/users': ['admin:manage_user', 'admin:manage_permission_set'],
  '/sessions': [],
  '/external-apps': [],
  '/notification-channels': [],
  '/audit': [],
  '/model-usage': [],
  '/settings': [],
}

/**
 * 判断某路径是否可进入。
 *
 * @param path 路由路径
 * @param can 能力码判定器
 * @param isAdmin 当前登录者是否持 admin 权限集
 */
export function canEnterPath(
  path: string,
  can: (code: string) => boolean,
  isAdmin: boolean,
): boolean {
  const required = NAV_ACCESS[path]
  if (required === undefined) return true
  if (required.length === 0) return isAdmin
  return required.some(can)
}

/**
 * 取该用户可进入的第一个页 —— 用于根路径重定向与「目标页无权」时的落脚点。
 *
 * @return 路径；一个都进不去时返回 null
 */
export function firstAccessiblePath(
  can: (code: string) => boolean,
  isAdmin: boolean,
): string | null {
  for (const path of Object.keys(NAV_ACCESS)) {
    if (canEnterPath(path, can, isAdmin)) return path
  }
  return null
}
