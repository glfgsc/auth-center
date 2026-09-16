/**
 * 通知渠道页的状态与写操作 —— 渠道 CRUD 与连通性测试。
 *
 * 所有失败都既提示用户又把原始错误留在控制台:只弹一句「保存失败」排查不出是网络、
 * 权限还是后端 500。
 */
import { onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  notificationCredentialApi,
  type ChannelTypeMeta,
  type NotificationCredentialItem,
} from '@/api'
import { useProductScope } from '@/composables/useProductScope'

export function useNotificationChannels() {
  const { t } = useI18n()
  const { current } = useProductScope()

  const channels = ref<NotificationCredentialItem[]>([])
  const types = ref<ChannelTypeMeta[]>([])
  const loading = ref(false)
  const testingId = ref<number | null>(null)

  const editorOpen = ref(false)
  const editorRecord = ref<NotificationCredentialItem | null>(null)
  const editorPresetType = ref<string | undefined>(undefined)

  async function load(): Promise<void> {
    loading.value = true
    try {
      // 按顶栏产品收窄;「全部产品」档传 undefined,后端不加归属条件。
      const res = await notificationCredentialApi.list(current.value || (undefined as never))
      channels.value = res.data ?? []
    } catch (e) {
      console.error('[NotifChannels] 渠道列表加载失败', e)
      message.error(t('notificationChannels.loadFail'))
    } finally {
      loading.value = false
    }
  }

  async function loadTypes(): Promise<void> {
    try {
      const res = await notificationCredentialApi.types()
      types.value = res.data ?? []
    } catch (e) {
      console.error('[NotifChannels] 渠道类型加载失败', e)
      types.value = []
    }
  }

  function openCreate(presetType?: string): void {
    editorRecord.value = null
    editorPresetType.value = presetType
    editorOpen.value = true
  }

  function openEdit(row: NotificationCredentialItem): void {
    editorRecord.value = row
    editorPresetType.value = undefined
    editorOpen.value = true
  }

  async function remove(row: NotificationCredentialItem): Promise<void> {
    try {
      await notificationCredentialApi.remove(row.id)
      message.success(t('notificationChannels.removed'))
      await load()
    } catch (e) {
      console.error('[NotifChannels] 删除失败', e)
      message.error(t('notificationChannels.removeFail'))
    }
  }

  async function test(row: NotificationCredentialItem): Promise<void> {
    testingId.value = row.id
    try {
      const res = await notificationCredentialApi.test(row.id)
      const outcome = res.data
      // 后端把「发出去了但对方拒绝」也算业务失败,故看 ok 而不是有没有抛异常。
      if (outcome?.ok) {
        message.success(outcome.message || t('notificationChannels.testOk'))
      } else {
        message.warning(outcome?.message || t('notificationChannels.testFail'))
      }
      await load()
    } catch (e) {
      console.error('[NotifChannels] 连通性测试失败', e)
      message.error(t('notificationChannels.testFail'))
    } finally {
      testingId.value = null
    }
  }

  onMounted(async () => {
    await loadTypes()
    await load()
  })

  // 顶栏切产品即重取 —— 渠道按归属产品分域。
  watch(current, load)

  return {
    channels,
    types,
    loading,
    testingId,
    editorOpen,
    editorRecord,
    editorPresetType,
    load,
    openCreate,
    openEdit,
    remove,
    test,
  }
}
