// 真实后端请求封装：基于 fetch，自动携带 JWT，统一返回 { code, data, message }

// 接口模式：http（默认，请求 Java 后端）/ mock（localStorage 假后端，便于未装后端时演示）
export const API_MODE = import.meta.env.VITE_API_MODE === 'mock' ? 'mock' : 'http'
export const isHttpMode = API_MODE === 'http'

// 演示账号统一密码（与后端 app.demo-password 一致），仅用于“体验账号”快捷登录
export const DEMO_PASSWORD = import.meta.env.VITE_DEMO_PASSWORD || 'Cga@2026'

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
  const headers = { Accept: 'application/json' }
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
  put: (url, body, options) => request('PUT', url, body ?? {}, options)
}
