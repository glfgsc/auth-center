/**
 * 用量看板的状态与取数 —— 三块面板(产品卡片 / 时间×模型 / 明细下钻)共用同一份筛选条件，
 * 故收敛在一个 composable 里，页面只做编排。
 */
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import {
  usageApi,
  type UsageBreakdownRow,
  type UsageSeriesPoint,
  type UsageSystemRow,
} from '@/api'
import { ALL_PRODUCTS, useProductScope } from '@/composables/useProductScope'

/** 明细下钻的粒度。turn 是最细一层：不聚合、带服务商请求 ID，对账和看少报只能在这一层做。 */
export type Dimension = 'user' | 'model' | 'agent' | 'turn'

/** 可选时间范围（天）。 */
export const RANGES = [
  { days: 1, labelKey: 'usage.range1' },
  { days: 7, labelKey: 'usage.range7' },
  { days: 30, labelKey: 'usage.range30' },
]

function dateStr(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() - offsetDays)
  return d.toISOString().slice(0, 10)
}

export function useModelUsage() {
  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()

  const { current, setCurrent } = useProductScope()

  const loading = ref(false)
  const rangeDays = ref(7)
  /**
   * 产品口径直接就是顶栏那一份，页内不另存一份 —— 否则页内选洞察、顶栏选循迹时
   * 两处各说一套，读数对不上。页内点产品卡片写回的也是这同一个全局状态。
   */
  const systemCode = computed<string | undefined>({
    get: () => current.value || undefined,
    set: (code) => setCurrent(code || ALL_PRODUCTS),
  })
  const dimension = ref<Dimension>('user')

  const summary = ref<UsageSystemRow[]>([])
  const series = ref<UsageSeriesPoint[]>([])
  const seriesBucket = ref('day')
  const breakdown = ref<UsageBreakdownRow[]>([])

  /**
   * 明细区分页。只有「按用户」「逐轮」在服务端真分页，另两个维度整份回来、total 即行数，
   * 分页器届时只有一页，不必为此分叉。
   */
  const breakdownTotal = ref(0)
  const breakdownPage = ref(1)
  const breakdownSize = ref(20)

  /** 展开态自己管：切维度时清空，免得上一维度的 rowKey 残留导致下一维度莫名展开一行。 */
  const expandedKeys = ref<string[]>([])

  /**
   * 深链对账键(?turn=，来自产品侧会话回放的「token 账本」链接)。置位时明细区按 requestId
   * 精确取那一轮 —— 无视时间窗，免得缺省 7 天窗把老轮次挡在外面；卡片与趋势仍按时间范围走
   * (它们是另两块面板，与定位单轮无关)。
   */
  const deepTurnId = ref('')

  /** 产品标识 → 展示名；未知码原样显示，不猜也不隐藏。 */
  function systemLabel(code: string): string {
    const key = `usage.system.${code}`
    const label = t(key)
    return label === key ? code : label
  }

  const grandTotal = computed(() => summary.value.reduce((sum, r) => sum + r.totalTokens, 0))

  const from = computed(() => dateStr(rangeDays.value - 1))
  const to = computed(() => dateStr(0))
  const rangeText = computed(() => `${from.value} ~ ${to.value}`)

  function query() {
    return { from: from.value, to: to.value, systemCode: systemCode.value || undefined }
  }

  function rowKeyOf(row: UsageBreakdownRow): string {
    if (dimension.value === 'turn') return String(row.requestId ?? row.createdAt ?? '—')
    if (dimension.value === 'user') return String(row.userId ?? row.username ?? '—')
    if (dimension.value === 'model') return `${row.provider}|${row.model}`
    return String(row.agentKey ?? '—')
  }

  async function reload() {
    loading.value = true
    try {
      const [s, ser, bd] = await Promise.all([
        // 汇总也按产品收窄:选了产品就只看这一个，选「全部产品」时才铺开各产品卡片。
        usageApi.summary(query()),
        usageApi.series(query()),
        usageApi.breakdown({
          ...query(),
          dimension: dimension.value,
          requestId: deepTurnId.value || undefined,
          page: breakdownPage.value,
          size: breakdownSize.value,
        }),
      ])
      summary.value = s.data?.records ?? []
      series.value = ser.data?.records ?? []
      seriesBucket.value = ser.data?.bucket ?? 'day'
      breakdown.value = bd.data?.records ?? []
      breakdownTotal.value = bd.data?.total ?? 0
      // 深链定位：命中的那一轮直接展开 —— 点链接进来的人要看的就是这轮的逐次账。
      if (deepTurnId.value) {
        expandedKeys.value = breakdown.value.map(rowKeyOf)
      }
    } catch (e) {
      // 提示用户之余把原始错误留在控制台，否则「加载失败」四个字排查不出是网络还是后端。
      console.error('[ModelUsage] 用量数据加载失败', e)
      message.error(t('usage.loadFailed'))
    } finally {
      loading.value = false
    }
  }

  /**
   * 换了取数条件后重查 —— 一律回到明细第一页。
   *
   * 停在第 5 页换个维度或时间窗，新条件下往往没那么多结果，页面就空着，
   * 看着像「没有数据」。
   */
  function reloadFromFirstPage() {
    breakdownPage.value = 1
    reload()
  }

  /**
   * 翻页 / 改每页条数 —— 只影响明细区，但三块面板共用一次取数，直接整体重查。
   *
   * @param page 目标页码
   * @param size 每页条数
   */
  function pickBreakdownPage(page: number, size: number) {
    breakdownPage.value = page
    breakdownSize.value = size
    reload()
  }

  function pickRange(days: number) {
    rangeDays.value = days
    reloadFromFirstPage()
  }

  // 产品口径变了就重查 —— 顶栏切换器与页内卡片写的是同一份状态，这里统一收口。
  watch(current, reloadFromFirstPage)

  /**
   * 点卡片 = 筛这个产品；再点一次回到全部 —— 卡片本身就是最顺手的筛选入口。
   *
   * 写的是全局产品状态(顶栏切换器同一份)，重查由下面那个 watch 统一触发，
   * 这里不再自己调 reload，否则一次点击会连发两轮请求。
   */
  function toggleSystem(code: string) {
    systemCode.value = systemCode.value === code ? undefined : code
  }

  /**
   * 退出单轮定位，回到按时间窗的全量明细。
   *
   * @param resetDimension 是否同时回到逐轮列表(横幅上的「返回全部明细」用；换维度时不用)
   */
  function clearDeepLink(resetDimension = true) {
    deepTurnId.value = ''
    if (resetDimension) {
      dimension.value = 'turn'
    }
    expandedKeys.value = []
    void router.replace({ query: { ...route.query, turn: undefined } })
    reloadFromFirstPage()
  }

  function switchDimension(d: Dimension) {
    dimension.value = d
    expandedKeys.value = []
    // 换维度 = 离开单轮定位：聚合维度里对账键没有意义，留着会让明细区一直只有一行。
    if (deepTurnId.value) {
      clearDeepLink(false)
      return
    }
    reloadFromFirstPage()
  }

  function onExpand(expanded: boolean, row: UsageBreakdownRow) {
    const key = rowKeyOf(row)
    expandedKeys.value = expanded
      ? [...expandedKeys.value, key]
      : expandedKeys.value.filter((k) => k !== key)
  }

  onMounted(() => {
    // 产品侧深链(?turn=<对账键>)：直接落到逐轮维度并精确取那一轮。
    const turn = route.query.turn
    if (typeof turn === 'string' && turn.trim()) {
      deepTurnId.value = turn.trim()
      dimension.value = 'turn'
    }
    reload()
  })

  return {
    loading,
    rangeDays,
    systemCode,
    dimension,
    summary,
    series,
    seriesBucket,
    breakdown,
    breakdownTotal,
    breakdownPage,
    breakdownSize,
    expandedKeys,
    deepTurnId,

    systemLabel,
    grandTotal,
    rangeText,
    reload,
    pickRange,
    pickBreakdownPage,
    toggleSystem,
    switchDimension,
    clearDeepLink,
    rowKeyOf,
    onExpand,
  }
}
