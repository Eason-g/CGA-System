import { createSeedData } from './seedData'

const STORAGE_KEY = 'CAMPUS_TARGA_DB_V1_1'
const AUTH_KEY = 'CAMPUS_TARGA_AUTH_V1_1'
const UNIV_KEY = 'CAMPUS_TARGA_UNIV_V1_1'

export const UNIVERSITY_LIST = [
  { id: 'UNIV_TSINGHUA', name: '清华大学', enName: 'Tsinghua University', code: '10003' },
  { id: 'UNIV_PKU', name: '北京大学', enName: 'Peking University', code: '10001' },
  { id: 'UNIV_ZJU', name: '浙江大学', enName: 'Zhejiang University', code: '10335' },
  { id: 'UNIV_FUDAN', name: '复旦大学', enName: 'Fudan University', code: '10246' },
  { id: 'UNIV_SJTU', name: '上海交通大学', enName: 'Shanghai Jiao Tong University', code: '10248' },
  { id: 'UNIV_NJU', name: '南京大学', enName: 'Nanjing University', code: '10284' },
  { id: 'UNIV_WHU', name: '武汉大学', enName: 'Wuhan University', code: '10486' },
  { id: 'UNIV_HUST', name: '华中科技大学', enName: 'Huazhong University of Science and Technology', code: '10487' },
  { id: 'UNIV_SYSU', name: '中山大学', enName: 'Sun Yat-sen University', code: '10558' },
  { id: 'UNIV_DEMO', name: '示范高校联盟（通用院校）', enName: 'Alliance University (Demo)', code: '10000' }
]

export function getSavedUniversityId() {
  try {
    return localStorage.getItem(UNIV_KEY) || 'UNIV_DEMO'
  } catch {
    return 'UNIV_DEMO'
  }
}

export function saveUniversityId(univId) {
  try {
    localStorage.setItem(UNIV_KEY, univId || 'UNIV_DEMO')
  } catch {
    // ignore
  }
}

let dbCache = null

export function initDB() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && parsed.meta && parsed.meta.version === '1.1.0' && Array.isArray(parsed.jobs)) {
        dbCache = parsed
        return dbCache
      }
    }
  } catch (err) {
    console.warn('Failed to parse localStorage DB, re-seeding:', err)
  }
  dbCache = createSeedData()
  persistDB()
  return dbCache
}

export function getDB() {
  if (!dbCache) {
    return initDB()
  }
  return dbCache
}

export function persistDB() {
  if (!dbCache) return
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(dbCache))
  } catch (err) {
    console.error('Failed to persist DB to localStorage:', err)
  }
}

export function resetDB() {
  dbCache = createSeedData()
  persistDB()
  return dbCache
}

export function getSavedUserId() {
  try {
    return localStorage.getItem(AUTH_KEY) || null
  } catch {
    return null
  }
}

export function saveUserId(userId) {
  try {
    if (userId) {
      localStorage.setItem(AUTH_KEY, userId)
    } else {
      localStorage.removeItem(AUTH_KEY)
    }
  } catch {
    // ignore storage errors
  }
}

export function generateId(prefix) {
  const rand = Math.random().toString(36).slice(2, 6).toUpperCase()
  const ts = Date.now().toString().slice(-4)
  return `${prefix}_${ts}${rand}`
}

export function nowTimestamp() {
  const d = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

export function addAuditLog(db, { targetType, targetId, operator, action, opinion = '', beforeValue = '', afterValue = '' }) {
  const entry = {
    id: generateId('LOG'),
    targetType,
    targetId,
    operatorName: operator?.name || '系统',
    operatorRole: operator?.roleLabel || '系统引擎',
    action,
    opinion,
    beforeValue,
    afterValue,
    timestamp: nowTimestamp()
  }
  db.auditLogs.unshift(entry)
  return entry
}
