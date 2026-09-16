/**
 * permsetMeta — 用户与权限页共享的展示元数据与纯函数。
 *
 * 系统/权限集的图标、色值、能力分类矩阵与解析工具,
 * 供用户表、权限集画廊、详情抽屉三个组件复用。
 */
import { markRaw, type Component } from 'vue'
import {
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  CrownOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  EyeOutlined,
  FundOutlined,
  KeyOutlined,
  PartitionOutlined,
  ReadOutlined,
  RobotOutlined,
  SafetyOutlined,
  SettingOutlined,
  ToolOutlined,
} from '@ant-design/icons-vue'

/**
 * 系统编码兜底清单(后端 /admin/systems 拉取失败时的 fail-safe 默认;
 * 正常运行时系统清单与顺序由后端 auth_system 表驱动,见 UsersView)。
 */
export const KNOWN_SYSTEMS = ['bi', 'agent', 'tracking', 'auth_center', 'global'] as const

const SYSTEM_TAG_COLORS: Record<string, string> = {
  global: 'purple',
  bi: 'blue',
  agent: 'cyan',
  tracking: 'green',
  auth_center: 'red',
}

/** ant-design tag 预设色名(用于 a-tag :color)。 */
export function systemColor(code: string): string {
  return SYSTEM_TAG_COLORS[code] ?? 'default'
}

const SYSTEM_HEX: Record<string, string> = {
  global: '#7c3aed',
  bi: '#3370ff',
  agent: '#0891b2',
  tracking: '#059669',
  auth_center: '#dc2626',
}

/** 系统 HEX 色值(分组标题圆点等自绘元素)。 */
export function systemHex(code: string): string {
  return SYSTEM_HEX[code] ?? '#6b7280'
}

const PS_ICONS: Record<string, Component> = {
  admin: markRaw(CrownOutlined),
  platform_analyst: markRaw(ToolOutlined),
  self_service_analyst: markRaw(DashboardOutlined),
  viewer: markRaw(EyeOutlined),
  agent_admin: markRaw(RobotOutlined),
  agent_builder: markRaw(ToolOutlined),
  agent_viewer: markRaw(EyeOutlined),
}

/** 权限集图标(未知编码回退钥匙)。 */
export function psIcon(code: string): Component {
  return PS_ICONS[code] ?? markRaw(KeyOutlined)
}

const PS_HEX: Record<string, string> = {
  admin: '#7c3aed',
  platform_analyst: '#059669',
  self_service_analyst: '#0891b2',
  viewer: '#6b7280',
  agent_admin: '#0891b2',
  agent_builder: '#0d9488',
  agent_viewer: '#6b7280',
}

/** 权限集主题色(卡片顶线 / 图标底色)。 */
export function psHex(code: string): string {
  return PS_HEX[code] ?? '#3370ff'
}

/** 头像底色:按用户名稳定散列。 */
export function avatarColor(username: string): string {
  const COLORS = ['#4338ca', '#059669', '#d97706', '#dc2626', '#7c3aed', '#0891b2', '#db2777']
  let hash = 0
  for (let i = 0; i < username.length; i++) hash = username.charCodeAt(i) + ((hash << 5) - hash)
  return COLORS[Math.abs(hash) % COLORS.length]
}

/** 解析权限集 capabilities JSON 串(异常回退空集)。 */
export function parseCaps(raw: string | undefined): string[] {
  if (!raw) return []
  try { return JSON.parse(raw) } catch { return [] }
}

/** 能力码 → 所属分类:取首个冒号前的域前缀(dashboard:view → dashboard;agent:use:qa → agent)。 */
function capCategoryOf(code: string): string {
  const i = code.indexOf(':')
  return i > 0 ? code.slice(0, i) : code
}

/** 分类展示顺序(卡片矩阵 / 详情抽屉的行序;未知分类按字母序追加到末尾)。 */
const CATEGORY_ORDER: readonly string[] = [
  'dashboard', 'story', 'dataset', 'semantic_model', 'datasource',
  'agent', 'pulse', 'governance', 'security', 'workspace', 'flow', 'admin',
]

const CATEGORY_ICONS: Record<string, Component> = {
  dashboard: markRaw(DashboardOutlined),
  story: markRaw(ReadOutlined),
  dataset: markRaw(DatabaseOutlined),
  semantic_model: markRaw(DeploymentUnitOutlined),
  datasource: markRaw(ApiOutlined),
  agent: markRaw(RobotOutlined),
  pulse: markRaw(FundOutlined),
  governance: markRaw(AuditOutlined),
  security: markRaw(SafetyOutlined),
  workspace: markRaw(AppstoreOutlined),
  flow: markRaw(PartitionOutlined),
  admin: markRaw(SettingOutlined),
}

/** 分类图标(未知分类回退钥匙)。 */
export function categoryIcon(cat: string): Component {
  return CATEGORY_ICONS[cat] ?? markRaw(KeyOutlined)
}

/** 单个能力分类:域前缀 + 该域下已知能力码全集(卡片矩阵与详情抽屉共用)。 */
export interface CapCategoryView {
  cat: string
  caps: string[]
}

/**
 * 从「所有权限集能力的并集」按域前缀派生能力全集矩阵 —— 即系统实际在用的能力分布
 * (admin 权限集含全平台能力,故并集即完整能力宇宙),不依赖后端 category 字段(运行时注册不落该字段)。
 * 分类按 {@link CATEGORY_ORDER} 排序、域内能力码字典序;供两处以「该权限集拥有 → 高亮」渲染密度。
 */
export function buildCapUniverse(sets: Array<{ capabilities?: string }>): CapCategoryView[] {
  const byCat = new Map<string, Set<string>>()
  for (const ps of sets) {
    for (const code of parseCaps(ps.capabilities)) {
      const cat = capCategoryOf(code)
      if (!byCat.has(cat)) byCat.set(cat, new Set())
      byCat.get(cat)!.add(code)
    }
  }
  const rank = (cat: string) => {
    const i = CATEGORY_ORDER.indexOf(cat)
    return i === -1 ? CATEGORY_ORDER.length : i
  }
  return [...byCat.entries()]
    .map(([cat, codes]) => ({ cat, caps: [...codes].sort() }))
    .sort((a, b) => rank(a.cat) - rank(b.cat) || a.cat.localeCompare(b.cat))
}

/** 某权限集实际触达的分类(至少拥有 1 个能力)—— 卡片矩阵只渲染这些行,忠实反映其权限范围。 */
export function ownedCategories(universe: CapCategoryView[], owned: Set<string>): CapCategoryView[] {
  return universe.filter((c) => c.caps.some((code) => owned.has(code)))
}

/** 能力编码 → 可读动作标签:"admin:manage_user" → "manage user"。 */
export function formatCapLabel(capCode: string): string {
  const parts = capCode.split(':')
  return parts[parts.length - 1].replace(/_/g, ' ')
}

/** 时间格式化:yyyy-MM-dd HH:mm。 */
export function formatTime(s: string | null | undefined): string {
  if (!s) return '—'
  const d = new Date(s)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
