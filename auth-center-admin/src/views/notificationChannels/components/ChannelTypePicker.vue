<!--
  ChannelTypePicker —— 建渠道时挑厂商;编辑时只显示已定的那一个。

  类型在编辑态锁死:字段名随厂商而变(企微机器人要 webhook、SMTP 要主机端口),换了类型
  等于换一份凭据,旧值一个都对不上。新建态用卡片网格而不是下拉 —— 厂商图标让人一眼认出
  是哪家,不必逐条读文字。
-->
<template>
  <section class="ce-section">
    <h4 class="ce-section-title">{{ $t('notificationChannels.field.type') }}</h4>

    <div v-if="locked" class="ce-type-locked">
      <span class="ce-type-locked-icon"><component :is="vendorIcon(picked)" /></span>
      <span class="ce-type-locked-name">{{ typeLabel(picked) }}</span>
    </div>

    <div v-else class="ce-type-grid">
      <button
        v-for="tp in types"
        :key="tp.name"
        type="button"
        class="ce-type-card"
        :class="{ 'is-active': picked === tp.name }"
        @click="pick(tp.name)"
      >
        <span class="ce-type-card-icon"><component :is="vendorIcon(tp.name)" /></span>
        <span class="ce-type-card-name">{{ tp.displayName }}</span>
      </button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { type Component } from 'vue'
import {
  ApiOutlined,
  DingtalkOutlined,
  MailOutlined,
  MessageOutlined,
  WechatOutlined,
} from '@ant-design/icons-vue'
import { type ChannelTypeMeta } from '@/api'

const props = defineProps<{
  types: ChannelTypeMeta[]
  /** 编辑态:类型已定,不给改(见组件头)。 */
  locked?: boolean
}>()

/** 选中的类型名。 */
const picked = defineModel<string | undefined>({ required: true })

/** 真的换了类型 —— 由父组件据此清空已填的凭据字段(字段名不同,留着会把上一家的值带进下一家)。 */
const emit = defineEmits<{ change: [name: string] }>()

/** 厂商图标 —— 让人在类型卡上一眼认出是哪家,不必只读文字。 */
const VENDOR_ICONS: Record<string, Component> = {
  WECOM_BOT: WechatOutlined,
  WECOM_APP: WechatOutlined,
  DINGTALK_BOT: DingtalkOutlined,
  LARK_BOT: MessageOutlined,
  SMTP: MailOutlined,
}

function vendorIcon(name?: string): Component {
  return (name && VENDOR_ICONS[name]) || ApiOutlined
}

function typeLabel(name?: string): string {
  if (!name) return ''
  return props.types.find((tp) => tp.name === name)?.displayName ?? name
}

function pick(name: string): void {
  if (picked.value === name) return
  picked.value = name
  emit('change', name)
}
</script>

<style scoped lang="scss">
.ce-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 段标题:轻描淡写的小号大写标签,分组而不抢戏 */
.ce-section-title {
  margin: 0;
  font-size: 11.5px;
  font-weight: 600;
  letter-spacing: 0.06em;
  color: var(--ds-text-faint);
}

/* ── 类型选择:紧凑图标卡,三列 ── */
.ce-type-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.ce-type-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 11px;
  background: var(--ds-card-bg);
  border: 1px solid var(--ds-border);
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition:
    border-color 0.15s ease,
    background-color 0.15s ease;

  &:hover,
  &:focus-visible {
    border-color: var(--ds-primary);
    outline: none;
  }

  &.is-active {
    border-color: var(--ds-primary);
    background: var(--ds-primary-soft);

    .ce-type-card-icon,
    .ce-type-card-name {
      color: var(--ds-primary);
    }
  }
}

.ce-type-card-icon {
  display: inline-flex;
  font-size: 15px;
  color: var(--ds-text-soft);
  flex-shrink: 0;
}

.ce-type-card-name {
  font-size: 12.5px;
  font-weight: 500;
  line-height: 1.25;
  color: var(--ds-text-soft);
}

/* 编辑态:类型已定,退成一枚不可点的徽标 */
.ce-type-locked {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  align-self: flex-start;
  padding: 7px 12px;
  background: var(--ds-bg-soft);
  border: 1px solid var(--ds-border-soft);
  border-radius: 8px;
}

.ce-type-locked-icon {
  display: inline-flex;
  font-size: 15px;
  color: var(--ds-text-soft);
}

.ce-type-locked-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--ds-text);
}
</style>
