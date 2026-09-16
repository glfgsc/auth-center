/**
 * 会话/登录历史共享展示辅助 —— 设备解析、时间格式化、地点、异常标记等纯展示逻辑,
 * 供活跃会话面板与登录历史面板共用,避免重复。
 */
import { markRaw } from 'vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  AndroidOutlined,
  AppleOutlined,
  DesktopOutlined,
  GlobalOutlined,
  MobileOutlined,
  WindowsOutlined,
} from '@ant-design/icons-vue'

/** IP 归类:公网(与后端 {@code IpGeoService.CLASS_PUBLIC} 一致)。 */
const IP_CLASS_PUBLIC = 'PUBLIC'

/** 判定为危险级(红)的异常标记。 */
const DANGER_ANOMALIES = new Set(['FAILED_BURST', 'CONCURRENT_LOCATION'])

/** 系统码 → 软色调(四业务系统各一色,便于一眼区分)。 */
const SYSTEM_TONE: Record<string, string> = {
  auth_center: 'neutral',
  bi: 'info',
  agent: 'primary',
  tracking: 'success',
}

/** 一条系统活跃度(展示用)。 */
interface SystemActivityItem {
  code: string
  label: string
  tone: string
  lastSeen: number
}

export function useLoginFormat() {
  const { t } = useI18n()

  /** 用户名首字母(头像占位)。 */
  function initial(name?: string): string {
    return (name || '?').charAt(0).toUpperCase()
  }

  /** User-Agent → 「浏览器 · 系统」。 */
  function device(ua?: string): string {
    if (!ua) return '—'
    let browser = t('sessions.unknownBrowser')
    if (/Edg\//.test(ua)) browser = 'Edge'
    else if (/OPR\//.test(ua)) browser = 'Opera'
    else if (/Chrome\//.test(ua)) browser = 'Chrome'
    else if (/Firefox\//.test(ua)) browser = 'Firefox'
    else if (/Safari\//.test(ua)) browser = 'Safari'
    let os = ''
    if (/Windows/.test(ua)) os = 'Windows'
    else if (/Mac OS/.test(ua)) os = 'macOS'
    else if (/Android/.test(ua)) os = 'Android'
    else if (/iPhone|iPad|iPod|iOS/.test(ua)) os = 'iOS'
    else if (/Linux/.test(ua)) os = 'Linux'
    return os ? `${browser} · ${os}` : browser
  }

  /** User-Agent → 设备图标。 */
  function deviceIcon(ua?: string): Component {
    if (!ua) return markRaw(GlobalOutlined)
    if (/Android/.test(ua)) return markRaw(AndroidOutlined)
    if (/iPhone|iPad|iPod|iOS/.test(ua)) return markRaw(AppleOutlined)
    if (/Mobile/.test(ua)) return markRaw(MobileOutlined)
    if (/Windows/.test(ua)) return markRaw(WindowsOutlined)
    return markRaw(DesktopOutlined)
  }

  /** epoch ms → 「yyyy-MM-dd HH:mm:ss」。 */
  function fmt(ms?: number): string {
    if (!ms) return '—'
    const d = new Date(ms)
    const p = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
  }

  /** ISO 字符串(后端 LocalDateTime)→ 「yyyy-MM-dd HH:mm:ss」。 */
  function fmtStr(s?: string): string {
    return s ? s.replace('T', ' ').slice(0, 19) : '—'
  }

  /** 相对时间(刚刚 / n 分钟前 / n 小时前 / 具体日期)。 */
  function relative(ms?: number): string {
    if (!ms) return '—'
    const diff = Date.now() - ms
    const min = Math.floor(diff / 60_000)
    if (min < 1) return t('sessions.justNow')
    if (min < 60) return t('sessions.minutesAgo', { n: min })
    const hr = Math.floor(min / 60)
    if (hr < 24) return t('sessions.hoursAgo', { n: hr })
    return fmt(ms)
  }

  /** 地点展示:有精确地点显示地点;否则按归类显示「公网 / 内网」。 */
  function locationText(ipClass?: string, location?: string): string {
    if (location) return location
    return ipClass === IP_CLASS_PUBLIC ? t('sessions.publicNet') : t('sessions.internal')
  }

  /** 拆分逗号分隔的异常标记为数组。 */
  function parseAnomalies(s?: string): string[] {
    return s ? s.split(',').map((x) => x.trim()).filter(Boolean) : []
  }

  /** 异常标记 → 本地化标签。 */
  function anomalyLabel(code: string): string {
    return t(`sessions.anomaly.${code}`)
  }

  /** 异常标记 → 软色调(危险级红,其余橙)。 */
  function anomalyTone(code: string): string {
    return DANGER_ANOMALIES.has(code) ? 'danger' : 'warning'
  }

  /** 系统码 → 本地化名称(中心/洞察/知数/循迹);未知码原样返回。 */
  function systemLabel(code: string): string {
    return t(`sessions.system.${code}`)
  }

  /** 系统码 → 软色调。 */
  function systemTone(code: string): string {
    return SYSTEM_TONE[code] ?? 'neutral'
  }

  /** 各系统活跃度 Map → 展示列表(按最近使用时间倒序)。 */
  function systemActivityList(map?: Record<string, number>): SystemActivityItem[] {
    if (!map) return []
    return Object.entries(map)
      .map(([code, lastSeen]) => ({ code, label: systemLabel(code), tone: systemTone(code), lastSeen }))
      .sort((a, b) => b.lastSeen - a.lastSeen)
  }

  return {
    initial,
    device,
    deviceIcon,
    fmt,
    fmtStr,
    relative,
    locationText,
    parseAnomalies,
    anomalyLabel,
    anomalyTone,
    systemLabel,
    systemTone,
    systemActivityList,
  }
}
