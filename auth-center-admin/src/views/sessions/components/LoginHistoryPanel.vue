<!--
  LoginHistoryPanel — 登录历史面板。
  分页展示每一次登录尝试(成功 + 失败),含 IP / 地点 / 设备 / 结果 / 异常标记,
  支持按用户名、结果、仅异常、时间范围过滤。异常标记由后端登录异常检测器落库前算出。
-->
<template>
  <div>
    <div class="hist-toolbar">
      <FilterOutlined class="hist-toolbar__lead" />
      <a-input
        v-model:value="hf.username"
        allow-clear
        :placeholder="t('sessions.searchUser')"
        style="width: 180px"
        @press-enter="reload"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-select
        v-model:value="hf.status"
        allow-clear
        :placeholder="t('sessions.filterStatus')"
        style="width: 120px"
        :options="statusOptions"
      />
      <label class="hist-anomaly">
        <a-switch v-model:checked="hf.anomalyOnly" size="small" />
        {{ t('sessions.anomalyOnly') }}
      </label>
      <a-range-picker v-model:value="dateRange" show-time />
      <div class="hist-toolbar__actions">
        <a-button type="primary" @click="reload">{{ t('sessions.search') }}</a-button>
        <a-button @click="reset">{{ t('sessions.reset') }}</a-button>
      </div>
    </div>

    <div class="sess-card">
      <a-table
        class="session-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        row-key="id"
      >
        <template #emptyText>
          <div class="sess-empty">
            <InboxOutlined class="sess-empty__icon" />
            <span>{{ t('sessions.historyEmpty') }}</span>
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'loginTime'">
            <span class="sess-time">{{ fmtStr(record.loginTime) }}</span>
          </template>
          <template v-else-if="column.key === 'user'">
            <span class="sess-user">
              <span class="sess-user__avatar">{{ initial(record.username) }}</span>
              <span class="sess-user__name">{{ record.username }}</span>
            </span>
          </template>
          <template v-else-if="column.key === 'status'">
            <span class="status" :class="record.status === 'SUCCESS' ? 'status--ok' : 'status--err'">
              <i class="status__dot"></i>
              {{ record.status === 'SUCCESS' ? t('sessions.statusSuccess') : t('sessions.statusFailed') }}
            </span>
            <span v-if="record.failReason" class="hist-reason">{{ record.failReason }}</span>
          </template>
          <template v-else-if="column.key === 'ip'">
            <span class="mono">{{ record.ip || '—' }}</span>
          </template>
          <template v-else-if="column.key === 'location'">
            <span class="sess-loc">
              <EnvironmentOutlined class="sess-loc__icon" />
              {{ locationText(record.ipClass, record.location) }}
            </span>
          </template>
          <template v-else-if="column.key === 'device'">
            <span class="sess-device">
              <component :is="deviceIcon(record.userAgent)" class="sess-device__icon" />
              {{ device(record.userAgent) }}
            </span>
          </template>
          <template v-else-if="column.key === 'anomalies'">
            <template v-if="parseAnomalies(record.anomalies).length">
              <span
                v-for="a in parseAnomalies(record.anomalies)"
                :key="a"
                class="pill pill--xs"
                :class="`pill--${anomalyTone(a)}`"
              >
                {{ anomalyLabel(a) }}
              </span>
            </template>
            <span v-else class="sess-dash">—</span>
          </template>
        </template>
      </a-table>

      <div class="sess-foot">
        <span class="sess-foot__total">{{ t('sessions.totalCount', { n: total }) }}</span>
        <a-pagination
          v-model:current="page"
          v-model:page-size="size"
          :total="total"
          :show-size-changer="true"
          :page-size-options="['20', '50', '100']"
          size="small"
          @change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import { EnvironmentOutlined, FilterOutlined, InboxOutlined, SearchOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import { loginHistoryApi, type LoginHistoryItem } from '@/api'
import { useProductScope } from '@/composables/useProductScope'
import { useLoginFormat } from '../composables/useLoginFormat'

const { t } = useI18n()
const { current } = useProductScope()
const {
  initial,
  device,
  deviceIcon,
  fmtStr,
  locationText,
  parseAnomalies,
  anomalyLabel,
  anomalyTone,
} = useLoginFormat()

const loading = ref(false)
const rows = ref<LoginHistoryItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const hf = reactive<{ username?: string; status?: string; anomalyOnly: boolean }>({
  anomalyOnly: false,
})
const dateRange = ref<[Dayjs, Dayjs] | undefined>()

const statusOptions = computed(() => [
  { value: 'SUCCESS', label: t('sessions.statusSuccess') },
  { value: 'FAILED', label: t('sessions.statusFailed') },
])

const columns = computed(() => [
  { title: t('sessions.colLoginTime'), key: 'loginTime', width: 168 },
  { title: t('sessions.colUser'), key: 'user', width: 170 },
  { title: t('sessions.colStatus'), key: 'status', width: 150 },
  { title: t('sessions.colIp'), key: 'ip', width: 140 },
  { title: t('sessions.colLocation'), key: 'location', width: 130 },
  { title: t('sessions.colDevice'), key: 'device', width: 170 },
  { title: t('sessions.colAnomalies'), key: 'anomalies', ellipsis: true },
])

async function load() {
  loading.value = true
  try {
    const res = await loginHistoryApi.list({
      username: hf.username || undefined,
      systemCode: current.value || undefined,
      status: hf.status || undefined,
      anomalyOnly: hf.anomalyOnly || undefined,
      startTime: dateRange.value?.[0]?.format('YYYY-MM-DD HH:mm:ss'),
      endTime: dateRange.value?.[1]?.format('YYYY-MM-DD HH:mm:ss'),
      page: page.value,
      size: size.value,
    })
    rows.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } catch {
    message.error(t('sessions.loadFail'))
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

watch(current, reload)

function reset() {
  hf.username = undefined
  hf.status = undefined
  hf.anomalyOnly = false
  dateRange.value = undefined
  reload()
}

onMounted(load)
</script>

<style scoped lang="scss">
.hist-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin-bottom: 14px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: var(--ds-radius);
}
.hist-toolbar__lead {
  color: var(--ds-text-faint);
  font-size: 14px;
}
.hist-toolbar__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.hist-anomaly {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--ds-text-soft);
  cursor: pointer;
  user-select: none;
}
.hist-reason {
  margin-left: 8px;
  font-size: 12px;
  color: var(--ds-text-faint);
}
</style>
