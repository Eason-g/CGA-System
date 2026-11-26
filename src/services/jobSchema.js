// 岗位类型差异化字段（configs.jobTypeSchemas）的前端辅助函数
// 发布页、配置中心都从这里读取配置；快照里缺少该配置（旧版 mock 本地数据、旧版后端库）时退回种子配置
import { createSeedData } from '@/mock/seedData'

export const JOB_TYPES = ['TA', 'RA', 'GA']

export const FIELD_TYPE_OPTIONS = [
  { value: 'text', label: '单行文本' },
  { value: 'textarea', label: '多行文本' },
  { value: 'number', label: '数字' },
  { value: 'select', label: '下拉选择' },
  { value: 'lines', label: '多行列表（每行一条）' },
  { value: 'date', label: '日期' }
]

export const FUND_SOURCE_OPTIONS = [
  { value: 'TA_FUND', label: '助教经费' },
  { value: 'RA_PROJECT', label: '科研项目经费' },
  { value: 'GA_FUND', label: '助管专项经费' }
]

// 由后端生成的字段，配置中心不允许新增同名字段
export const DERIVED_FIELD_KEYS = ['languageRequirement', 'milestones', 'scheduleSlots']

let seedSchemas = null
function getSeedSchemas() {
  if (!seedSchemas) seedSchemas = createSeedData().configs.jobTypeSchemas
  return seedSchemas
}

const clone = (v) => JSON.parse(JSON.stringify(v))

// 三种岗位类型的配置（缺哪种类型就用种子里的那份）
export function getJobTypeSchemas(db) {
  const fromDb = db?.configs?.jobTypeSchemas || {}
  const seed = getSeedSchemas()
  const result = {}
  for (const t of JOB_TYPES) {
    result[t] = fromDb[t] && Array.isArray(fromDb[t].fields) ? fromDb[t] : seed[t]
  }
  return result
}

export function getJobTypeSchema(db, type) {
  return getJobTypeSchemas(db)[type] || { fields: [], allowedFundSources: [], defaults: {} }
}

// 按字段定义生成表单初始值：优先取 defaultValue；lines 类型用多行字符串编辑
export function initialExtraValues(schema) {
  const values = {}
  for (const f of schema?.fields || []) {
    let v = f.defaultValue
    if (v === undefined || v === null) v = f.type === 'select' ? f.options?.[0]?.value ?? '' : ''
    if (f.type === 'lines' && Array.isArray(v)) v = v.join('\n')
    values[f.key] = clone(v)
  }
  return values
}

// 把表单值整理成提交给后端的 extraFields（字段名与配置 key 一致；lines 转为字符串数组）
export function buildExtraFields(schema, values) {
  const extra = {}
  for (const f of schema?.fields || []) {
    let v = values[f.key]
    if (f.type === 'lines') {
      v = String(v ?? '')
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
    } else if (f.type === 'number' && v !== '' && v !== null && v !== undefined) {
      v = Number(v)
    } else if (typeof v === 'string') {
      v = v.trim()
    }
    extra[f.key] = v
  }
  return extra
}

// 前端提前提示用：必填为空、下拉值不在选项内（后端会再校验一次）
export function validateExtraFields(schema, values) {
  const errors = []
  for (const f of schema?.fields || []) {
    const v = values[f.key]
    const empty = v === undefined || v === null || String(v).trim() === ''
    if (f.required && empty) errors.push(`请填写「${f.label}」`)
    if (f.type === 'select' && !empty && !(f.options || []).some((o) => String(o.value) === String(v))) {
      errors.push(`「${f.label}」的取值不在可选项内`)
    }
  }
  return errors
}

// 经费来源是否符合岗位类型
export function isFundAllowed(schema, budget) {
  const allowed = schema?.allowedFundSources?.length ? schema.allowedFundSources : null
  return !allowed || !budget || allowed.includes(budget.sourceType)
}

export function fundSourceLabel(value) {
  return FUND_SOURCE_OPTIONS.find((o) => o.value === value)?.label || value
}

// 仅 mock 模式使用：模拟后端生成的派生字段（http 模式由后端统一生成）
export function deriveExtraFieldsForMock(type, extraFields, { weeks = 16, deadline } = {}) {
  const extra = { ...(extraFields || {}) }
  if (type === 'TA') {
    const score = Number(extra.minLangScore || 0)
    extra.languageRequirement = score > 0 ? `IELTS ${score.toFixed(1)} 及以上` : '无特殊语言限制'
  }
  if (type === 'RA' && 'milestonesText' in extra) {
    const lines = String(extra.milestonesText || '')
      .split('\n')
      .map((s) => s.trim())
      .filter(Boolean)
    delete extra.milestonesText
    const base = deadline ? new Date(`${deadline}T00:00:00`) : new Date()
    // 截止日后的第一个周一起算，按聘期均分
    const start = new Date(base)
    start.setDate(start.getDate() + (((8 - start.getDay()) % 7) || 7))
    const total = Math.max(1, Number(weeks) || 1) * 7
    extra.milestones = lines.map((title, i) => {
      const d = new Date(start)
      d.setDate(d.getDate() + Math.round((total * (i + 1)) / lines.length))
      const pad = (n) => String(n).padStart(2, '0')
      return { id: `MS_${Date.now().toString(36)}_${i + 1}`, title, dueDate: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`, done: false }
    })
  }
  return extra
}
