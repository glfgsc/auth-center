/**
 * useSsoConfig — 登录设置(CAS)页的状态与动作。
 *
 * 表单状态经 provide/inject(SSO_FORM_KEY)下发给各分区卡片,
 * 视图层只负责布局;加载/保存/测试/删除与脏检查全部收敛在此。
 *
 * 配置按产品存放:读写的都是顶栏当前产品自己那一行,该产品没单独配置过就是空态。
 * 顶栏切产品会重新加载 —— 否则会拿着 A 产品的表单去覆盖 B 产品那一行。
 */
import { computed, reactive, ref, watch, type InjectionKey } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'

import { ssoApi, type IdentityProvider, type IdpLoginMode, type IdpTestResult } from '@/api'
import { useSpinning } from '@loom/shared-ui/composables'
import { useProductScope } from '@/composables/useProductScope'

/** SSO 配置表单状态(reactive 对象经 provide 下发)。 */
interface SsoForm {
  exists: boolean
  id: number | undefined
  name: string
  icon: string
  enabled: boolean
  loginMode: IdpLoginMode
  serverUrl: string
  protocolVersion: string
  userAttribute: string
  emailAttribute: string
  nameAttribute: string
  autoCreate: boolean
  singleLogout: boolean
}

export const SSO_FORM_KEY: InjectionKey<SsoForm> = Symbol('sso-form')

interface CasConfigJson {
  serverUrl?: unknown
  protocolVersion?: unknown
  userAttribute?: unknown
  emailAttribute?: unknown
  nameAttribute?: unknown
  autoCreate?: unknown
  singleLogout?: unknown
}

const DEFAULT_ICON = '\u{1F3E2}'

