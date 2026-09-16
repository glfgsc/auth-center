<!--
  SsoPage — 登录设置(CAS)管理，按顶栏当前产品分档。
  布局:限宽内容列;主表单(连接/模式/映射/行为)+ 右侧粘性状态栏(状态摘要 +
  测试连接就地反馈 + 集成信息)。状态与动作收敛在 useSsoConfig。
  标题与空态都点名在配哪一档 —— 产品自己那一行,还是对所有未单独配置的产品生效的全局兜底档。
-->
<template>
  <div class="ac-page sso-page">
    <a-tabs class="sso-tabs" default-active-key="cas">
      <a-tab-pane key="cas" :tab="$t('sso.casTab')">
        <a-spin :spinning="spinning">
      <!-- 空态:未配置 SSO -->
      <div v-if="!form.exists && !loading" class="sso-empty">
        <div class="sso-empty__icon">
          <SafetyOutlined />
        </div>
        <h3 class="sso-empty__title">
          {{ isGlobalFallback ? $t('sso.emptyTitle') : $t('sso.emptyTitleProduct', { product: productLabel }) }}
        </h3>
        <p class="sso-empty__desc">
          {{ isGlobalFallback ? $t('sso.emptyDesc') : $t('sso.emptyDescProduct', { product: productLabel }) }}
        </p>
        <a-button type="primary" size="large" @click="enableCas">
          <template #icon><PlusOutlined /></template>
          {{ $t('sso.enableCta') }}
        </a-button>
      </div>

      <!-- 已配置 SSO -->
      <template v-else-if="form.exists">
        <PageHeader :title="$t('nav.sso')" :description="scopeDesc">
          <template #actions>
            <span v-if="dirty" class="sso-unsaved">
              <span class="sso-unsaved__dot" />
              {{ $t('sso.unsaved') }}
            </span>
            <a-popconfirm :title="deleteConfirmText" @confirm="removeCas">
              <a-button danger>{{ $t('sso.deleteCas') }}</a-button>
            </a-popconfirm>
            <a-button type="primary" :loading="saving" @click="save">
              {{ $t('common.save') }}
            </a-button>
          </template>
        </PageHeader>

        <div class="sso-layout">
          <div class="sso-main">
            <SsoConnectionCard />
            <SsoModeCard />
            <SsoMappingCard />
            <SsoBehaviorCard />
          </div>
          <aside class="sso-aside">
            <SsoStatusAside
              :testing="testing"
              :test-result="testResult"
              :callback-pattern="callbackPattern"
              @test="testConnection"
            />
          </aside>
        </div>
      </template>
        </a-spin>
      </a-tab-pane>
      <a-tab-pane key="oauth" :tab="$t('sso.oauthTab')">
        <OAuthPage />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import { PlusOutlined, SafetyOutlined } from '@ant-design/icons-vue'

import { SSO_FORM_KEY, useSsoConfig } from './composables/useSsoConfig'
import PageHeader from '@/components/common/PageHeader.vue'
import SsoConnectionCard from './components/SsoConnectionCard.vue'
import SsoModeCard from './components/SsoModeCard.vue'
import SsoMappingCard from './components/SsoMappingCard.vue'
import SsoBehaviorCard from './components/SsoBehaviorCard.vue'
import SsoStatusAside from './components/SsoStatusAside.vue'
import OAuthPage from '@/views/oauth/OAuthPage.vue'

const { t } = useI18n()

const {
  form, loading, spinning, saving, testing, testResult, callbackPattern, dirty,
  isGlobalFallback, productLabel,
  load, save, testConnection, removeCas, enableCas,
} = useSsoConfig()

/** 页头描述点名作用域 —— 「仅对某产品生效」还是「所有未单独配置的产品」。 */
const scopeDesc = computed(() =>
  isGlobalFallback.value
    ? t('sso.scopeGlobal')
    : t('sso.scopeProduct', { product: productLabel.value }),
)

/** 删除确认同样点名后果:产品档删了是回落兜底档,兜底档删了才是全线回本地登录。 */
const deleteConfirmText = computed(() =>
  isGlobalFallback.value
    ? t('sso.deleteCasConfirm')
    : t('sso.deleteCasConfirmProduct', { product: productLabel.value }),
)

provide(SSO_FORM_KEY, form)

onMounted(load)
</script>

<style scoped lang="scss">
/* ── 主/侧双列布局 ── */
.sso-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 16px;
  align-items: start;

  @media (max-width: 1080px) {
    grid-template-columns: 1fr;
  }
}

.sso-main {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.sso-aside {
  position: sticky;
  top: 24px;

  @media (max-width: 1080px) {
    position: static;
  }
}

/* ── 未保存提示 ── */
.sso-unsaved {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ds-warning-soft-text);
  margin-right: 4px;
  white-space: nowrap;
}

.sso-unsaved__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--ds-warning);
}

/* ── 空态 ── */
.sso-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 72px 32px;
  background: var(--ds-empty-bg);
  border: 1px dashed var(--ds-empty-border);
  border-radius: var(--ds-radius);
}

.sso-empty__icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--ds-empty-icon-bg);
  color: var(--ds-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;

  :deep(.anticon) {
    font-size: 26px;
  }
}

.sso-empty__title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ds-text);
  margin: 0 0 8px;
}

.sso-empty__desc {
  font-size: 13px;
  color: var(--ds-text-muted);
  margin: 0 0 24px;
  max-width: 400px;
  line-height: 1.6;
}
</style>
