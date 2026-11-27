// 真实后端请求封装：基于 fetch，自动携带 JWT 和 Accept-Language，统一返回 { code, data, message }
import { getLocale } from '@/i18n'

// 接口模式：http（默认，请求 Java 后端）/ mock（localStorage 假后端，便于未装后端时演示）
// 在 Node 脚本里直接导入时没有 import.meta.env，按空配置处理
const env = import.meta.env || {}
export const API_MODE = env.VITE_API_MODE === 'mock' ? 'mock' : 'http'
export const isHttpMode = API_MODE === 'http'

// 演示账号统一密码（与后端 app.demo-password 一致），仅用于“体验账号”快捷登录
export const DEMO_PASSWORD = env.VITE_DEMO_PASSWORD || 'Cga@2026'

const TOKEN_KEY = 'CGA_AUTH_TOKEN'
const NETWORK_ERROR = { code: 0, data: null, message: '无法连接服务器，请确认后端已启动' }

export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY) || null
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // 忽略存储异常（如隐私模式）
  }
}

// 401 处理：由 main.js 注册（清空登录状态并跳转登录页）；未注册时退回直接改 hash
let unauthorizedHandler = null
export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn
}

function handleUnauthorized() {
  setToken(null)
  if (unauthorizedHandler) {
    unauthorizedHandler()
  } else if (typeof window !== 'undefined') {
    window.location.hash = '#/login'
  }
}

/**
 * 发起请求。无论 HTTP 状态码如何都解析 JSON 并返回 { code, data, message }。
 * @param {string} method HTTP 方法
 * @param {string} url 相对路径，如 /api/jobs
 * @param {object} [body] 请求体（自动 JSON 序列化）
 * @param {object} [options] { skipAuthRedirect: 为 true 时 401 不触发登出跳转（登录接口用） }
 */
export async function request(method, url, body, options = {}) {
  const headers = { Accept: 'application/json', 'Accept-Language': getLocale() }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let resp
  try {
    resp = await fetch(url, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined
    })
  } catch {
    return { ...NETWORK_ERROR }
  }

  let payload = null
  try {
    const text = await resp.text()
    payload = text ? JSON.parse(text) : null
  } catch {
    payload = null
  }

  let result
  if (payload && typeof payload === 'object' && 'code' in payload) {
    result = { code: payload.code, data: payload.data ?? null, message: payload.message || '' }
  } else if (resp.status >= 500) {
    // 开发代理连不上后端时返回 5xx 且没有 JSON 体（后端自身的 500 一定带 JSON 体）
    result = { ...NETWORK_ERROR }
  } else {
    result = {
      code: resp.status,
      data: payload,
      message: resp.ok ? '操作成功' : `请求失败（HTTP ${resp.status}）`
    }
  }

  if ((resp.status === 401 || result.code === 401) && !options.skipAuthRedirect) {
    handleUnauthorized()
  }
  return result
}

export const http = {
  get: (url, options) => request('GET', url, undefined, options),
  post: (url, body, options) => request('POST', url, body ?? {}, options),
  put: (url, body, options) => request('PUT', url, body ?? {}, options),
  del: (url, options) => request('DELETE', url, undefined, options)
}

/** 从 Content-Disposition 取文件名：优先 filename*（RFC 5987，UTF-8 编码中文），其次 filename */
function parseFileName(disposition, fallback) {
  if (!disposition) return fallback
  const star = /filename\*\s*=\s*([^']*)''([^;]+)/i.exec(disposition)
  if (star) {
    try {
      return decodeURIComponent(star[2].trim().replace(/^"|"$/g, ''))
    } catch {
      // 编码异常时退回普通 filename
    }
  }
  const plain = /filename\s*=\s*"?([^";]+)"?/i.exec(disposition)
  return plain ? plain[1] : fallback
}

/**
 * 下载文件（报表导出等）：带 Authorization 的 fetch，拿到 Blob 后用临时链接触发浏览器保存。
 * 不能用裸 <a href>，因为裸链接带不上 token。
 * 成功返回 { code: 200, data: { fileName, size }, message }；失败时后端返回 JSON，转成 { code, data, message }。
 */
export async function download(url, fallbackName = 'download') {
  const headers = { Accept: '*/*', 'Accept-Language': getLocale() }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let resp
  try {
    resp = await fetch(url, { method: 'GET', headers })
  } catch {
    return { ...NETWORK_ERROR }
  }

  const type = resp.headers.get('Content-Type') || ''
  if (!resp.ok || type.includes('application/json')) {
    let payload = null
    try {
      payload = await resp.json()
    } catch {
      payload = null
    }
    if (resp.status === 401) handleUnauthorized()
    if (payload && typeof payload === 'object' && 'code' in payload) {
      return { code: payload.code, data: payload.data ?? null, message: payload.message || '' }
    }
    return resp.status >= 500
      ? { ...NETWORK_ERROR }
      : { code: resp.status, data: null, message: `下载失败（HTTP ${resp.status}）` }
  }

  const blob = await resp.blob()
  const fileName = parseFileName(resp.headers.get('Content-Disposition'), fallbackName)
  const href = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = href
  a.download = fileName
  a.style.display = 'none'
  document.body.appendChild(a)
  a.click()
  a.remove()
  // 稍后再释放，避免部分浏览器在保存开始前就失效
  setTimeout(() => URL.revokeObjectURL(href), 10000)
  return { code: 200, data: { fileName, size: blob.size }, message: `已导出 ${fileName}` }
}