export function useSsoConfig() {
  const { t } = useI18n()
  const { current, currentProduct } = useProductScope()

  /**
   * 本页读写哪一档配置。
   *
   * 顶栏选「全部产品」时落到 global 兜底档 —— 那正是「对所有未单独配置的产品都生效」的那一档,
   * 而不是某种跨产品聚合(配置无法聚合编辑)。
   */
  const targetSystem = computed(() => current.value || 'global')

  /** 当前编辑的是不是兜底档 —— 决定文案说「本产品」还是「所有未单独配置的产品」。 */
  const isGlobalFallback = computed(() => targetSystem.value === 'global')

  /** 当前产品显示名,给文案用;兜底档没有对应产品项。 */
  const productLabel = computed(
    () => currentProduct.value?.name || currentProduct.value?.code || t('product.all'),
  )

  const loading = ref(false)
  const spinning = useSpinning(loading)
  const saving = ref(false)
  const testing = ref(false)
  const testResult = ref<IdpTestResult | null>(null)
  const callbackPattern = ref('')

  const form = reactive<SsoForm>({
    exists: false,
    id: undefined,
    name: '',
    icon: DEFAULT_ICON,
    enabled: true,
    loginMode: 'mixed',
    serverUrl: '',
    protocolVersion: '3.0',
    userAttribute: 'user',
    emailAttribute: 'email',
    nameAttribute: 'displayName',
    autoCreate: true,
    singleLogout: false,
  })

  // ── 脏检查:以最近一次 hydrate/save 的快照为基线 ──
  const baseline = ref('')

  function snapshot(): string {
    return JSON.stringify({
      name: form.name, icon: form.icon, loginMode: form.loginMode,
      serverUrl: form.serverUrl, protocolVersion: form.protocolVersion,
      userAttribute: form.userAttribute, emailAttribute: form.emailAttribute,
      nameAttribute: form.nameAttribute, autoCreate: form.autoCreate,
      singleLogout: form.singleLogout,
    })
  }

  const dirty = computed(() => form.exists && snapshot() !== baseline.value)

  function hydrate(row: IdentityProvider): void {
    form.exists = true
    form.id = row.id
    form.name = row.name || 'CAS'
    form.icon = row.icon || DEFAULT_ICON
    form.enabled = row.enabled !== false
    if (!row.enabled) {
      form.loginMode = 'disabled'
    } else {
      form.loginMode = (row.loginMode as IdpLoginMode) || 'mixed'
    }
    let cfg: CasConfigJson = {}
    try { cfg = row.configJson ? (JSON.parse(row.configJson) as CasConfigJson) : {} }
    catch { cfg = {} }
    form.serverUrl = String(cfg.serverUrl || '')
    form.protocolVersion = String(cfg.protocolVersion || '3.0')
    form.userAttribute = String(cfg.userAttribute || 'user')
    form.emailAttribute = String(cfg.emailAttribute || 'email')
    form.nameAttribute = String(cfg.nameAttribute || 'displayName')
    form.autoCreate = cfg.autoCreate !== false
    form.singleLogout = cfg.singleLogout === true
    baseline.value = snapshot()
  }

  function enableCas(): void {
    form.exists = true
    form.name = 'CAS'
    form.loginMode = 'mixed'
    baseline.value = ''
  }

  function packBody(): Partial<IdentityProvider> {
    const cfg = {
      serverUrl: form.serverUrl.trim().replace(/\/+$/, ''),
      protocolVersion: form.protocolVersion,
      userAttribute: form.userAttribute || 'user',
      emailAttribute: form.emailAttribute || 'email',
      nameAttribute: form.nameAttribute || 'displayName',
      autoCreate: form.autoCreate,
      singleLogout: form.singleLogout,
    }
    return {
      id: form.id,
      name: form.name.trim() || 'CAS',
      icon: form.icon || DEFAULT_ICON,
      enabled: form.loginMode !== 'disabled',
      loginMode: form.loginMode === 'disabled' ? 'mixed' : form.loginMode,
      configJson: JSON.stringify(cfg),
    }
  }

  /** 切产品时把表单彻底复位 —— 残留上一个产品的值会被误当成本产品的配置保存下去。 */
  function reset(): void {
    form.exists = false
    form.id = undefined
    form.name = ''
    form.icon = DEFAULT_ICON
    form.enabled = true
    form.loginMode = 'mixed'
    form.serverUrl = ''
    form.protocolVersion = '3.0'
    form.userAttribute = 'user'
    form.emailAttribute = 'email'
    form.nameAttribute = 'displayName'
    form.autoCreate = true
    form.singleLogout = false
    testResult.value = null
    baseline.value = ''
  }

  async function load(): Promise<void> {
    loading.value = true
    try {
      const [cfgRes, cbRes] = await Promise.all([
        ssoApi.adminGet(targetSystem.value),
        ssoApi.callbackUrl(),
      ])
      const row = cfgRes.data
      callbackPattern.value = cbRes.data?.pattern || ''
      // data 为 null = 该产品未单独配置(后端不回落兜底档)→ 走空态,让人明确知道在配哪一档。
      if (row) hydrate(row)
      else reset()
    } catch {
      // 加载失败也必须复位:切产品时若留着上一档的值,而 targetSystem 已指向新产品,
      // 那些残留会被误当成本产品的配置保存下去。
      reset()
      message.error(t('sso.loadFail'))
    } finally {
      loading.value = false
    }
  }

  // 顶栏切产品即重载本页;ALL_PRODUCTS 与 global 映射到同一档,不重复拉。
  watch(targetSystem, (next, prev) => {
    if (next !== prev) load()
  })

  async function save(): Promise<void> {
    if (!form.serverUrl.trim()) {
      message.warning(t('sso.serverRequired'))
      return
    }
    saving.value = true
    try {
      const res = await ssoApi.adminSave(targetSystem.value, packBody())
      hydrate(res.data)
      message.success(t('sso.saved'))
    } catch {
      message.error(t('sso.saveFail'))
    } finally {
      saving.value = false
    }
  }

  async function testConnection(): Promise<void> {
    if (!form.serverUrl.trim()) {
      message.warning(t('sso.serverRequired'))
      return
    }
    testing.value = true
    testResult.value = null
    try {
      const res = await ssoApi.adminTest(packBody())
      testResult.value = res.data
    } catch (e) {
      testResult.value = { ok: false, message: e instanceof Error ? e.message : t('sso.testFail') }
    } finally {
      testing.value = false
    }
  }

  async function removeCas(): Promise<void> {
    try {
      await ssoApi.adminDelete(targetSystem.value)
      message.success(t('sso.deleted'))
      reset()
    } catch {
      message.error(t('sso.deleteFail'))
    }
  }

  return {
    form, loading, spinning, saving, testing, testResult, callbackPattern, dirty,
    targetSystem, isGlobalFallback, productLabel,
    load, save, testConnection, removeCas, enableCas,
  }
}
