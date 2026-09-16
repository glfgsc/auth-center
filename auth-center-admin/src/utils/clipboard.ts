import { message } from 'ant-design-vue'
import { i18n } from '@/i18n'

/**
 * 复制文本到剪贴板 —— 失败必须提示。
 *
 * `navigator.clipboard` 在非安全上下文(http 访问)与权限被拒时会抛,静默吞掉的话
 * 用户以为复制成功、粘出来是旧内容 —— 密钥、回调地址这类一次性可见的东西尤其致命。
 *
 * @param text 待复制文本
 */
export async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    message.success(i18n.global.t('common.copied'))
  } catch (e) {
    console.warn('[clipboard] 写剪贴板失败(通常是非安全上下文或权限被拒)', e)
    message.error(i18n.global.t('common.copyFail'))
  }
}
