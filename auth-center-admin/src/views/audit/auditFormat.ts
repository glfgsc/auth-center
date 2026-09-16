/** 审计台两张表共用的展示换算。 */

/**
 * 后端下发的 ISO 时间串 → 表格里的紧凑写法（``2026-08-07 12:34:56``）。
 *
 * @param s ISO 时间串；空值返回破折号占位。
 */
export function fmtTime(s?: string): string {
  return s ? s.replace('T', ' ').slice(0, 19) : '—'
}
