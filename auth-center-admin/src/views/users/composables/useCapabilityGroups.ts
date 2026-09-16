/**
 * 权限集编辑器的能力全集派生与勾选 —— 把「有哪些能力可选、怎么分组、勾了哪些」这套逻辑
 * 从抽屉组件里独立出来。
 *
 * 能力全集 = 后端注册表 ∪ 本系统其它权限集在用 ∪ 当前已选。并上后两者是自愈:后端注册表
 * 不全时,历史权限集里已经在用的能力仍然可选可见,不会因为漏注册而在编辑时被静默丢掉。
 */
import { computed, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import type { PermissionSetDef, SystemCapabilityDef } from '@/api'
import { parseCaps } from '../permsetMeta'

/** 分组(资源)展示顺序;不在表内的资源按字母序排后。 */
const RES_ORDER = [
  'dashboard',
  'dataset',
  'semantic_model',
  'story',
  'datasource',
  'workspace',
  'pulse',
  'agent',
  'admin',
]

/** 未登记在 {@link RES_ORDER} 里的资源的排序权重 —— 排在已登记项之后。 */
const RES_ORDER_TAIL = 99

/** 一条能力。 */
interface CapItem {
  code: string
  label: string
}

/** 按资源分组后的一组能力。 */
export interface CapGroup {
  cat: string
  label: string
  caps: CapItem[]
}

/**
 * @param catalog 后端能力注册表(随所选系统加载)——决定「有哪些条目可勾」
 * @param permissionSets 全部权限集(用于自愈补齐本系统在用的能力)
 * @param systemCode 当前所选系统
 * @param selected 当前已勾选的能力码(双向)
 * @param labelCatalog 全系统能力注册表——只用来取 label,缺省时退回 catalog
 */
export function useCapabilityGroups(
  catalog: Ref<SystemCapabilityDef[]>,
  permissionSets: Ref<PermissionSetDef[]>,
  systemCode: Ref<string>,
  selected: Ref<string[]>,
  labelCatalog?: Ref<SystemCapabilityDef[]>,
) {
  const { t } = useI18n()

  /**
   * 后端能力目录 code -> label(有则条目优先用此 label)。
   *
   * 取自全系统目录:能力码的显示名是全局事实,不随正在编辑哪个权限集而变。
   * 跨系统超管权限集的 systemCode 是 'global',它不是真实注册系统
   * (`?systemCode=global` 恒返回空目录),label 若跟着按系统取,它持有的能力码
   * 就会全部拿不到 label、整片退到 i18n 兜底。
   */
  const registryLabels = computed<Record<string, string>>(() => {
    const m: Record<string, string> = {}
    const source = labelCatalog?.value?.length ? labelCatalog.value : catalog.value
    for (const c of source) if (c.label) m[c.capabilityCode] = c.label
    return m
  })

  /** 该系统所有权限集实际用到的能力码(自愈:后端注册表不全时补齐候选)。 */
  const knownCapsForSystem = computed<string[]>(() => {
    const s = new Set<string>()
    for (const ps of permissionSets.value) {
      if (ps.systemCode === systemCode.value) parseCaps(ps.capabilities).forEach((c) => s.add(c))
    }
    return [...s]
  })

  function humanize(s: string): string {
    return s.replace(/[_:]/g, ' ').trim()
  }

  function resLabel(res: string): string {
    const key = `users.capRes.${res}`
    const v = t(key)
    return v === key ? humanize(res) : v
  }

  function actLabel(action: string): string {
    const key = `users.capAct.${action}`
    const v = t(key)
    return v === key ? humanize(action) : v
  }

  /** 能力码的资源段(冒号前),无冒号则整串即资源。 */
  function capResource(code: string): string {
    const i = code.indexOf(':')
    return i >= 0 ? code.slice(0, i) : code
  }

  function capLabel(code: string): string {
    const reg = registryLabels.value[code]
    if (reg) return reg
    const i = code.indexOf(':')
    return actLabel(i >= 0 ? code.slice(i + 1) : code)
  }

  /** 能力全集,按资源分组、资源顺序排列。 */
  const groups = computed<CapGroup[]>(() => {
    const universe = new Set<string>([
      ...catalog.value.map((c) => c.capabilityCode),
      ...knownCapsForSystem.value,
      ...selected.value,
    ])
    const byRes = new Map<string, CapItem[]>()
    for (const code of universe) {
      const res = capResource(code)
      if (!byRes.has(res)) byRes.set(res, [])
      byRes.get(res)!.push({ code, label: capLabel(code) })
    }
    return [...byRes.entries()]
      .sort((a, b) => {
        const ia = RES_ORDER.indexOf(a[0])
        const ib = RES_ORDER.indexOf(b[0])
        if (ia !== -1 || ib !== -1) {
          return (ia === -1 ? RES_ORDER_TAIL : ia) - (ib === -1 ? RES_ORDER_TAIL : ib)
        }
        return a[0].localeCompare(b[0])
      })
      .map(([res, caps]) => ({
        cat: res,
        label: resLabel(res),
        caps: caps.sort((x, y) => x.code.localeCompare(y.code)),
      }))
  })

  const allCodes = computed(() => groups.value.flatMap((g) => g.caps.map((c) => c.code)))

  function has(code: string): boolean {
    return selected.value.includes(code)
  }

  function toggle(code: string): void {
    selected.value = has(code)
      ? selected.value.filter((c) => c !== code)
      : [...selected.value, code]
  }

  function groupSelectedCount(g: CapGroup): number {
    return g.caps.filter((c) => has(c.code)).length
  }

  function groupAllOn(g: CapGroup): boolean {
    return g.caps.length > 0 && g.caps.every((c) => has(c.code))
  }

  function groupSomeOn(g: CapGroup): boolean {
    const n = groupSelectedCount(g)
    return n > 0 && n < g.caps.length
  }

  function toggleGroup(g: CapGroup): void {
    const codes = g.caps.map((c) => c.code)
    selected.value = groupAllOn(g)
      ? selected.value.filter((c) => !codes.includes(c))
      : [...new Set([...selected.value, ...codes])]
  }

  function selectAll(): void {
    selected.value = [...new Set([...selected.value, ...allCodes.value])]
  }

  function clearAll(): void {
    selected.value = []
  }

  return {
    groups,
    allCodes,
    has,
    toggle,
    groupSelectedCount,
    groupAllOn,
    groupSomeOn,
    toggleGroup,
    selectAll,
    clearAll,
  }
}
