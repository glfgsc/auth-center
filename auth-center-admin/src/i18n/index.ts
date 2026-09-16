// i18n 入口 —— 复用共享设计系统的工厂(@loom/shared-ui/i18n):legacy:false + globalInjection +
// locale 从 localStorage(key `locale`,与 bi-front / agent-console 同 key,同源可共享语言偏好)。
// 本 app 仅提供自己的 messages;切换语言走顶栏内置的 LanguageSwitcher。
import { createI18nInstance } from '@loom/shared-ui/i18n'
import zhCN from './zh-CN'
import enUS from './en-US'

export const i18n = createI18nInstance({ 'zh-CN': zhCN, 'en-US': enUS })
