<!--
  ActiveSessionsPanel — 活跃会话面板。
  列出所有活跃登录会话(一次登录 = 一条,以 refresh 族 ID 为主键,跨 access 续期稳定),
  可逐条吊销或按用户批量踢下线。吊销 = 拉黑当前 access 令牌(网关即刻拒)+ 作废 refresh 族。
  只追踪部署后的新登录。风险徽标来自登录时算出的异常标记快照。
-->
<template>
  <div>
    <div class="sess-toolbar">
      <a-input
        v-model:value="keyword"
        allow-clear
        :placeholder="t('sessions.searchUser')"
        style="width: 240px"
        @press-enter="reload"
      >
        <template #prefix><SearchOutlined /></template>
      </a-input>
      <a-button type="primary" @click="reload">{{ t('sessions.search') }}</a-button>
      <a-button :loading="loading" @click="load">
        <template #icon><ReloadOutlined /></template>
        {{ t('common.refresh') }}
      </a-button>
      <span class="sess-toolbar__stat">
        <span class="sess-toolbar__dot" />
        <!-- 计数取服务端总数,不是 rows.length —— 分页后本页只有一屏,拿它当总数会少报。 -->
        {{ t('sessions.activeCount', { n: total }) }}
      </span>
    </div>

    <div class="sess-card">
      <a-table
        class="session-table"
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        :pagination="false"
        :scroll="{ x: 1410 }"
        row-key="sessionId"
      >
        <template #emptyText>
          <div class="sess-empty">
            <DesktopOutlined class="sess-empty__icon" />
            <span>{{ t('sessions.empty') }}</span>
          </div>
        </template>
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'user'">
            <span class="sess-user">
              <span class="sess-user__avatar">{{ initial(record.username) }}</span>
              <span class="sess-user__name">{{ record.username }}</span>
              <span v-if="record.sessionId === selfSid" class="pill pill--primary pill--xs">
                {{ t('sessions.current') }}
              </span>
              <span
                v-for="a in parseAnomalies(record.anomalies)"
                :key="a"
                class="pill pill--xs"
                :class="`pill--${anomalyTone(a)}`"
              >
                {{ anomalyLabel(a) }}
              </span>
            </span>
          </template>
          <template v-else-if="column.key === 'systems'">
            <div class="sess-systems">
              <template v-if="systemActivityList(record.systemActivity).length">
                <span
                  v-for="s in systemActivityList(record.systemActivity)"
                  :key="s.code"
                  class="pill pill--xs"
                  :class="`pill--${s.tone}`"
                  :title="t('sessions.lastUsed', { t: relative(s.lastSeen) })"
                >
                  {{ s.label }}
                  <i class="sess-systems__time">{{ relative(s.lastSeen) }}</i>
                </span>
              </template>
              <span v-else class="sess-dash">{{ t('sessions.noSystemActivity') }}</span>
            </div>
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
          <template v-else-if="column.key === 'loginAt'">
            <span class="sess-time">{{ fmt(record.loginAt) }}</span>
          </template>
          <template v-else-if="column.key === 'lastActiveAt'">
            <span class="sess-time">{{ relative(record.lastActiveAt) }}</span>
          </template>
          <template v-else-if="column.key === 'expiresAt'">
            <span class="sess-time sess-time--faint">{{ fmt(record.expiresAt) }}</span>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-dropdown :trigger="['click']" placement="bottomRight">
              <a-button type="text" size="small" :loading="revokingId === record.sessionId">
                <template #icon><EllipsisOutlined /></template>
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="revoke" danger @click="confirmRevoke(record)">
                    <LogoutOutlined /> {{ t('sessions.revoke') }}
                  </a-menu-item>
                  <a-menu-item key="revokeUser" danger @click="confirmRevokeUser(record)">
                    <UsergroupDeleteOutlined /> {{ t('sessions.kickUser') }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
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
import { computed, onMounted, ref, watch } from 'vue'
import { Modal, message } from 'ant-design-vue'
import { useI18n } from 'vue-i18n'
import {
  DesktopOutlined,
  EllipsisOutlined,
  EnvironmentOutlined,
  LogoutOutlined,
  ReloadOutlined,
  SearchOutlined,
  UsergroupDeleteOutlined,
} from '@ant-design/icons-vue'
import { sessionApi, type AuthSession } from '@/api'
import { useProductScope } from '@/composables/useProductScope'
import { clearAdminSession } from '@/utils/session'
import { useLoginFormat } from '../composables/useLoginFormat'

const { t } = useI18n()
const { current } = useProductScope()
const {
  initial,
  device,
  deviceIcon,
  fmt,
  relative,
  locationText,
  parseAnomalies,
  anomalyLabel,
  anomalyTone,
  systemActivityList,
} = useLoginFormat()

const loading = ref(false)
const revokingId = ref<string | null>(null)
const keyword = ref('')
const rows = ref<AuthSession[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

/** 当前浏览器自身会话的 sid(从本地 access token 解码),用于标记「当前会话」。 */
const selfSid = computed(() => {
  try {
    const token = localStorage.getItem('token')
    if (!token) return null
    const payload = JSON.parse(atob(token.split('.')[1] ?? ''))
    return (payload.sid as string) || null
  } catch {
    return null
  }
})

const columns = computed(() => [
  { title: t('sessions.colUser'), key: 'user', width: 240 },
  { title: t('sessions.colActiveSystems'), key: 'systems', width: 240 },
  { title: t('sessions.colIp'), key: 'ip', width: 130 },
  { title: t('sessions.colLocation'), key: 'location', width: 120 },
  { title: t('sessions.colDevice'), key: 'device', width: 170 },
  { title: t('sessions.colLoginAt'), key: 'loginAt', width: 168 },
  { title: t('sessions.colLastActive'), key: 'lastActiveAt', width: 110 },
  { title: t('sessions.colExpiresAt'), key: 'expiresAt', width: 168 },
  { title: t('sessions.colActions'), key: 'actions', width: 64, fixed: 'right' },
])

async function load() {
  loading.value = true
  try {
    // 按顶栏产品收窄:筛的是「令牌在哪个产品活跃过」,与本表「活跃系统」列同源(systemActivity)。
    // 另有一个「这次登录从哪个产品发起」维度(systemCode),产品前端未上报故恒空,不作筛选判据。
    const res = await sessionApi.list(
      keyword.value.trim() || undefined,
      current.value || undefined,
      page.value,
      size.value,
    )
    rows.value = res.data.records ?? []
    total.value = res.data.total ?? 0
  } catch {
    message.error(t('sessions.loadFail'))
  } finally {
    loading.value = false
  }
}

/** 换了过滤条件就回到第一页 —— 停在第 3 页会因新条件下没那么多结果而显示空表。 */
function reload() {
  page.value = 1
  load()
}

watch(current, reload)

function confirmRevoke(record: AuthSession) {
  const isSelf = record.sessionId === selfSid.value
  Modal.confirm({
    title: t('sessions.revokeTitle'),
    content: isSelf
      ? t('sessions.revokeSelfConfirm', { user: record.username })
      : t('sessions.revokeConfirm', { user: record.username }),
    okType: 'danger',
    okText: t('sessions.revoke'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      revokingId.value = record.sessionId
      try {
        await sessionApi.revoke(record.sessionId)
        message.success(t('sessions.revoked'))
        if (isSelf) {
          logoutSelf()
          return
        }
        await load()
      } catch {
        message.error(t('sessions.revokeFail'))
      } finally {
        revokingId.value = null
      }
    },
  })
}

function confirmRevokeUser(record: AuthSession) {
  const isSelf = record.sessionId === selfSid.value
  Modal.confirm({
    title: t('sessions.kickUserTitle'),
    content: t('sessions.kickUserConfirm', { user: record.username }),
    okType: 'danger',
    okText: t('sessions.kickUser'),
    cancelText: t('common.cancel'),
    onOk: async () => {
      revokingId.value = record.sessionId
      try {
        await sessionApi.revokeUser(record.userId)
        message.success(t('sessions.kicked'))
        if (isSelf) {
          logoutSelf()
          return
        }
        await load()
      } catch {
        message.error(t('sessions.revokeFail'))
      } finally {
        revokingId.value = null
      }
    },
  })
}

/** 吊销的是自己的会话:清本地令牌并回登录页。 */
function logoutSelf() {
  clearAdminSession()
  window.location.assign('/admin/login')
}

onMounted(load)
</script>

<style scoped lang="scss">
.sess-systems {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}
.sess-systems .pill {
  gap: 5px;
}
.sess-systems__time {
  font-style: normal;
  font-size: 10px;
  opacity: 0.72;
}
</style>
