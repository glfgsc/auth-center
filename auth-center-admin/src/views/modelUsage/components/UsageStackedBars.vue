<!--
  UsageStackedBars — 按时间桶的堆叠柱状图,分段 = 模型。

  纯 CSS 绘制,不引图表库:本控制台只此一处需要图,为一根柱子拉一个渲染引擎不值当,
  而且 CSS 方案天然跟着 --ds-* 令牌走深浅色,不必再为图表单独做一套主题桥接。

  颜色取语义令牌轮转,不写死色值 —— 模型是数据驱动的(今天两个、换服务商后可能五个),
  写死几种颜色迟早不够用;轮转在超出调色板时回到第一色,宁可重复也不出现看不见的段。
-->
<template>
  <div class="usb">
    <!-- 图例:模型 → 颜色。放在图上方,读柱子之前先建立对应关系 -->
    <div class="usb__legend">
      <span v-for="(m, i) in models" :key="m" class="usb__legend-item">
        <i class="usb__swatch" :style="{ background: colorOf(i) }" />
        {{ m }}
      </span>
    </div>

    <div v-if="!buckets.length" class="usb__empty">{{ emptyText }}</div>

    <div v-else class="usb__plot">
      <!-- 纵轴刻度:只给峰值与中值两条参考线,再多就成了网格纸 -->
      <div class="usb__axis">
        <span>{{ fmt(peak) }}</span>
        <span>{{ fmt(peak / 2) }}</span>
        <span>0</span>
      </div>

      <div class="usb__bars">
        <div v-for="b in buckets" :key="b.bucket" class="usb__col">
          <div class="usb__stack" :title="tooltipOf(b)">
            <div
              v-for="seg in b.segments"
              :key="seg.model"
              class="usb__seg"
              :style="{
                height: heightOf(seg.value),
                background: colorOf(models.indexOf(seg.model)),
              }"
            />
          </div>
          <span class="usb__tick">{{ tickOf(b.bucket) }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { UsageSeriesPoint } from '@/api'
import { fmt } from '../usageFormat'

const props = defineProps<{
  points: UsageSeriesPoint[]
  /** 后端按跨度自动选的桶粒度('hour' / 'day'),决定横轴刻度怎么写 */
  bucket: string
  emptyText: string
}>()

/** 语义色轮转 —— 深浅色由令牌自身负责,这里只管取哪一个。 */
const PALETTE = [
  'var(--ds-primary)',
  'var(--ds-success)',
  'var(--ds-warning)',
  'var(--ds-info)',
  'var(--ds-danger)',
]

function colorOf(index: number): string {
  return PALETTE[((index % PALETTE.length) + PALETTE.length) % PALETTE.length]
}

/** 出现过的模型(按首次出现顺序),同时是图例顺序与配色下标来源。 */
const models = computed<string[]>(() => {
  const seen: string[] = []
  for (const p of props.points) if (!seen.includes(p.model)) seen.push(p.model)
  return seen
})

interface Column {
  bucket: string
  total: number
  segments: Array<{ model: string; value: number }>
}

/** 把扁平的 (桶,模型,量) 行集折成按桶分组的列。 */
const buckets = computed<Column[]>(() => {
  const byBucket = new Map<string, Column>()
  for (const p of props.points) {
    let col = byBucket.get(p.bucket)
    if (!col) {
      col = { bucket: p.bucket, total: 0, segments: [] }
      byBucket.set(p.bucket, col)
    }
    col.segments.push({ model: p.model, value: p.totalTokens })
    col.total += p.totalTokens
  }
  return [...byBucket.values()].sort((a, b) => a.bucket.localeCompare(b.bucket))
})

/** 纵轴峰值。全 0 时给 1,避免除零把所有段算成 NaN%。 */
const peak = computed(() => Math.max(1, ...buckets.value.map((b) => b.total)))

function heightOf(value: number): string {
  return `${(value / peak.value) * 100}%`
}

/** 横轴刻度:按天只留「月-日」,按小时只留「时」—— 完整时间戳挤在一起谁也读不清。 */
function tickOf(bucket: string): string {
  return props.bucket === 'hour' ? (bucket.split(' ')[1] ?? bucket) : bucket.slice(5)
}

function tooltipOf(col: Column): string {
  const lines = col.segments
    .slice()
    .sort((a, b) => b.value - a.value)
    .map((s) => `${s.model} ${fmt(s.value)}`)
  return [`${col.bucket} 合计 ${fmt(col.total)}`, ...lines].join('\n')
}
</script>

<style scoped lang="scss">
.usb__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--ds-text-soft);
}
.usb__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.usb__swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}

.usb__empty {
  padding: 40px 0;
  text-align: center;
  color: var(--ds-text-faint);
  font-size: 13px;
}

.usb__plot {
  display: flex;
  gap: 10px;
}

.usb__axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 200px;
  padding-bottom: 20px; /* 与横轴刻度行等高,使 0 对齐柱底 */
  font-size: 11px;
  color: var(--ds-text-faint);
  text-align: right;
  white-space: nowrap;
}

.usb__bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  min-width: 0;
  overflow-x: auto;
}

.usb__col {
  flex: 1 1 0;
  min-width: 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* 柱体自下而上堆叠:column-reverse 让第一个模型落在最底部,与图例顺序一致 */
.usb__stack {
  width: 100%;
  height: 200px;
  display: flex;
  flex-direction: column-reverse;
  border-radius: 3px 3px 0 0;
  overflow: hidden;
  background: var(--ds-bg-soft);
  cursor: default;
}

.usb__seg {
  width: 100%;
  min-height: 1px; /* 极小值也留一线,否则"有量"和"没量"看起来一样 */
  transition: opacity 0.12s;
}
.usb__stack:hover .usb__seg {
  opacity: 0.82;
}

.usb__tick {
  height: 20px;
  line-height: 20px;
  font-size: 11px;
  color: var(--ds-text-faint);
  white-space: nowrap;
}
</style>
