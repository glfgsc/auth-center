import { clearToken } from '@loom/shared-ui/request'
import { clearPermissionSnapshot } from '@/composables/useCapabilities'

/**
 * 清空本地登录态 —— 令牌(token / refreshToken)+ 用户信息 + 能力快照。
 *
 * 三处登出入口(顶栏菜单、无权限页、吊销自己的会话)共用这一份:漏清能力快照的话,
 * 下一个登录者会先看到上一个人的导航。请求层 401 兜底另走
 * {@code createRequestClient} 的 onLogout(那条路径随后整页跳登录页,内存态一并作废)。
 */
export function clearAdminSession(): void {
  clearToken()
  localStorage.removeItem('userInfo')
  clearPermissionSnapshot()
}
