<!--
  签名公钥集(JWKS)查看 —— 外部应用验签本中心签发的 JWT 时用的公钥。只读。
-->
<template>
  <div class="tn-card ea-jwks">
    <div class="ea-jwks__head">
      <SafetyCertificateOutlined class="ea-jwks__icon" />
      <span class="ea-jwks__title">{{ $t('externalApps.jwksTitle') }}</span>
    </div>
    <p class="ea-jwks__desc">{{ $t('externalApps.jwksDesc') }}</p>
    <div class="ea-info">
      <span class="ea-info__label">Endpoint</span>
      <code class="ea-info__val">{{ JWKS_PATH }}</code>
      <a-button size="small" type="text" @click="emit('copy', jwksUrl)"><CopyOutlined /></a-button>
    </div>
    <div v-for="k in keys" :key="k.kid" class="ea-jwk">
      <span class="ea-jwk__badge">{{ k.alg || DEFAULT_ALG }}</span>
      <code class="ea-jwk__kid">kid: {{ k.kid }}</code>
    </div>
    <div v-if="!keys.length" class="ea-secret-row--empty">{{ $t('externalApps.jwksEmpty') }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { CopyOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import type { JwkKey } from '@/api'

/** JWKS 的标准发现路径(RFC 8615 well-known)—— 外部应用按它拉公钥。 */
const JWKS_PATH = '/.well-known/jwks.json'

/** 公钥未声明 alg 时的展示回退 —— 本中心只签 RS256。 */
const DEFAULT_ALG = 'RS256'

defineProps<{
  /** 公钥集。 */
  keys: JwkKey[]
}>()

const emit = defineEmits<{ copy: [text: string] }>()

const jwksUrl = computed(() => `${window.location.origin}${JWKS_PATH}`)
</script>

<style scoped lang="scss">
@use './_externalApps.scss';

.ea-jwks {
  padding: 16px 18px;
  margin-top: 12px;
}
.ea-jwks__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.ea-jwks__icon {
  color: var(--ds-primary);
}
.ea-jwks__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ds-text);
}
.ea-jwks__desc {
  font-size: 12.5px;
  color: var(--ds-text-muted);
  margin: 0 0 8px;
  line-height: 1.6;
}
.ea-jwk {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}
.ea-jwk__badge {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 6px;
  background: var(--ds-primary-soft);
  color: var(--ds-primary-soft-text);
}
.ea-jwk__kid {
  font-size: 12px;
  color: var(--ds-text-faint);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
