// 中英双语：极简实现（不引入 vue-i18n）。
// - 词条按模块拆分：locales/<语言>/common.js（通用、状态、学生端、登录、评价页）、admin.js（管理端），避免多人同时改一个大文件
// - t('a.b.c', { n: 1 }) 按点号取词条，{n} 插值；英文缺词条时退回中文，再退回 key 本身（便于发现漏译）
// - 当前语言存在 localStorage，刷新不丢；同步到 <html lang>；http.js 用 Accept-Language 告诉后端
import { ref, computed } from 'vue'
import zhCommon from './locales/zh-CN/common.js'
import zhAdmin from './locales/zh-CN/admin.js'
import enCommon from './locales/en-US/common.js'
import enAdmin from './locales/en-US/admin.js'

export const LOCALES = [
  { code: 'zh-CN', label: '中文' },
  { code: 'en-US', label: 'English' }
]

const STORAGE_KEY = 'CGA_LOCALE'
const messages = {
  'zh-CN': { ...zhCommon, ...zhAdmin },
  'en-US': { ...enCommon, ...enAdmin }
}

function initialLocale() {
  try {
    const saved = typeof localStorage !== 'undefined' ? localStorage.getItem(STORAGE_KEY) : null
    if (saved && messages[saved]) return saved
  } catch {
    // 忽略存储异常（隐私模式、Node 脚本）
  }
  return 'zh-CN'
}

export const locale = ref(initialLocale())
export const isEn = computed(() => locale.value === 'en-US')

function syncDocument() {
  if (typeof document !== 'undefined') document.documentElement.lang = locale.value
}
syncDocument()

export function setLocale(code) {
  if (!messages[code]) return
  locale.value = code
  try {
    localStorage.setItem(STORAGE_KEY, code)
  } catch {
    // 忽略
  }
  syncDocument()
}

export function toggleLocale() {
  setLocale(isEn.value ? 'zh-CN' : 'en-US')
}

/** 当前语言代码，供非组件代码（http.js）读取 */
export function getLocale() {
  return locale.value
}

function lookup(dict, key) {
  return key.split('.').reduce((node, part) => (node && typeof node === 'object' ? node[part] : undefined), dict)
}

/**
 * 取词条。读取 locale.value，在模板 / computed 里使用时语言切换会自动刷新。
 * @param {string} key 点号路径，如 'nav.jobs'
 * @param {object} [params] 插值参数，如 { n: 3 } 对应词条里的 {n}
 */
export function t(key, params) {
  const current = locale.value
  let text = lookup(messages[current], key)
  if (text === undefined && current !== 'zh-CN') text = lookup(messages['zh-CN'], key)
  if (text === undefined) return key
  if (typeof text !== 'string') return text
  return params ? text.replace(/\{(\w+)\}/g, (m, name) => (params[name] ?? m)) : text
}

/**
 * 后端返回的双语字段：英文模式下优先取 xxxEn（如 detailEn、labelEn、messageEn），没有时退回中文原字段。
 * 用法：localized(check, 'detail')、localized(res, 'message')
 */
export function localized(obj, field) {
  if (!obj) return ''
  if (isEn.value) {
    const en = obj[`${field}En`]
    if (en !== undefined && en !== null && en !== '') return en
  }
  return obj[field] ?? ''
}

/** 组件里使用：const { t, isEn, locale, setLocale, toggleLocale, localized } = useI18n() */
export function useI18n() {
  return { t, isEn, locale, setLocale, toggleLocale, localized, LOCALES }
}

/** Vue 插件：模板里可直接用 $t、$localized、$isEn */
export default {
  install(app) {
    app.config.globalProperties.$t = t
    app.config.globalProperties.$localized = localized
    Object.defineProperty(app.config.globalProperties, '$isEn', { get: () => isEn.value })
  }
}
