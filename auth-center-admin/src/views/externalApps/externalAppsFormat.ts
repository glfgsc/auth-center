/**
 * 外部应用页的纯展示换算。
 *
 * 刻意保持 i18n-free（文案由调用方传入）——纯工具模块一旦 import i18n 实例，任何引用它的
 * 模块或测试都会连带触发 createI18n。
 */

/**
 * 允许回调域名 JSON → 逗号分隔展示串。
 *
 * 空列表与「没配」在界面上是同一句文案（都表示不限制），但解析失败不是：那说明库里存的
 * 不是合法 JSON，要留痕，否则一个写坏的配置在界面上看起来和「没配」一模一样。
 *
 * @param json 后端存的 JSON 数组串
 * @param noDomainsText 未配置时的展示文案
 * @returns 逗号分隔的域名，或 {@link noDomainsText}
 */
export function formatDomains(json: string | undefined, noDomainsText: string): string {
  if (!json) return noDomainsText
  try {
    const list: unknown = JSON.parse(json)
    return Array.isArray(list) && list.length ? list.join(', ') : noDomainsText
  } catch (e) {
    console.warn('[ExternalApps] allowedDomains JSON 解析失败,按未配置展示', json, e)
    return noDomainsText
  }
}

/**
 * ISO 时间串 → 本地日期。
 *
 * @param s ISO 时间串；空值返回空串
 */
export function formatDate(s?: string): string {
  return s ? new Date(s).toLocaleDateString() : ''
}
