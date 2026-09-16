<!--
  PlatformSettingsView — 平台配置(按系统分区的中心配置)。
  管理员在此统一管理各系统(全局/洞察/知数/循迹/中心)的特性开关与平台级设置。
  BI 的运行时调优配置(query 限额等)仍在 BI 本地,不在此。
-->
<template>
  <div class="ac-page">
    <PageHeader :title="t('config.title')" :description="t('config.subtitle')">
      <template #actions>
        <a-button :loading="loading" @click="load">
          <template #icon><ReloadOutlined /></template>
          {{ t('common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <a-spin :spinning="loading">
      <div v-if="!loading && rows.length === 0" class="cfg-empty">
        <InboxOutlined class="cfg-empty__icon" />
        <span>{{ t('config.empty') }}</span>
      </div>

      <div class="cfg-groups">
        <SectionCard
          v-for="grp in grouped"
          :key="grp.system"
          :title="systemLabel(grp.system)"
          :icon="systemIcon(grp.system)"
        >
          <template #extra>
            <span class="cfg-count">{{ t('config.itemCount', { n: grp.items.length }) }}</span>
          </template>

          <div class="cfg-list">
            <div v-for="item in grp.items" :key="item.id" class="cfg-item">
              <div class="cfg-item__info">
                <div class="cfg-item__label">
                  {{ item.label || item.configKey }}
                  <code class="cfg-item__key">{{ item.configKey }}</code>
                </div>
                <div v-if="item.description" class="cfg-item__desc">{{ item.description }}</div>
              </div>

              <div class="cfg-item__control">
                <!-- BOOLEAN → 开关(即时保存) -->
                <a-switch
                  v-if="item.valueType === 'BOOLEAN'"
                  :checked="local[item.id] === 'true'"
                  :loading="savingId === item.id"
                  @change="(v: boolean) => saveValue(item, v ? 'true' : 'false')"
                />

                <!-- INTEGER / DOUBLE → 数字 + 保存 -->
                <template v-else-if="item.valueType === 'INTEGER' || item.valueType === 'DOUBLE'">
                  <a-input-number
                    v-model:value="localNum[item.id]"
                    :step="item.valueType === 'DOUBLE' ? 0.1 : 1"
                    style="width: 150px"
                  />
                  <a-button
                    v-if="String(localNum[item.id] ?? '') !== (item.configValue ?? '')"
                    type="primary"
                    size="small"
                    :loading="savingId === item.id"
                    @click="saveValue(item, String(localNum[item.id] ?? ''))"
                  >
                    {{ t('config.save') }}
                  </a-button>
                </template>

                <!-- STRING / JSON → 文本 + 保存 -->
                <template v-else>
                  <a-input
                    v-model:value="local[item.id]"
                    :placeholder="item.defaultValue"
                    style="width: 240px"
                  />
                  <a-button
                    v-if="local[item.id] !== (item.configValue ?? '')"
                    type="primary"
                    size="small"
                    :loading="savingId === item.id"
                    @click="saveValue(item, local[item.id] ?? '')"
                  >
                    {{ t('config.save') }}
                  </a-button>
                </template>

                <a-tooltip :title="t('config.resetTip', { d: item.defaultValue })">
                  <a-button
                    class="cfg-item__reset"
                    type="text"
                    size="small"
                    :disabled="item.configValue === item.defaultValue"
                    @click="reset(item)"
                  >
                    <template #icon><UndoOutlined /></template>
                  </a-button>
                </a-tooltip>
              </div>
            </div>
          </div>
        </SectionCard>
      </div>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, markRaw, onMounted, reactive, ref, watch, type Component } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  AimOutlined,
  GlobalOutlined,
  InboxOutlined,
  ReloadOutlined,
  RobotOutlined,
  SafetyCertificateOutlined,
  ThunderboltOutlined,
  UndoOutlined,
} from '@ant-design/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import SectionCard from '@/components/common/SectionCard.vue'
import { platformConfigApi, type PlatformConfigItem } from '@/api'
import { useProductScope } from '@/composables/useProductScope'

const { t } = useI18n()
const { current } = useProductScope()

// 顶栏切产品即重拉配置 —— 平台配置本就按系统分区，口径统一由顶栏给。
watch(current, () => void load())

const SYSTEM_ORDER = ['global', 'bi', 'agent', 'tracking', 'auth_center']

/** 各系统图标(统一走 SectionCard 品牌软色小方块,克制单色,仅以图形区分)。 */
const SYSTEM_ICONS: Record<string, Component> = {
  global: markRaw(GlobalOutlined),
  bi: markRaw(ThunderboltOutlined),
  agent: markRaw(RobotOutlined),
  tracking: markRaw(AimOutlined),
  auth_center: markRaw(SafetyCertificateOutlined),
}

const loading = ref(false)
const savingId = ref<number | null>(null)
const rows = ref<PlatformConfigItem[]>([])
/** 文本类本地编辑副本(id → 值)。 */
const local = reactive<Record<number, string>>({})
/** 数字类本地编辑副本。 */
const localNum = reactive<Record<number, number | null>>({})

const grouped = computed(() => {
  const map = new Map<string, PlatformConfigItem[]>()
  for (const r of rows.value) {
    if (!map.has(r.systemCode)) map.set(r.systemCode, [])
    map.get(r.systemCode)!.push(r)
  }
  return [...map.entries()]
    .sort((a, b) => order(a[0]) - order(b[0]))
    .map(([system, items]) => ({ system, items }))
})

function order(s: string): number {
  const i = SYSTEM_ORDER.indexOf(s)
  return i < 0 ? 99 : i
}
function systemLabel(s: string): string {
  return t(`config.system.${s}`, s)
}
function systemIcon(s: string): Component {
  return SYSTEM_ICONS[s] ?? SYSTEM_ICONS.global
}

function syncLocal() {
  for (const r of rows.value) {
    local[r.id] = r.configValue ?? ''
    localNum[r.id] = r.configValue != null && r.configValue !== '' ? Number(r.configValue) : null
  }
}

async function load() {
  loading.value = true
  try {
    const res = await platformConfigApi.list(current.value || undefined)
    rows.value = res.data ?? []
    syncLocal()
  } catch {
    message.error(t('config.loadFail'))
  } finally {
    loading.value = false
  }
}

async function saveValue(item: PlatformConfigItem, value: string) {
  savingId.value = item.id
  try {
    const res = await platformConfigApi.update(item.id, value)
    Object.assign(item, res.data)
    local[item.id] = res.data.configValue ?? ''
    localNum[item.id] =
      res.data.configValue != null && res.data.configValue !== ''
        ? Number(res.data.configValue)
        : null
    message.success(t('config.saved'))
  } catch {
    message.error(t('config.saveFail'))
    syncLocal()
  } finally {
    savingId.value = null
  }
}

async function reset(item: PlatformConfigItem) {
  savingId.value = item.id
  try {
    const res = await platformConfigApi.reset(item.id)
    Object.assign(item, res.data)
    local[item.id] = res.data.configValue ?? ''
    localNum[item.id] =
      res.data.configValue != null && res.data.configValue !== ''
        ? Number(res.data.configValue)
        : null
    message.success(t('config.reset'))
  } catch {
    message.error(t('config.saveFail'))
  } finally {
    savingId.value = null
  }
}

onMounted(load)
</script>

<style scoped lang="scss">
.cfg-groups {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.cfg-count {
  font-size: 12px;
  font-weight: 500;
  line-height: 20px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--ds-neutral-soft);
  color: var(--ds-text-muted);
}

/* ── 配置项分隔列表(SectionCard body 内) ── */
.cfg-list {
  display: flex;
  flex-direction: column;
}
.cfg-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 15px 0;
  border-top: 1px solid var(--ds-divider);

  &:first-child { border-top: none; padding-top: 4px; }
  &:last-child { padding-bottom: 2px; }
}

.cfg-item__info {
  min-width: 0;
  flex: 1;
}
.cfg-item__label {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--ds-text);
}
.cfg-item__key {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 11px;
  font-weight: 400;
  padding: 1px 7px;
  border-radius: 5px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  color: var(--ds-text-faint);
}
.cfg-item__desc {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--ds-text-muted);
  max-width: 560px;
}

.cfg-item__control {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.cfg-item__reset {
  color: var(--ds-text-faint);
  &:not(:disabled):hover { color: var(--ds-primary); }
}

/* ── 空态 ── */
.cfg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--ds-text-faint);
  font-size: 13px;
}
.cfg-empty__icon { font-size: 34px; opacity: 0.5; }
</style>
