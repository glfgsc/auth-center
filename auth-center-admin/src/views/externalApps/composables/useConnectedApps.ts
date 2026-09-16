/**
 * 外部应用页的状态与写操作 —— 应用 CRUD、启停、密钥轮换 / 撤销、JWKS 读取。
 *
 * 所有失败都既提示用户又把原始错误留在控制台:只弹一句「保存失败」排查不出是网络、
 * 权限还是后端 500。
 */
import { onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  connectedAppApi,
  jwksApi,
  type ConnectedAppItem,
  type JwkKey,
  type SecretReveal,
} from '@/api'
import { useProductScope } from '@/composables/useProductScope'
import { copyText } from '@/utils/clipboard'

export function useConnectedApps() {
  const { t } = useI18n()
  const { current } = useProductScope()

  const apps = ref<ConnectedAppItem[]>([])
  const loading = ref(false)
  const togglingId = ref<number | null>(null)
  const rotatingId = ref<number | null>(null)
  const jwksKeys = ref<JwkKey[]>([])

  const revealOpen = ref(false)
  const reveal = ref<SecretReveal>({})

  async function load(): Promise<void> {
    loading.value = true
    try {
      // 按顶栏产品收窄:外部应用的 targetSystem 就是它嵌入进哪个产品。
      const res = await connectedAppApi.list(current.value || undefined)
      apps.value = res.data ?? []
    } catch (e) {
      console.error('[ExternalApps] 应用列表加载失败', e)
      message.error(t('externalApps.loadFail'))
    } finally {
      loading.value = false
    }
  }

  // 顶栏切产品即重查。
  watch(current, load)

  /**
   * 读签名公钥集。读不到时留空列表(面板自己显示空态),但必须留痕 —— 公钥集拉不下来会让
   * 所有外部应用验签失败,这是要能第一时间看见的故障,不能和「还没配公钥」混为一谈。
   */
  async function loadJwks(): Promise<void> {
    try {
      const res = await jwksApi.get()
      jwksKeys.value = res.keys ?? []
    } catch (e) {
      console.error('[ExternalApps] JWKS 公钥集加载失败,外部应用将无法验签', e)
      jwksKeys.value = []
    }
  }

  function showReveal(data: SecretReveal): void {
    reveal.value = data
    revealOpen.value = true
  }

  async function toggleStatus(app: ConnectedAppItem): Promise<void> {
    const next = app.status === 'enabled' ? 'disabled' : 'enabled'
    togglingId.value = app.id
    try {
      await connectedAppApi.update(app.id, { status: next })
      await load()
    } catch (e) {
      console.error('[ExternalApps] 应用启停失败', e)
      message.error(t('externalApps.saveFail'))
    } finally {
      togglingId.value = null
    }
  }

  async function removeApp(id: number): Promise<void> {
    try {
      await connectedAppApi.remove(id)
      message.success(t('externalApps.deleted'))
      await load()
    } catch (e) {
      console.error('[ExternalApps] 应用删除失败', e)
      message.error(t('externalApps.deleteFail'))
    }
  }

  async function generateSecret(app: ConnectedAppItem): Promise<void> {
    rotatingId.value = app.id
    try {
      const res = await connectedAppApi.generateSecret(app.id)
      showReveal({
        clientId: app.clientId,
        secretId: res.data?.secretId,
        secretValue: res.data?.secretValue,
      })
      await load()
    } catch (e) {
      console.error('[ExternalApps] 新建密钥失败', e)
      message.error(t('externalApps.saveFail'))
    } finally {
      rotatingId.value = null
    }
  }

  async function revokeSecret(appId: number, secretId: string): Promise<void> {
    try {
      await connectedAppApi.revokeSecret(appId, secretId)
      message.success(t('externalApps.secretRevoked'))
      await load()
    } catch (e) {
      console.error('[ExternalApps] 撤销密钥失败', e)
      message.error(t('externalApps.deleteFail'))
    }
  }

  onMounted(() => {
    void load()
    void loadJwks()
  })

  return {
    apps,
    loading,
    togglingId,
    rotatingId,
    jwksKeys,
    revealOpen,
    reveal,
    load,
    showReveal,
    toggleStatus,
    removeApp,
    generateSecret,
    revokeSecret,
    copyText,
  }
}
