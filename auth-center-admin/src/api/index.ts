/**
 * auth-center 管理控制台 API。
 *
 * SPA 与 auth-center 同源部署（Spring Boot static），
 * 直接调用 /api/auth/... 无 CORS 问题。
 */
import axios, { type AxiosRequestConfig } from 'axios'
import { createRequestClient } from '@loom/shared-ui/request'

/**
 * 本产品在 auth-center 的产品编码，取值须与 `auth_system.code` 一致。
 *
 * 登录时随请求上报，认证中心据此给登录历史与活跃会话标注「这次登录从哪个产品发起」——
 * 不报的话那一行不归属任何产品，按产品筛时不出现（服务端不会猜）。
 * 与顶栏产品切换器（{@link useProductScope}，选的是「看哪个产品的数据」）是两回事。
 */
export const PRODUCT_CODE = 'auth_center'

/** auth-center Result<T> 通用响应。 */
interface Result<T> {
  code: number
  data: T
  message?: string
}

type R<T> = Promise<Result<T>>

/**
 * 管理台请求客户端 —— 走共享工厂 {@link createRequestClient}(与 bi-front / agent-console 同一套 token 注入 + 401
 * 刷新队列 + 登录页防重定向环 + HTTP 错误归一)。
 *
 * 信封原样返回(不解包),调用方仍读 {@code res.data}。差别在于后端 {@code Result.fail}(HTTP 200 + code 500)会
 * reject:若把它当成成功,{@code res.data} 会悄悄变 undefined,于是「保存/删除失败」也照弹成功提示。各调用点已有
 * catch 承接。
 */
const client = createRequestClient({
  baseURL: '/api/auth',
  defaultTimeout: 30_000,
  loginPath: '/admin/login',
  // 401 跳登录页时带上回跳目标。redirect 存基座相对路径(去掉 /admin base),
  // 否则登录后 router.replace 会再加一层 base → /admin/admin/* 匹配不到路由 → 空白页。
  // 「已在登录页就不再跳」由工厂按 loginPath 判定 —— 否则当前 search 里已有的 redirect= 会被
  // 再编码一层塞进新的 redirect=,每轮 URL 多套一层直到浏览器长度上限。
  buildLoginUrl: () => {
    const path = window.location.pathname.replace(/^\/admin/, '') || '/'
    return `/admin/login?redirect=${encodeURIComponent(path + window.location.search)}`
  },
  // 管理台除 token 外还缓存了 userInfo(工厂只负责 token/refreshToken)。
  onLogout: () => localStorage.removeItem('userInfo'),
})

/**
 * 信封类型化的薄封装 —— 各 API 方法直接拿 {@code Result<T>},不必每处写请求泛型实参。
 */
const http = {
  get: <T>(url: string, config?: AxiosRequestConfig): R<T> => client.get<unknown, Result<T>>(url, config),
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): R<T> =>
    client.post<unknown, Result<T>>(url, data, config),
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): R<T> =>
    client.put<unknown, Result<T>>(url, data, config),
  delete: <T>(url: string, config?: AxiosRequestConfig): R<T> =>
    client.delete<unknown, Result<T>>(url, config),
}

// ---------------------------------------------------------------------------
// SSO Config
// ---------------------------------------------------------------------------

export type IdpLoginMode = 'mixed' | 'enforced' | 'disabled'

export interface IdentityProvider {
  id?: number
  /** 这一档配置属于哪个产品；global = 未单独配置的产品共用的兜底档。 */
  systemCode?: string
  name?: string
  icon?: string
  enabled?: boolean
  loginMode?: string
  serverUrl?: string
  configJson?: string
}

export interface IdpTestResult {
  ok: boolean
  message: string
}

/**
 * 登录设置按产品存放，一个产品一行。
 *
 * `adminGet` 取的是该产品自己那一行，没单独配置过就是 `data: null` —— 后端不回落
 * `global` 兜底档，否则管理员会把兜底档误认成本产品的配置，一保存就悄悄分叉出一份副本。
 * 登录期的解析才走「先按产品、取不到回落 global」。
 */
