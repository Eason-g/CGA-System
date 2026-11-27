// 管理端双语辅助：按代码翻译角色、岗位类型、审批节点、状态、院系名、考核分档等
// 原则：中文模式保持原有显示（配置 / 后端给的中文原样），英文模式按代码查 admin.* 词条，查不到时退回原文
import { t, isEn, localized } from '@/i18n'

// 词条存在返回译文，否则返回 null
function lookup(key, params) {
  const v = t(key, params)
  return v === key ? null : v
}

/** 角色名：中文模式优先用账号自带的 roleLabel（如“校级系统/财务管理员”），英文按 role 代码翻译 */
export function roleLabel(role, fallback) {
  if (!isEn.value && fallback) return fallback
  return lookup(`admin.roles.${role}`) || fallback || role || '-'
}

/** 岗位类型全称：助教（TA） / Teaching Assistant (TA) */
export function jobTypeLabel(type) {
  return lookup(`admin.jobTypes.${type}`) || type || '-'
}

/** 审批节点名：中文模式下有配置里的 label 就用它，英文按节点代码翻译 */
export function stepLabel(key, fallback) {
  if (!isEn.value && fallback) return fallback
  return lookup(`admin.steps.${key}`) || fallback || key
}

/** 状态表（JOB_STATUS_MAP 等）里的状态名：英文模式取 labelEn，缺省退回中文 */
export function statusLabel(map, code, empty = code) {
  const item = map?.[code]
  return item ? localized(item, 'label') || code : empty
}

export function statusTone(map, code) {
  return map?.[code]?.tone || 'neutral'
}

function findDept(db, deptId) {
  return db?.departments?.find((d) => d.id === deptId)
}

/** 院系简称（英文模式取 shortNameEn） */
export function deptShortName(db, deptId) {
  return localized(findDept(db, deptId), 'shortName') || deptId || '-'
}

/** 院系全称（英文模式取 nameEn） */
export function deptName(db, deptId) {
  const d = findDept(db, deptId)
  return localized(d, 'name') || localized(d, 'shortName') || deptId || '-'
}

// 考核分档：后端 / ruleEngine 返回中文分档名（属于记录数据），这里映射成代码再翻译
const GRADE_CODES = { 优秀: 'EXCELLENT', 合格: 'PASS', 不合格: 'FAIL' } // i18n-ignore
export function gradeCode(grade) {
  return GRADE_CODES[grade] || grade
}
export function gradeLabel(grade) {
  return lookup(`admin.grades.${gradeCode(grade)}`) || grade || '-'
}

/** 考核维度名：英文模式按岗位类型 + 维度 key 翻译种子维度，自定义维度显示配置原文 */
export function evalDimLabel(jobType, dim) {
  if (!dim) return ''
  if (!isEn.value) return dim.label
  return lookup(`admin.evaluation.dims.${jobType}.${dim.key}`) || dim.label
}

/** 审计对象类型 */
export function auditTargetLabel(type) {
  return lookup(`admin.audit.targets.${type}`) || type
}

/** 后端校验 / 拦截信息列表：英文模式优先用 xxxEn 数组（如 errorsEn） */
export function localizedList(obj, field) {
  if (!obj) return []
  const en = obj[`${field}En`]
  if (isEn.value && Array.isArray(en) && en.length) return en
  return obj[field] || []
}
