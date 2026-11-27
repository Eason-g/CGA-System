import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { initDB, getSavedUserId, saveUserId } from '@/mock/mockDB'
import { api } from '@/services/api'
import { integrationApi, DEFAULT_SITE, ssoServiceUrl } from '@/services/integrationApi'
import { isHttpMode, DEMO_PASSWORD, getToken, setToken } from '@/services/http'
import { setTodayStr, localDateStr } from '@/services/ruleEngine'
import { createSeedData } from '@/mock/seedData'
import { t, isEn, localized } from '@/i18n'

// http 模式下的初始空库：结构与 mock db 一致，登录后由 /api/snapshot 填充
function createEmptyDB() {
  return {
    meta: { version: '1.1.0', currentWeekNo: 1, currentMonth: localDateStr().slice(0, 7), today: localDateStr() },
    departments: [],
    users: [],
    budgets: [],
    configs: { workflows: {}, evaluationTemplates: {}, globalRules: {}, jobTypeSchemas: {} },
    jobs: [],
    applications: [],
    contracts: [],
    timesheets: [],
    evaluations: [],
    payrolls: [],
    notifications: [],
    auditLogs: [],
    jobTemplates: []
  }
}

// 补齐快照中缺失的集合，避免页面访问 undefined
function normalizeSnapshot(data) {
  const empty = createEmptyDB()
  const merged = { ...empty, ...data }
  merged.meta = { ...empty.meta, ...(data?.meta || {}) }
  merged.configs = { ...empty.configs, ...(data?.configs || {}) }
  for (const key of Object.keys(empty)) {
    if (Array.isArray(empty[key]) && !Array.isArray(merged[key])) merged[key] = []
  }
  return merged
}

// http 模式下的体验账号列表：快照只含当前用户可见的用户，登录前也拿不到，故取种子数据中的预置账号（仅展示字段）
const HTTP_PRESET_USERS = createSeedData()
  .users.filter((u) => u.preset)
  .map(({ id, name, username, studentNo, role, roleLabel, portal, preset, presetDesc, deptId }) => ({
    id, name, username, studentNo, role, roleLabel, portal, preset, presetDesc, deptId
  }))

