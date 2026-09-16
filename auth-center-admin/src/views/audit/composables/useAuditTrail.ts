/**
 * 审计台的状态与取数 —— 两个板块(活动审计 / AI 信任日志)共用一套分页与加载态,
 * 各自持一份筛选条件。
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import type { Dayjs } from 'dayjs'
import { auditApi, trustApi, type AiTrustItem, type AuditLogItem } from '@/api'
import { useProductScope } from '@/composables/useProductScope'

/** 板块标识。 */
type AuditTab = 'activity' | 'trust'

/**
 * 活动审计的筛选条件。
 *
 * 来源系统不在这里 —— 它由顶栏的全局产品切换器决定，页内不再各留一个下拉,
 * 免得两处各说一套(页内选洞察、顶栏选循迹时读数根本对不上)。
 */
interface ActivityFilter {
  actor?: string
  module?: string
  operationType?: string
  status?: string
}

/**
 * AI 信任日志的筛选条件。
 *
 * {@link source} 留在页内:它是遥测上报方(RUNTIME / BI_UTILITY),不是产品 ——
 * 两者是正交的维度,产品由顶栏决定。
 */
interface TrustFilter {
  agentKey?: string
  source?: string
  blockedOnly: boolean
}

/** 后端要求的时间参数格式。 */
const TIME_FORMAT = 'YYYY-MM-DD HH:mm:ss'

export function useAuditTrail() {
  const { t } = useI18n()
  const { current } = useProductScope()

  const tab = ref<AuditTab>('activity')
  const loading = ref(false)
  const total = ref(0)
  const page = ref(1)
  const size = ref(20)

  const activityRows = ref<AuditLogItem[]>([])
  const af = reactive<ActivityFilter>({})
  const dateRange = ref<[Dayjs, Dayjs] | undefined>()

  const trustRows = ref<AiTrustItem[]>([])
  const tf = reactive<TrustFilter>({ blockedOnly: false })

  async function load() {
    loading.value = true
    try {
      if (tab.value === 'activity') {
        const res = await auditApi.list({
          source: current.value || undefined,
          actor: af.actor || undefined,
          module: af.module || undefined,
          operationType: af.operationType || undefined,
          status: af.status || undefined,
          startTime: dateRange.value?.[0]?.format(TIME_FORMAT),
          endTime: dateRange.value?.[1]?.format(TIME_FORMAT),
          page: page.value,
          size: size.value,
        })
        activityRows.value = res.data.records ?? []
        total.value = res.data.total ?? 0
      } else {
        const res = await trustApi.list({
          agentKey: tf.agentKey || undefined,
          source: tf.source || undefined,
          systemCode: current.value || undefined,
          blockedOnly: tf.blockedOnly || undefined,
          page: page.value,
          size: size.value,
        })
        trustRows.value = res.data.records ?? []
        total.value = res.data.total ?? 0
      }
    } catch (e) {
      // 提示用户之余把原始错误留在控制台 —— 否则「加载失败」四个字排查不出是网络还是后端。
      console.error('[AuditTrail] 审计数据加载失败', e)
      message.error(t('audit.loadFail'))
    } finally {
      loading.value = false
    }
  }

  /** 改了筛选条件后重查 —— 回到第一页,否则会停在越界页码上看到空表。 */
  function reload() {
    page.value = 1
    load()
  }

  function switchTab(key: AuditTab) {
    if (tab.value === key) return
    tab.value = key
    page.value = 1
    total.value = 0
    load()
  }

  // 「重置」只清页内条件,不动顶栏产品 —— 那是全局口径,不该被某一页的重置按钮改掉。
  function resetActivity() {
    af.actor = undefined
    af.module = undefined
    af.operationType = undefined
    af.status = undefined
    dateRange.value = undefined
    reload()
  }

  function resetTrust() {
    tf.agentKey = undefined
    tf.source = undefined
    tf.blockedOnly = false
    reload()
  }

  // 顶栏切产品即回到第一页重查。
  watch(current, reload)

  onMounted(load)

  return {
    tab,
    loading,
    total,
    page,
    size,
    activityRows,
    af,
    dateRange,
    trustRows,
    tf,
    load,
    reload,
    switchTab,
    resetActivity,
    resetTrust,
  }
}
