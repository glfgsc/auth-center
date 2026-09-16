/**
 * 用量看板的纯展示换算 —— 无状态、无请求，页面与各子组件共用。
 *
 * 口径三条(读数别算错)：合计恒为输入+输出；思考是输出的子集、缓存命中是输入的子集，两者都不另加。
 */
import type { AiGenerationItem, UsageBreakdownRow, UsageSystemRow } from '@/api'

/** 明细表里一律走 token 缩写渲染的列，免得每加一列就要改一次模板。 */
export const NUMERIC_KEYS = new Set([
  'totalTokens',
  'inputTokens',
  'outputTokens',
  'cachedInputTokens',
  'reasoningTokens',
])

/** token 数缩写：千位以下原样，其余按 k / M 保留一位小数。 */
export function fmt(n: number): string {
  const v = Math.round(n || 0)
  if (v < 1000) return String(v)
  const trim = (x: number) => x.toFixed(1).replace(/\.0$/, '')
  return v < 1_000_000 ? `${trim(v / 1000)}k` : `${trim(v / 1_000_000)}M`
}

/** 该产品占全平台的百分比；总量为 0 时给 0%（而不是除零）。 */
export function sharePercent(row: UsageSystemRow, grandTotal: number): string {
  if (!grandTotal) return '0%'
  return `${Math.round((row.totalTokens / grandTotal) * 100)}%`
}

/** 占比越高越「刺眼」：过半用告警色，四分之一以上用主色，其余中性 —— 只标注不评判。 */
export function sharePill(row: UsageSystemRow, grandTotal: number): string {
  if (!grandTotal) return 'pill--neutral'
  const share = row.totalTokens / grandTotal
  if (share >= 0.5) return 'pill--warning'
  if (share >= 0.25) return 'pill--primary'
  return 'pill--neutral'
}

/** 有输入与缓存命中两个数的行 —— 汇总行、明细行、时间桶都算。 */
export interface CacheHitInput {
  inputTokens: number
  cachedInputTokens: number
}

/**
 * 缓存命中率 = 命中 / 输入，百分比保留一位小数；没有输入时无所谓命中，返回 null 由调用方显示占位。
 *
 * 命中是输入的子集，故结果落在 0% ~ 100%。
 */
export function cacheHitRate(row: CacheHitInput): string | null {
  if (!row.inputTokens) return null
  return `${((row.cachedInputTokens / row.inputTokens) * 100).toFixed(1)}%`
}

export function inputShare(row: UsageSystemRow): string {
  return row.totalTokens ? `${(row.inputTokens / row.totalTokens) * 100}%` : '0%'
}

export function outputShare(row: UsageSystemRow): string {
  return row.totalTokens ? `${(row.outputTokens / row.totalTokens) * 100}%` : '0%'
}

/** 这一轮少报了没：真实出网次数多于已记账次数，差出来的那几次没有用量进总和。 */
export function underReported(row: UsageBreakdownRow): boolean {
  return gapOf(row) > 0
}

export function gapOf(row: UsageBreakdownRow): number {
  if (row.httpAttempts == null || row.llmCalls == null) return 0
  return Math.max(0, row.httpAttempts - row.llmCalls)
}

/**
 * 逐次求和 —— 与本行的合计对不上就是 bug，展开区把两边都摆出来让它自己暴露。
 *
 * 入参是已取回的调用级明细而不是行本身:逐次归属已经从 JSON 列行化到 auth_ai_generation,
 * 由 `trustApi.detail` 按需取(见那里的说明),这里只负责算。
 */
export function callSum(calls: AiGenerationItem[], key: keyof AiGenerationItem): number {
  return calls.reduce((sum, c) => sum + (Number(c[key]) || 0), 0)
}