export const useAppStore = defineStore('app', () => {
  const db = ref(isHttpMode ? createEmptyDB() : initDB())
  // mock 模式从 localStorage 恢复用户 ID；http 模式由 token 调 /auth/me 恢复（见 restoreSession）
  const currentUserId = ref(isHttpMode ? null : getSavedUserId())
  // http 模式：登录接口或 /auth/me 返回的当前用户
  const sessionUser = ref(null)
  const lastLoginError = ref('')
  let restorePromise = null
  const loadingCount = ref(0)
  const toasts = ref([])
  const archiveContractId = ref(null)

  // P2 B.4：校名与统一认证开关来自后端配置（GET /api/public/site），不再在前端维护高校名单
  const site = ref(DEFAULT_SITE)
  const schoolName = computed(() => site.value?.schoolName || DEFAULT_SITE.schoolName)
  let sitePromise = null

  function loadSite() {
    if (sitePromise) return sitePromise
    sitePromise = integrationApi.getSite().then((res) => {
      if (res.code === 200 && res.data) site.value = { ...DEFAULT_SITE, ...res.data }
      else sitePromise = null
      return site.value
    })
    return sitePromise
  }

  const isLoading = computed(() => loadingCount.value > 0)

  const currentUser = computed(() => {
    if (!currentUserId.value || !db.value) return null
    const fromDb = db.value.users.find((u) => u.id === currentUserId.value) || null
    if (!isHttpMode) return fromDb
    // http 模式：优先用快照里的本人记录（含 hoursSummary、资料更新后的最新值），快照未到时用登录返回的用户
    return fromDb || sessionUser.value
  })

  const presetUsers = computed(() => {
    if (isHttpMode) return HTTP_PRESET_USERS
    return (db.value?.users || []).filter((u) => u.preset)
  })

  const unreadNotifications = computed(() => {
    if (!currentUser.value) return []
    return (db.value?.notifications || []).filter((n) => n.userId === currentUser.value.id && !n.read)
  })

  const userNotifications = computed(() => {
    if (!currentUser.value) return []
    return (db.value?.notifications || []).filter((n) => n.userId === currentUser.value.id)
  })

  /**
   * 接口返回的提示文案：英文模式优先 messageEn。
   * - 失败提示：没有 messageEn 时退回后端中文 message（信息更具体），再退回词条 fallbackKey
   * - 成功提示（success: true）：英文模式下没有 messageEn 时用词条 fallbackKey，避免成功提示夹中文
   */
  function resMessage(res, fallbackKey, params, { success = false } = {}) {
    if (success && isEn.value) return res?.messageEn || t(fallbackKey, params)
    return localized(res, 'message') || t(fallbackKey, params)
  }

  function notify(message, type = 'info') {
    const id = Date.now() + Math.random()
    toasts.value.push({ id, message, type })
    setTimeout(() => {
      toasts.value = toasts.value.filter((t) => t.id !== id)
    }, 3600)
  }

  function dismissToast(id) {
    toasts.value = toasts.value.filter((t) => t.id !== id)
  }

  async function withLoading(fn) {
    loadingCount.value += 1
    try {
      return await fn()
    } finally {
      loadingCount.value = Math.max(0, loadingCount.value - 1)
    }
  }

  function applySnapshot(data) {
    if (isHttpMode) {
      db.value = normalizeSnapshot(data)
      setTodayStr(db.value.meta?.today)
    } else {
      db.value = data
    }
  }

  async function refreshSnapshot() {
    const res = await withLoading(() => api.fetchSnapshot())
    if (res.code === 200 && res.data) {
      applySnapshot(res.data)
    }
    return res
  }

  // http 模式：保存 token 与用户并拉取快照
  async function startSession(token, user) {
    setToken(token)
    sessionUser.value = user
    currentUserId.value = user.id
    await refreshSnapshot()
  }

  // http 模式：清除 token、用户与数据（退出登录、token 失效时调用）
  function clearSession() {
    setToken(null)
    sessionUser.value = null
    currentUserId.value = null
    db.value = createEmptyDB()
    setTodayStr(null)
  }

  // 刷新页面后用本地 token 恢复登录；路由守卫会等待它完成。mock 模式直接完成
  function restoreSession() {
    if (restorePromise) return restorePromise
    restorePromise = (async () => {
      if (!isHttpMode || !getToken()) return
      const res = await withLoading(() => api.fetchCurrentUser())
      if (res.code === 200 && res.data) {
        sessionUser.value = res.data
        currentUserId.value = res.data.id
        await refreshSnapshot()
      } else if (res.code === 0) {
        // 连不上后端：保留 token，提示后回到登录页
        notify(resMessage(res, 'http.networkError'), 'error')
      } else {
        clearSession()
      }
    })()
    return restorePromise
  }

  // http 模式：账号 + 密码登录，成功返回用户，失败返回 null 并把后端文案写入 lastLoginError
  async function httpLogin(account, password) {
    lastLoginError.value = ''
    const res = await withLoading(() => api.loginByCredentials(account, password))
    if (res.code === 200 && res.data?.token && res.data?.user) {
      await startSession(res.data.token, res.data.user)
      notify(resMessage(res, 'toast.welcomeBack', { name: res.data.user.name }, { success: true }), 'success')
      return res.data.user
    }
    lastLoginError.value = resMessage(res, 'toast.authFailed')
    notify(lastLoginError.value, 'error')
    return null
  }

  // P2 B.1：统一身份认证回跳后用 ticket 换取本系统 JWT；失败返回 null 并把后端文案写入 lastLoginError
  async function loginWithSsoTicket(ticket) {
    lastLoginError.value = ''
    const res = await withLoading(() => integrationApi.exchangeSsoTicket({ ticket, service: ssoServiceUrl() }))
    if (res.code === 200 && res.data?.token && res.data?.user) {
      await startSession(res.data.token, res.data.user)
      notify(resMessage(res, 'toast.welcomeBack', { name: res.data.user.name }, { success: true }), 'success')
      return res.data.user
    }
    lastLoginError.value = resMessage(res, 'toast.ssoFailed')
    notify(lastLoginError.value, 'error')
    return null
  }

  async function loginAs(userId) {
    if (isHttpMode) {
      // 体验账号：用预置账号 + 演示密码走正常登录
      const preset = HTTP_PRESET_USERS.find((u) => u.id === userId)
      if (!preset) {
        notify(t('toast.demoMissing'), 'error')
        return null
      }
      return httpLogin(preset.username || preset.studentNo, DEMO_PASSWORD)
    }
    const res = await withLoading(() => api.loginByUserId(userId))
    if (res.code === 200 && res.data) {
      currentUserId.value = res.data.id
      saveUserId(res.data.id)
      await refreshSnapshot()
      notify(resMessage(res, 'toast.welcomeBack', { name: res.data.name }, { success: true }), 'success')
      return res.data
    } else {
      notify(resMessage(res, 'toast.loginFailed'), 'error')
      return null
    }
  }

  async function loginWithUsername(username, password) {
    if (isHttpMode) return httpLogin(username, password)
    const res = await withLoading(() => api.loginByCredentials(username))
    if (res.code === 200 && res.data) {
      currentUserId.value = res.data.id
      saveUserId(res.data.id)
      await refreshSnapshot()
      notify(resMessage(res, 'toast.welcomeBack', { name: res.data.name }, { success: true }), 'success')
      return res.data
    } else {
      notify(resMessage(res, 'toast.authFailed'), 'error')
      return null
    }
  }

  function logout() {
    if (isHttpMode) {
      clearSession()
      notify(t('toast.loggedOut'), 'info')
      return
    }
    currentUserId.value = null
    saveUserId(null)
    notify(t('toast.demoLoggedOut'), 'info')
  }

  async function resetDemoData() {
    if (isHttpMode) {
      // 仅校级管理员可重置；其他角色显示后端返回的错误文案
      const res = await withLoading(() => api.resetDemoData())
      if (res.code === 200) {
        if (res.data) applySnapshot(res.data)
        else await refreshSnapshot()
        notify(resMessage(res, 'toast.resetDone', null, { success: true }), 'success')
      } else {
        notify(resMessage(res, 'toast.resetFailed'), 'error')
      }
      return res
    }
    const res = await withLoading(() => api.resetDemoData())
    if (res.code === 200 && res.data) {
      db.value = res.data
      notify(t('toast.resetDone'), 'success')
    }
    return res
  }

  async function mutate(apiPromiseFn, successToast = true) {
    const res = await withLoading(apiPromiseFn)
    if (res.code === 200) {
      await refreshSnapshot()
      if (successToast && res.message) {
        notify(resMessage(res, 'toast.done', null, { success: true }), 'success')
      }
    } else {
      notify(resMessage(res, 'toast.blocked'), 'error')
    }
    return res
  }

  function openArchive(contractId) {
    archiveContractId.value = contractId
  }

  function closeArchive() {
    archiveContractId.value = null
  }

  // Lookup helpers（院系名称：英文模式优先 nameEn / shortNameEn，没有时退回中文）
  function getDeptName(deptId) {
    const dept = db.value?.departments?.find((d) => d.id === deptId)
    return localized(dept, 'name') || deptId || '-'
  }

  function getDeptShortName(deptId) {
    const dept = db.value?.departments?.find((d) => d.id === deptId)
    return localized(dept, 'shortName') || deptId || '-'
  }

  function getUser(userId) {
    return db.value?.users?.find((u) => u.id === userId) || null
  }

  function getJob(jobId) {
    return db.value?.jobs?.find((j) => j.id === jobId) || null
  }

  function getBudget(budgetId) {
    return db.value?.budgets?.find((b) => b.id === budgetId) || null
  }

  return {
    db,
    currentUserId,
    currentUser,
    site,
    schoolName,
    loadSite,
    loginWithSsoTicket,
    presetUsers,
    isLoading,
    toasts,
    unreadNotifications,
    userNotifications,
    archiveContractId,
    notify,
    resMessage,
    dismissToast,
    refreshSnapshot,
    lastLoginError,
    restoreSession,
    clearSession,
    loginAs,
    loginWithUsername,
    logout,
    resetDemoData,
    mutate,
    openArchive,
    closeArchive,
    getDeptName,
    getDeptShortName,
    getUser,
    getJob,
    getBudget
  }
})
