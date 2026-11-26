// P1 第三批：材料上传、合规核验、财务对接的前端接口。
// 只在 http 模式可用；mock 模式统一提示“该功能需要连接后端”（见 docs/P1设计.md）。
import { http, isHttpMode, getToken } from './http'

const NEED_BACKEND = { code: 400, data: null, message: '该功能需要连接后端' }
const NETWORK_ERROR = { code: 0, data: null, message: '无法连接服务器，请确认后端已启动' }
const enc = (id) => encodeURIComponent(id ?? '')

// 与后端一致：单个文件 ≤ 10MB，只收 pdf/jpg/png（真实类型由后端按文件头判断）
export const MAX_UPLOAD_BYTES = 10 * 1024 * 1024
export const ACCEPT_TYPES = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

export const ATTACHMENT_KIND_MAP = {
  RESUME: '个人简历',
  LANG_CERT: '语言证明',
  VISA: '签证/居留许可',
  OTHER: '其他材料'
}

function authHeaders() {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

async function parseJson(resp) {
  try {
    const text = await resp.text()
    const payload = text ? JSON.parse(text) : null
    if (payload && typeof payload === 'object' && 'code' in payload) {
      return { code: payload.code, data: payload.data ?? null, message: payload.message || '' }
    }
  } catch {
    // 非 JSON（如代理错误页）
  }
  return resp.status >= 500 ? { ...NETWORK_ERROR } : { code: resp.status, data: null, message: `请求失败（HTTP ${resp.status}）` }
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
    if (!isHttpMode) return { ...NEED_BACKEND }
    if (!file) return { code: 400, data: null, message: '请选择要上传的文件' }
    if (file.size > MAX_UPLOAD_BYTES) return { code: 400, data: null, message: '文件大小超过 10MB 上限' }
    const form = new FormData()
    form.append('file', file)
    form.append('kind', kind)
    try {
      const resp = await fetch('/api/attachments', { method: 'POST', headers: authHeaders(), body: form })
      return parseJson(resp)
    } catch {
      return { ...NETWORK_ERROR }
    }
  },

  // 可见范围内的附件元数据：{ ownerId, applicationId, kind } 均可选
  listAttachments(params = {}) {
    if (!isHttpMode) return Promise.resolve({ ...NEED_BACKEND, data: [] })
    const q = new URLSearchParams()
    Object.entries(params).forEach(([k, v]) => {
      if (v) q.set(k, v)
    })
    return http.get(`/api/attachments${q.toString() ? `?${q}` : ''}`)
  },

  // 带令牌下载（不能用裸链接，因为要带 Authorization），成功后触发浏览器保存
  async downloadAttachment(attachment) {
    if (!isHttpMode) return { ...NEED_BACKEND }
    let resp
    try {
      resp = await fetch(`/api/attachments/${enc(attachment.id)}`, { headers: authHeaders() })
    } catch {
      return { ...NETWORK_ERROR }
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
    return { code: 200, data: null, message: '已开始下载' }
  },

  // 合规核验列表（国际生办、校级管理员）
  listComplianceMaterials(status) {
    if (!isHttpMode) return Promise.resolve({ ...NEED_BACKEND, data: [] })
    return http.get(`/api/compliance/materials${status ? `?status=${enc(status)}` : ''}`)
  },

  // 核验：{ attachmentId, decision: APPROVE|REJECT, languageLevel, languageCert, visaExpiry, visaType, opinion }
  verifyCompliance(userId, payload) {
    if (!isHttpMode) return Promise.resolve({ ...NEED_BACKEND })
    return http.post(`/api/users/${enc(userId)}/compliance-verify`, payload)
  },

  // 财务两步发放：提交财务
  submitPayrollToFinance(payrollId) {
    if (!isHttpMode) return Promise.resolve({ ...NEED_BACKEND })
    return http.post(`/api/payrolls/${enc(payrollId)}/submit-finance`)
  },

  // 演示：模拟财务系统回调（成功 / 失败）
  simulateFinanceCallback(payrollId, success, reason) {
    if (!isHttpMode) return Promise.resolve({ ...NEED_BACKEND })
    return http.post(`/api/payrolls/${enc(payrollId)}/simulate-finance-callback`, { success, reason })
  }
}
