// P1 第三批：材料上传、合规核验、财务对接的前端接口。
// 只在 http 模式可用；mock 模式统一提示“该功能需要连接后端”（见 docs/P1设计.md）。
import { http, isHttpMode, getToken } from './http'
import { t } from '@/i18n'

// 前端本地生成的提示按当前语言取词条（调用时求值，切换语言后也正确）
const needBackend = () => ({ code: 400, data: null, message: t('admin.svc.needBackend') })
const networkError = () => ({ code: 0, data: null, message: t('admin.svc.networkError') })
const enc = (id) => encodeURIComponent(id ?? '')

// 与后端一致：单个文件 ≤ 10MB，只收 pdf/jpg/png（真实类型由后端按文件头判断）
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024
export const ACCEPT_TYPES = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

// 附件类型名：getter 在渲染时取词条，学生端、管理端同样随语言切换
export const ATTACHMENT_KIND_MAP = Object.defineProperties(
  {},
  Object.fromEntries(
    ['RESUME', 'LANG_CERT', 'VISA', 'OTHER'].map((k) => [k, { enumerable: true, get: () => t(`admin.attachmentKind.${k}`) }])
  )
)

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function parseJson(resp) {
  try {
    const text = await resp.text()
    const payload = text ? JSON.parse(text) : null
    if (payload && typeof payload === 'object' && 'code' in payload) {
      const out = { code: payload.code, data: payload.data ?? null, message: payload.message || '' }
      if (payload.messageEn) out.messageEn = payload.messageEn
      return out
    }
  } catch {
    // 非 JSON（如代理错误页）
  }
  return resp.status >= 500 ? networkError() : { code: resp.status, data: null, message: t('admin.svc.httpFailed', { status: resp.status }) }
}

export function formatFileSize(bytes) {
  const n = Number(bytes || 0)
  if (n >= 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`
  if (n >= 1024) return `${Math.round(n / 1024)} KB`
  return `${n} B`
}

export const materialFinanceApi = {
  // 上传一个文件：kind 取 RESUME / LANG_CERT / VISA / OTHER
  async uploadAttachment(file, kind) {
    if (!isHttpMode) return needBackend()
    if (!file) return { code: 400, data: null, message: t('admin.svc.chooseFile') }
    if (file.size > MAX_UPLOAD_BYTES) return { code: 400, data: null, message: t('admin.svc.fileTooLarge') }
    const form = new FormData()
    form.append('file', file)
    form.append('kind', kind)
    try {
      const resp = await fetch('/api/attachments', { method: 'POST', headers: authHeaders(), body: form })
      return parseJson(resp)
    } catch {
      return networkError()
    }
  },

  // 可见范围内的附件元数据：{ ownerId, applicationId, kind } 均可选
  listAttachments(params = {}) {
    if (!isHttpMode) return Promise.resolve({ ...needBackend(), data: [] })
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v) q.set(k, v)
    })
    return http.get(`/api/attachments${q.toString() ? `?${q}` : ''}`)
  },

  // 带令牌下载（不能用裸链接，因为要带 Authorization），成功后触发浏览器保存
  async downloadAttachment(attachment) {
    if (!isHttpMode) return needBackend()
    let resp
    try {
      resp = await fetch(`/api/attachments/${enc(attachment.id)}`, { headers: authHeaders() })
    } catch {
      return networkError()
    }
    if (!resp.ok) return parseJson(resp)
    const blob = await resp.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = attachment.originalName || attachment.id
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 10000)
    return { code: 200, data: null, message: t('admin.svc.downloadStarted') }
  },

  // 合规核验列表（国际生办、校级管理员）
  listComplianceMaterials(status) {
    if (!isHttpMode) return Promise.resolve({ ...needBackend(), data: [] })
    return http.get(`/api/compliance/materials${status ? `?status=${enc(status)}` : ''}`)
  },

  // 核验：{ attachmentId, decision: APPROVE|REJECT, languageLevel, languageCert, visaExpiry, visaType, opinion }
  verifyCompliance(userId, payload) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.post(`/api/users/${enc(userId)}/compliance-verify`, payload)
  },

  // 财务两步发放：提交财务
  submitPayrollToFinance(payrollId) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.post(`/api/payrolls/${enc(payrollId)}/submit-finance`)
  },

  // 演示：模拟财务系统回调（成功 / 失败）
  simulateFinanceCallback(payrollId, success, reason) {
    if (!isHttpMode) return Promise.resolve(needBackend())
    return http.post(`/api/payrolls/${enc(payrollId)}/simulate-finance-callback`, { success, reason })
  }
}
