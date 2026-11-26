import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { initDB, getSavedUserId, saveUserId, UNIVERSITY_LIST, getSavedUniversityId, saveUniversityId } from '@/mock/mockDB'
import { api } from '@/services/api'

export const useAppStore = defineStore('app', () => {
  const db = ref(initDB())
  const currentUserId = ref(getSavedUserId())
  const currentUniversityId = ref(getSavedUniversityId())
  const loadingCount = ref(0)
  const toasts = ref([])
  const archiveContractId = ref(null)

  const universities = UNIVERSITY_LIST
  const currentUniversity = computed(() => {
    return universities.find((u) => u.id === currentUniversityId.value) || universities[universities.length - 1]
  })

  function setUniversity(univId) {
    currentUniversityId.value = univId
    saveUniversityId(univId)
  }

  const isLoading = computed(() => loadingCount.value > 0)

  const currentUser = computed(() => {
    if (!currentUserId.value || !db.value) return null
    return db.value.users.find((u) => u.id === currentUserId.value) || null
  })

  const presetUsers = computed(() => {
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

  async function refreshSnapshot() {
    const res = await withLoading(() => api.fetchSnapshot())
    if (res.code === 200 && res.data) {
      db.value = res.data
    }
    return res
  }

  async function loginAs(userId) {
    const res = await withLoading(() => api.loginByUserId(userId))
    if (res.code === 200 && res.data) {
      currentUserId.value = res.data.id
      saveUserId(res.data.id)
      await refreshSnapshot()
      notify(res.message, 'success')
      return res.data
    } else {
      notify(res.message || '登录失败', 'error')
      return null
    }
  }

  async function loginWithUsername(username) {
    const res = await withLoading(() => api.loginByCredentials(username))
    if (res.code === 200 && res.data) {
      currentUserId.value = res.data.id
      saveUserId(res.data.id)
      await refreshSnapshot()
      notify(res.message, 'success')
      return res.data
    } else {
      notify(res.message || '认证失败', 'error')
      return null
    }
  }

  function logout() {
    currentUserId.value = null
    saveUserId(null)
    notify('已退出当前演示身份', 'info')
  }

  async function resetDemoData() {
    const res = await withLoading(() => api.resetDemoData())
    if (res.code === 200 && res.data) {
      db.value = res.data
      notify('已一键重置为初始演示数据', 'success')
    }
    return res
  }

  async function mutate(apiPromiseFn, successToast = true) {
    const res = await withLoading(apiPromiseFn)
    if (res.code === 200) {
      await refreshSnapshot()
      if (successToast && res.message) {
        notify(res.message, 'success')
      }
    } else {
      notify(res.message || '操作被规则引擎拦截', 'error')
    }
    return res
  }

  function openArchive(contractId) {
    archiveContractId.value = contractId
  }

  function closeArchive() {
    archiveContractId.value = null
  }

  // Lookup helpers
  function getDeptName(deptId) {
    return db.value?.departments?.find((d) => d.id === deptId)?.name || deptId || '-'
  }

  function getDeptShortName(deptId) {
    return db.value?.departments?.find((d) => d.id === deptId)?.shortName || deptId || '-'
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
    universities,
    currentUniversityId,
    currentUniversity,
    setUniversity,
    presetUsers,
    isLoading,
    toasts,
    unreadNotifications,
    userNotifications,
    archiveContractId,
    notify,
    dismissToast,
    refreshSnapshot,
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