export const ssoApi = {
  adminGet: (system: string): R<IdentityProvider> =>
    http.get('/sso/admin/cas', { params: { system } }),
  adminSave: (system: string, body: Partial<IdentityProvider>): R<IdentityProvider> =>
    http.post('/sso/admin/cas', body, { params: { system } }),
  adminDelete: (system: string): R<string> =>
    http.delete('/sso/admin/cas', { params: { system } }),
  adminTest: (body: Partial<IdentityProvider>): R<IdpTestResult> =>
    http.post('/sso/admin/cas/test', body),
  /** 已单独配置过登录设置的产品编码 —— 用于标注哪些产品没在用兜底档。 */
  configuredSystems: (): R<string[]> => http.get('/sso/admin/cas/configured-systems'),
  callbackUrl: (): R<{ base: string; pattern: string }> =>
    http.get('/sso/admin/callback-url'),
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

/** 用户权限集绑定（含系统归属）。 */
export interface UserPermissionSetInfo {
  id: number
  code: string
  name: string
  systemCode: string
}

export interface AuthUser {
  id: number
  username: string
  nickname: string
  avatar?: string
  email?: string
  phone?: string
  createTime?: string
  permissionSets?: UserPermissionSetInfo[]
}

/** 新增 / 编辑用户提交体（编辑时 password 留空表示不改）。 */
export interface UserSaveInput {
  username: string
  password?: string
  nickname?: string
  email?: string
  phone?: string
}

/** 权限集定义。 */
export interface PermissionSetDef {
  id: number
  code: string
  systemCode: string
  name: string
  description?: string
  isSystem?: number
  capabilities?: string
  sortOrder?: number
}

/** 新增 / 编辑权限集提交体（capabilities 为 JSON 字符串数组）。 */
export interface PermissionSetSaveInput {
  id?: number
  code: string
  systemCode: string
  name: string
  description?: string
  capabilities?: string
  sortOrder?: number
}

export const userApi = {
  list: (systemCode?: string): R<AuthUser[]> =>
    http.get('/admin/users', { params: systemCode ? { systemCode } : {} }),
  getById: (id: number): R<AuthUser> => http.get(`/admin/users/${id}`),
  create: (body: UserSaveInput): R<number> => http.post('/admin/users', body),
  update: (id: number, body: UserSaveInput): R<void> => http.put(`/admin/users/${id}`, body),
  remove: (id: number): R<void> => http.delete(`/admin/users/${id}`),
  assignPermissionSet: (userId: number, permissionSetCode: string): R<void> =>
    http.put(`/admin/users/${userId}/permission-set`, { permissionSetCode }),
  removePermissionSet: (userId: number, psId: number): R<void> =>
    http.delete(`/admin/users/${userId}/permission-set/${psId}`),
  listPermissionSets: (systemCode?: string): R<PermissionSetDef[]> =>
    http.get('/admin/users/permission-sets', { params: systemCode ? { systemCode } : {} }),
}

/** 权限集管理（/admin/permission-sets，与用户端 listPermissionSets 同数据不同用途）。 */
export const permissionSetApi = {
  list: (systemCode?: string): R<PermissionSetDef[]> =>
    http.get('/admin/permission-sets', { params: systemCode ? { systemCode } : {} }),
  create: (body: PermissionSetSaveInput): R<number> => http.post('/admin/permission-sets', body),
  update: (id: number, body: PermissionSetSaveInput): R<void> =>
    http.put(`/admin/permission-sets/${id}`, body),
  remove: (id: number): R<void> => http.delete(`/admin/permission-sets/${id}`),
}

// ---------------------------------------------------------------------------
// Systems（已接入平台/系统注册表；管理台系统分组由此后端清单驱动）
// ---------------------------------------------------------------------------

/** 系统注册表项。 */
export interface AuthSystemDef {
  id?: number
  code: string
  name: string
  sortOrder?: number
}

export const systemApi = {
  list: (): R<AuthSystemDef[]> => http.get('/admin/systems'),
}

// ---------------------------------------------------------------------------
// Capabilities（能力目录；权限集编辑器的能力勾选由各子系统注册清单驱动）
// ---------------------------------------------------------------------------

/** 已注册能力码项。 */
export interface SystemCapabilityDef {
  id?: number
  systemCode: string
  capabilityCode: string
  category?: string
  label?: string
  description?: string
}

export const capabilityApi = {
  list: (systemCode?: string): R<SystemCapabilityDef[]> =>
    http.get('/capabilities', { params: systemCode ? { systemCode } : {} }),
}

// ---------------------------------------------------------------------------
// 当前登录者的权限（导航显隐用；后端另有 @PreAuthorize 把守，这里只决定看得见什么）
// ---------------------------------------------------------------------------

/** {@code GET /permission-set} 的返回：当前登录者的权限集与能力码。 */
interface MyPermissionInfo {
  /** 权限集编码，如 admin / platform_analyst */
  permissionSet?: string
  /** 能力码 JSON 数组字符串，如 {@code ["admin:manage_user"]} */
  capabilities?: string
  /** 按系统权限：system -> { ps, caps[] } */
  systemPermissions?: Record<string, { ps?: string; caps?: string[] }>
}

export const myPermissionApi = {
  /** 实时从库解析（非 token 快照），故权限刚被改过也能立刻反映到导航上。 */
  get: (): R<MyPermissionInfo> => http.get('/permission-set'),
}

// ---------------------------------------------------------------------------
// Groups（用户组 + 成员）
// ---------------------------------------------------------------------------

export interface AuthGroup {
  id: number
  /** 所属产品；global = 不属于任何单一产品的全平台组（如预置的 all_users）。 */
  systemCode?: string
  code: string
  name: string
  description?: string
  isSystem?: number
  createdAt?: string
}

/** 新增 / 编辑用户组提交体。 */
export interface GroupSaveInput {
  code: string
  name: string
  description?: string
  /** 所属产品；不给则落到 global（全平台组）。 */
  systemCode?: string
}

/** 组成员（userId + JOIN 填充的用户信息）。 */
export interface GroupMember {
  id: number
  groupId: number
  userId: number
  username?: string
  nickname?: string
  email?: string
  createdAt?: string
}

export const groupApi = {
  list: (systemCode?: string): R<AuthGroup[]> =>
    http.get('/admin/groups', { params: systemCode ? { systemCode } : {} }),
  getById: (id: number): R<AuthGroup> => http.get(`/admin/groups/${id}`),
  create: (body: GroupSaveInput): R<AuthGroup> => http.post('/admin/groups', body),
  update: (id: number, body: Partial<GroupSaveInput>): R<AuthGroup> =>
    http.put(`/admin/groups/${id}`, body),
  remove: (id: number): R<void> => http.delete(`/admin/groups/${id}`),
  listMembers: (id: number): R<GroupMember[]> => http.get(`/admin/groups/${id}/members`),
  addMembers: (id: number, userIds: number[]): R<void> =>
    http.post(`/admin/groups/${id}/members`, { userIds }),
  removeMembers: (id: number, userIds: number[]): R<void> =>
    http.delete(`/admin/groups/${id}/members`, { data: { userIds } }),
}

// ---------------------------------------------------------------------------
// User search（成员选择器用；仅回公开字段）
// ---------------------------------------------------------------------------

export interface UserSearchResult {
  id: number
  username: string
  nickname?: string
  avatar?: string
  email?: string
}

export const userSearchApi = {
  search: (q: string, limit = 10): R<UserSearchResult[]> =>
    http.get('/users/search', { params: { q, limit } }),
}

// ---------------------------------------------------------------------------
// Connected Apps（外部应用注册 + 密钥；从 BI 上移的"外部应用与密钥"中心）
// ---------------------------------------------------------------------------

interface ConnectedAppSecretItem {
  secretId: string
  createdAt?: string
}

export interface ConnectedAppItem {
  id: number
  name: string
  clientId: string
  allowedDomains?: string
  /** 目标系统 code：应用嵌入进哪个系统(bi=洞察 / agent=知数 / tracking=循迹)。 */
  targetSystem: string
  status: string
  hasPublicKey?: boolean
  assertionPublicKeyPem?: string
  secrets?: ConnectedAppSecretItem[]
  secretCount?: number
  createdAt?: string
  updatedAt?: string
}

interface ConnectedAppCreateInput {
  name: string
  allowedDomains?: string
  targetSystem?: string
  assertionPublicKeyPem?: string
}

interface ConnectedAppUpdateInput {
  name?: string
  allowedDomains?: string
  targetSystem?: string
  status?: string
  assertionPublicKeyPem?: string
}

/** 创建 / 轮换密钥后一次性返回的密钥原文。 */
export interface SecretReveal {
  clientId?: string
  secretId?: string
  secretValue?: string
}

export const connectedAppApi = {
  list: (targetSystem?: string): R<ConnectedAppItem[]> =>
    http.get('/admin/connected-apps', { params: targetSystem ? { targetSystem } : {} }),
  create: (body: ConnectedAppCreateInput): R<SecretReveal> =>
    http.post('/admin/connected-apps', body),
  update: (id: number, body: ConnectedAppUpdateInput): R<ConnectedAppItem> =>
    http.put(`/admin/connected-apps/${id}`, body),
  remove: (id: number): R<void> => http.delete(`/admin/connected-apps/${id}`),
  generateSecret: (id: number): R<SecretReveal> =>
    http.post(`/admin/connected-apps/${id}/secrets`),
  revokeSecret: (id: number, secretId: string): R<void> =>
    http.delete(`/admin/connected-apps/${id}/secrets/${secretId}`),
}

// ---------------------------------------------------------------------------
// JWKS（签名公钥集；外部应用/下游据此验签 auth-center 签发的 JWT）
// ---------------------------------------------------------------------------

export interface JwkKey {
  kty: string
  use?: string
  alg?: string
  kid?: string
  n?: string
  e?: string
}

interface JwksResponse {
  keys: JwkKey[]
}

export const jwksApi = {
  // JWKS 在 /.well-known/jwks.json（非 /api/auth）,用绝对路径 + Vite 代理到 auth-center。
  get: (): Promise<JwksResponse> =>
    axios.get<JwksResponse>('/.well-known/jwks.json').then((r) => r.data),
}

// ---------------------------------------------------------------------------
// OAuth 2.1 Authorization Server（与 CAS 登录设置、嵌入 Connected App 分离）
// ---------------------------------------------------------------------------

export interface OAuthSettings {
  issuer: string
  mcpResourceUrl: string
  scopes: string[]
  accessTokenTtlSeconds: number
  refreshTokenTtlSeconds: number
  authorizationCodeTtlSeconds: number
  resourceName: string
  resourceDocumentation?: string
}

export interface OAuthClientItem {
  id: number
  clientId: string
  clientName: string
  clientType: 'public' | 'confidential'
  redirectUris: string[]
  scopes: string[]
  resourceAudience: string
  tokenTtlSeconds: number
  status: string
}

export interface OAuthClientCreateResult {
  clientId: string
  clientSecret?: string
  client: OAuthClientItem
}

export const oauthApi = {
  settings: (): R<OAuthSettings> => http.get('/admin/oauth/settings'),
  saveSettings: (body: OAuthSettings): R<OAuthSettings> => http.put('/admin/oauth/settings', body),
  clients: (): R<OAuthClientItem[]> => http.get('/admin/oauth/clients'),
  createClient: (body: Omit<OAuthClientItem, 'id' | 'clientId' | 'status'>): R<OAuthClientCreateResult> =>
    http.post('/admin/oauth/clients', body),
  deleteClient: (id: number): R<void> => http.delete(`/admin/oauth/clients/${id}`),
}

// ---------------------------------------------------------------------------
// 统一审计中枢（活动审计 auth_audit_log + AI 信任日志 auth_ai_trust_log）
// ---------------------------------------------------------------------------

/** 分页结果。 */
interface AuditPage<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 一条活动审计记录(来源:auth_center 自身 / bi 平台)。 */
export interface AuditLogItem {
  id: number
  sourceSystem: string
  actorUserId?: number
  actorUsername?: string
  module?: string
  operation?: string
  operationType?: string
  method?: string
  path?: string
  params?: string
  result?: string
  oldValue?: string
  newValue?: string
  diffResult?: string
  targetType?: string
  targetId?: string
  targetName?: string
  tenantId?: number
  workspaceId?: number
  sensitiveOp?: number
  ip?: string
  userAgent?: string
  status?: number
  errorMsg?: string
  durationMs?: number
  createdAt?: string
}

/** 活动审计查询过滤 + 分页参数。 */
interface AuditQuery {
  source?: string
  actor?: string
  module?: string
  operationType?: string
  status?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export const auditApi = {
  list: (params: AuditQuery): R<AuditPage<AuditLogItem>> => http.get('/admin/audit', { params }),
}

/**
 * 一条 AI 信任遥测记录 —— 轮级那一层(来源:agent 平台)。
 *
 * 往下还有两层,按 `requestId` 展开:每次模型调用见 {@link AiGenerationItem}(ReAct 一轮 N 次),
 * 每条检测判定见 {@link AiContentSignalItem}。
 */
export interface AiTrustItem {
  id: number
  source?: string
  requestId?: string
  agentKey?: string
  userId?: number
  /** 用户问的那句(已掩码)。不是模型收到的提示词 —— 后者一轮 N 份,在调用级里 */
  questionText?: string
  /** 交付给用户那份(已去掩码)。模型原样返回在调用级的 rawResponse */
  responseText?: string
  blocked?: number
  blockReason?: string
  groundingSource?: string
  latencyMs?: number
  provider?: string
  model?: string
  createdAt?: string
}

/** 一次模型调用 —— 模型究竟看到了什么、原样吐了什么。 */
export interface AiGenerationItem {
  generationId: string
  requestId: string
  seq: number
  provider?: string
  model?: string
  modelRole?: string
  /** 送给模型那份;`textRetention=DIGEST` 时为空(按策略没留,不是没采到 —— 看字数列) */
  promptText?: string | null
  rawResponse?: string | null
  /** FULL 留了全文 / DIGEST 只留字数 */
  textRetention?: string
  promptChars?: number | null
  responseChars?: number | null
  inputTokens?: number | null
  outputTokens?: number | null
  reasoningTokens?: number | null
  cachedInputTokens?: number | null
  /** 服务商请求 ID —— 拿去服务商控制台按它逐条核对 */
  providerRequestId?: string | null
  streamed?: number | null
  latencyMs?: number | null
  /** length 意味着输出被截断 —— 「答得不全」最直接的解释 */
  finishReason?: string | null
  errorType?: string | null
}

/** 一条检测判定。打分型看 valueNum,判档型看 valueLabel —— 两种量纲各记各的。 */
export interface AiContentSignalItem {
  id: number
  requestId: string
  generationId?: string | null
  /** PII / TOXICITY / PROMPT_DEFENSE / SCOPE_LANDED / NUMBER_GROUNDED / VALUE_GROUNDED 等 */
  detectorType: string
  /** INPUT 用户侧 / OUTPUT 模型侧 —— 分辨「是用户问得脏还是模型答得脏」 */
  contentType?: string | null
  category?: string | null
  valueNum?: number | null
  valueLabel?: string | null
  detail?: string | null
}

/** 一轮的三层明细。 */
export interface AiTrustDetail {
  generations: AiGenerationItem[]
  signals: AiContentSignalItem[]
}

/**
 * AI 信任日志查询过滤 + 分页参数。
 *
 * `source` 是遥测上报方(RUNTIME / BI_UTILITY),`systemCode` 才是产品 —— 两者正交，别混用。
 */
interface TrustQuery {
  agentKey?: string
  source?: string
  systemCode?: string
  blockedOnly?: boolean
  page?: number
  size?: number
}

export const trustApi = {
  list: (params: TrustQuery): R<AuditPage<AiTrustItem>> =>
    http.get('/admin/audit/trust', { params }),
  /**
   * 一轮的三层明细 —— 逐次调用 + 逐条检测判定。
   *
   * 按需取而不是随列表下发:一轮十几次调用、每次可能带全文,塞进列表会让一页几十行变成几兆。
   */
  detail: (requestId: string): R<AiTrustDetail> =>
    http.get(`/admin/audit/trust/${encodeURIComponent(requestId)}/detail`),
}

// ---------------------------------------------------------------------------
// 大模型用量（与 AI 信任日志同表不同问法:那边逐条看合规,这边聚合看消耗）
// ---------------------------------------------------------------------------

/**
 * 用量口径三条约定，读数时别算错：
 * - 合计恒为输入 + 输出；
 * - reasoning 是输出的子集、cachedInput 是输入的子集，都不另加；
 * - 后端聚合出口已把 NULL 兜成 0（存储侧仍留 NULL 以区分「没采到」与「真的是 0」）。
 */
interface UsageTotals {
  turns: number
  llmCalls: number
  inputTokens: number
  outputTokens: number
  cachedInputTokens: number
  reasoningTokens?: number
  totalTokens: number
}

/** 分产品汇总行。systemCode 为 'unknown' = 上报方没带产品标识。 */
export interface UsageSystemRow extends UsageTotals {
  systemCode: string
  users: number
}

/** 时间桶 × 模型的一格。输入与缓存命中一起给，按格算命中率用（cachedInput 仍是 input 的子集）。 */
export interface UsageSeriesPoint {
  bucket: string
  model: string
  inputTokens: number
  cachedInputTokens: number
  totalTokens: number
}

/**
 * 明细下钻行 —— 各维度共用一个形状，按维度取其中的标识字段。
 *
 * `turn` 维度不聚合，多带三样对账用的东西：服务商请求 ID（JSON 数组字符串，
 * 需自行 parse）、真实出网次数、其中失败次数。`httpAttempts > llmCalls` 即这一轮少报了
 * —— 差出来的那几次服务商可能已计费而我们没记用量。
 */
export interface UsageBreakdownRow extends UsageTotals {
  userId?: number
  username?: string
  nickname?: string
  provider?: string
  model?: string
  agentKey?: string
  requestId?: string
  /** 产品侧会话 ID（运行时上报；空 = 该行早于互链上线或无会话上下文）。凭它链回产品的会话回放页 */
  sessionId?: string | null
  createdAt?: string
  systemCode?: string
  latencyMs?: number
  httpAttempts?: number | null
  failedAttempts?: number | null
}

/** 时间范围 + 产品过滤;日期为 yyyy-MM-dd,省略则后端回看 7 天。 */
interface UsageQuery {
  from?: string
  to?: string
  systemCode?: string
}

export const usageApi = {
  summary: (params: UsageQuery): R<{ records: UsageSystemRow[]; from: string; to: string }> =>
    http.get('/admin/audit/usage/summary', { params }),
  /** bucket 由后端按跨度自动选(hour / day),前端只按返回值决定横轴格式。 */
  series: (params: UsageQuery): R<{ records: UsageSeriesPoint[]; bucket: string }> =>
    http.get('/admin/audit/usage/series', { params }),
  /**
   * requestId 给了就按对账键精确取一轮（无视时间窗与分页，维度强制 turn）—— 产品侧深链入口。
   *
   * 分页只对「按用户」「逐轮」两个维度生效（会超出一屏的是它们）；
   * 「按模型」「按智能体」聚合后基数天然小，整份返回，total 即行数。
   */
  breakdown: (
    params: UsageQuery & {
      dimension: 'user' | 'model' | 'agent' | 'turn'
      requestId?: string
      page?: number
      size?: number
    },
  ): R<{
    records: UsageBreakdownRow[]
    dimension: string
    total: number
    page: number
    size: number
  }> => http.get('/admin/audit/usage/breakdown', { params }),
}

// ---------------------------------------------------------------------------
// 平台配置（按系统分区的中心配置:特性开关 + 平台级设置）
// ---------------------------------------------------------------------------

/** 一条平台配置。 */
export interface PlatformConfigItem {
  id: number
  systemCode: string
  configKey: string
  configValue?: string
  valueType: string
  category?: string
  label?: string
  description?: string
  defaultValue?: string
  sortOrder?: number
  updatedBy?: number
  updatedAt?: string
}

export const platformConfigApi = {
  list: (systemCode?: string, category?: string): R<PlatformConfigItem[]> =>
    http.get('/admin/config', { params: { systemCode, category } }),
  systems: (): R<string[]> => http.get('/admin/config/systems'),
  update: (id: number, value: string): R<PlatformConfigItem> =>
    http.put(`/admin/config/${id}`, { value }),
  reset: (id: number): R<PlatformConfigItem> => http.post(`/admin/config/${id}/reset`),
}

// ---------------------------------------------------------------------------
// 会话治理（活跃登录会话:列出 / 吊销）
// ---------------------------------------------------------------------------

/** 一条活跃会话（一次登录,以 refresh 族 ID 为主键,跨 access 续期稳定）。 */
export interface AuthSession {
  /** 会话 ID（= refresh 族 ID）。 */
  sessionId: string
  userId: number
  username: string
  /** 登录时间（epoch ms）。 */
  loginAt: number
  /** 最近活跃时间（epoch ms,每次续期刷新）。 */
  lastActiveAt: number
  /** 会话过期时间（epoch ms,= refresh 存活上限）。 */
  expiresAt: number
  /** 客户端 IP。 */
  ip?: string
  /** 客户端 User-Agent 原串（前端解析为设备/浏览器）。 */
  userAgent?: string
  /** IP 归类:INTERNAL / PUBLIC。 */
  ipClass?: string
  /** 登录地点（内网为空,公网经可插拔 GeoIP 解析）。 */
  location?: string
  /** 登录时算出的异常标记快照,逗号分隔（NEW_IP / NEW_DEVICE / CONCURRENT_LOCATION）。 */
  anomalies?: string
  /** 各业务系统最近使用时间（系统码 → epoch ms）—— 令牌正在哪些系统活跃。 */
  systemActivity?: Record<string, number>
  /**
   * 发起本次登录的产品编码。与 {@link systemActivity} 不是一回事：那个是令牌之后
   * 在哪些产品用过（一次登录可跨多个产品），这个是这次登录从哪个产品发起。
   * 产品前端目前均未上报来源产品，故恒空；按产品筛会话走的是 systemActivity。
   */
  systemCode?: string
}

export const sessionApi = {
  /**
   * 分页列出活跃会话（可按用户名与「在哪个产品活跃过」过滤）。
   *
   * 过滤、排序、分页都在服务端按索引完成，只回本页那几条 —— 别改成一次拉全量再前端切片。
   */
  list: (
    username?: string,
    systemCode?: string,
    page = 1,
    size = 20,
  ): R<{ records: AuthSession[]; total: number; page: number; size: number }> =>
    http.get('/admin/sessions', {
      params: { username, systemCode: systemCode || undefined, page, size },
    }),
  /** 吊销一条会话（拉黑当前令牌 + 作废 refresh 族）。 */
  revoke: (sessionId: string): R<void> => http.delete(`/admin/sessions/${sessionId}`),
  /** 吊销某用户的全部会话（踢下线）。 */
  revokeUser: (userId: number): R<void> => http.delete(`/admin/sessions/user/${userId}`),
}

// ---------------------------------------------------------------------------
// 登录历史（每一次登录尝试:成功 + 失败,支撑异常登录监控）
// ---------------------------------------------------------------------------

/** 一条登录历史（一次登录尝试）。 */
export interface LoginHistoryItem {
  id: number
  userId?: number
  username: string
  /** 登录时间（格式 yyyy-MM-dd HH:mm:ss）。 */
  loginTime: string
  ip?: string
  /** IP 归类:INTERNAL / PUBLIC。 */
  ipClass?: string
  /** 登录地点（内网为空,公网经可插拔 GeoIP 解析）。 */
  location?: string
  userAgent?: string
  /** 结果:SUCCESS / FAILED。 */
  status: string
  /** 失败原因（可空）。 */
  failReason?: string
  /** 成功时的会话 ID。 */
  sessionId?: string
  /** 异常标记,逗号分隔（NEW_IP / NEW_DEVICE / FAILED_BURST / CONCURRENT_LOCATION）。 */
  anomalies?: string
  /** 来源产品；空 = 登录时调用方未报来源产品。 */
  systemCode?: string
}

/** 登录历史查询过滤 + 分页参数。 */
interface LoginHistoryQuery {
  username?: string
  /** 来源产品；登录时未报来源产品的行按产品筛时不出现。 */
  systemCode?: string
  status?: string
  anomalyOnly?: boolean
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export const loginHistoryApi = {
  /** 分页查询登录历史（按登录时间倒序）。 */
  list: (params: LoginHistoryQuery): R<AuditPage<LoginHistoryItem>> =>
    http.get('/admin/login-history', { params }),
}

// ---------------------------------------------------------------------------
// 通知渠道凭据（机器人 webhook / SMTP —— 各产品共用一处备案）
// ---------------------------------------------------------------------------

/** 渠道类型的秘密字段元数据 —— 前端据此渲染控件，不硬编码任何厂商字段。 */
interface ChannelFieldMeta {
  key: string
  label: string
  /** text / password / number / boolean */
  inputType: string
  placeholder: string
  required: boolean
}

/** 渠道类型元数据。 */
export interface ChannelTypeMeta {
  name: string
  displayName: string
  /** 厂商码，与站点渠道开关里的值对应。 */
  channel: string
  fields: ChannelFieldMeta[]
}

/** 通知渠道凭据行。秘密只进不出：读回时 encryptedSecretJson 恒为掩码。 */
export interface NotificationCredentialItem {
  id: number
  targetSystem: string
  credKey: string
  credType: string
  displayName?: string
  description?: string
  encryptedSecretJson?: string
  /** 作用域引用，由目标系统自行解释（洞察写 workspace:N，知数写 product:X）。 */
  scopeRef?: string
  enabled?: number
  lastTestStatus?: string
  lastTestAt?: string
  lastTestMessage?: string
  lastRotatedAt?: string
  updatedAt?: string
}

interface NotificationCredentialInput {
  targetSystem: string
  credKey: string
  credType: string
  displayName?: string
  description?: string
  /** 秘密 JSON 明文；留空或为掩码时后端保持原值。 */
  encryptedSecretJson?: string
  scopeRef?: string
  enabled?: boolean
}

/** 连通性测试结果。 */
interface ChannelTestOutcome {
  ok: boolean
  message: string
  durationMs: number
}

export const notificationCredentialApi = {
  list: (targetSystem: string, credType?: string): R<NotificationCredentialItem[]> =>
    http.get('/admin/notification-credentials', {
      params: credType ? { targetSystem, credType } : { targetSystem },
    }),
  get: (id: number): R<NotificationCredentialItem> =>
    http.get(`/admin/notification-credentials/${id}`),
  create: (body: NotificationCredentialInput): R<number> =>
    http.post('/admin/notification-credentials', body),
  update: (id: number, body: NotificationCredentialInput): R<number> =>
    http.put(`/admin/notification-credentials/${id}`, body),
  remove: (id: number): R<void> => http.delete(`/admin/notification-credentials/${id}`),
  /** 向上游真发一条探测消息，结果落库到 lastTest* 三列。 */
  test: (id: number): R<ChannelTestOutcome> =>
    http.post(`/admin/notification-credentials/${id}/test`),
  types: (): R<ChannelTypeMeta[]> => http.get('/admin/notification-credentials/types'),
}
