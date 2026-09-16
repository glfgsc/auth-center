<!--
  大模型用量看板。

  三层结构(自上而下由粗到细,每层回答一个问题):
    1) 分产品卡片 —— 「哪个产品在烧 token」                            → UsageSystemCards
    2) 按时间 × 模型的堆叠图(可切表格)—— 「什么时候烧的、烧在哪个模型上」 → UsageTrendSection
    3) 分用户 / 模型 / 智能体的明细 —— 「具体是谁」                      → UsageBreakdownSection

  数据源是 AI 信任遥测(所有 LLM 调用的统一留痕),与「AI 信任日志」同表不同问法:
  那边逐条看合规,这边聚合看消耗。

  本文件只做编排:三块面板共用同一份筛选条件与取数,状态收敛在 useModelUsage,
  换算收敛在 usageFormat。
-->
<template>
  <div class="ac-page usage-page">
    <PageHeader :title="t('usage.title')" :description="t('usage.subtitle')">
      <template #actions>
        <a-button :loading="loading" @click="reload">
          <template #icon><ReloadOutlined /></template>
          {{ t('common.refresh') }}
        </a-button>
      </template>
    </PageHeader>

    <UsageFilterBar :range-days="rangeDays" :range-text="rangeText" @pick-range="pickRange" />

    <UsageSystemCards
      :rows="summary"
      :grand-total="grandTotal"
      :active-code="systemCode"
      :loading="loading"
      :system-label="systemLabel"
      @toggle="toggleSystem"
    />

    <UsageTrendSection :series="series" :series-bucket="seriesBucket" :loading="loading" />

    <UsageBreakdownSection
      :rows="breakdown"
      :dimension="dimension"
      :loading="loading"
      :deep-turn-id="deepTurnId"
      :expanded-keys="expandedKeys"
      :row-key-of="rowKeyOf"
      :total="breakdownTotal"
      :page="breakdownPage"
      :size="breakdownSize"
      @switch-dimension="switchDimension"
      @clear-deeplink="clearDeepLink()"
      @expand="onExpand"
      @pick-page="pickBreakdownPage"
    />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { ReloadOutlined } from '@ant-design/icons-vue'
import PageHeader from '@/components/common/PageHeader.vue'
import { useModelUsage } from './composables/useModelUsage'
import UsageBreakdownSection from './components/UsageBreakdownSection.vue'
import UsageFilterBar from './components/UsageFilterBar.vue'
import UsageSystemCards from './components/UsageSystemCards.vue'
import UsageTrendSection from './components/UsageTrendSection.vue'

const { t } = useI18n()

const {
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
} = useModelUsage()
</script>
