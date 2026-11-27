// P2 B 批：站点信息、统一身份认证（模拟 CAS）、学籍系统与科研系统对接（均为模拟实现）
// 这些功能只在 http 模式下可用；mock 模式返回提示，不在 localStorage 假后端里实现
import { http, isHttpMode } from '@/services/http'
import { t } from '@/i18n'

const needBackend = () => ({ code: 400, data: null, message: t('admin.svc.needBackend') })
const enc = encodeURIComponent

// mock 模式下的默认站点信息（与后端 app.school-name 默认值一致）
// 名称用 getter 按当前语言取词条；http 模式下后端返回的值会覆盖它们
export const DEFAULT_SITE = {
  get schoolName() {
    return t('admin.svc.schoolName')
  },
  sso: {
    enabled: false,
    provider: 'mock',
    mock: true,
    get displayName() {
      return t('admin.svc.ssoName')
    }
  }
}

// 统一认证回跳地址：当前页面去掉查询串和 hash（hash 路由下 ticket 会被追加在 ? 之后、# 之前）
export function ssoServiceUrl() {
  return `${window.location.origin}${window.location.pathname}`
}

// 从地址栏取出回跳带回的 ticket，并立即从地址栏清掉（保留 hash 路由）
export function takeSsoTicketFromUrl() {
  if (typeof window === 'undefined') return null
  const params = new URLSearchParams(window.location.search)
  const ticket = params.get('ticket')
  if (!ticket) return null
  params.delete('ticket')
  const rest = params.toString()
  const clean = `${window.location.pathname}${rest ? `?${rest}` : ''}${window.location.hash || '#/login'}`
  window.history.replaceState(window.history.state, '', clean)
  return ticket
}

// 回跳带回、尚未换票的 ticket（main.js 写入，登录页取走后清空，只用一次）
let pendingSsoTicket = null
export function setPendingSsoTicket(ticket) {
  pendingSsoTicket = ticket || null
}
export function consumePendingSsoTicket() {
  const t = pendingSsoTicket
  pendingSsoTicket = null
  return t
}

export const integrationApi = {
  // 公开：{ schoolName, sso: { enabled, provider, displayName, mock } }
  getSite() {
    if (!isHttpMode) return Promise.resolve({ code: 200, data: DEFAULT_SITE, message: '' })
    return http.get('/api/public/site', { skipAuthRedirect: true })
  },

  getSsoLoginUrl(service) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.get(`/api/auth/sso/login-url?service=${enc(service)}`, { skipAuthRedirect: true })
  },

  // 成功时 data 为 { token, user }，与密码登录相同；401/403 不触发全局登出跳转
  exchangeSsoTicket({ ticket, service }) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.post('/api/auth/sso/exchange', { ticket, service }, { skipAuthRedirect: true })
  },

  // 学籍同步（校级管理员、国际生办）：data 为 { source, syncedAt, checked, changedStudents, diffs, unmatched, skipped }
  syncSis() {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.post('/api/integration/sis/sync')
  },

  // 科研项目查询与预校验：data 为 { ok, source, project, jobEndDate, checks }
  getResearchProject({ code, deptId, deadline, weeks }) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    const q = new URLSearchParams()
    if (deptId) q.set('deptId', deptId)
    if (deadline) q.set('deadline', deadline)
    if (weeks) q.set('weeks', String(weeks))
    const qs = q.toString()
    return http.get(`/api/integration/research/projects/${enc((code || '').trim())}${qs ? `?${qs}` : ''}`)
  }
}
