// 岗位类型差异化字段（configs.jobTypeSchemas）的前端辅助函数
// 发布页、配置中心都从这里读取配置；快照里缺少该配置（旧版 mock 本地数据、旧版后端库）时退回种子配置
import { createSeedData } from '@/mock/seedData'
import { t, isEn } from '@/i18n'

export const JOB_TYPES = ['TA', 'RA', 'GA']

// label 用 getter：模板渲染时读取，随语言切换自动刷新
const withLabel = (prefix) => (value) => ({
  value,
  get label() {
    return t(`${prefix}.${value}`)
  }
})

export const FIELD_TYPE_OPTIONS = ['text', 'textarea', 'number', 'select', 'lines', 'date'].map(withLabel('admin.fieldTypes'))

export const FUND_SOURCE_OPTIONS = ['TA_FUND', 'RA_PROJECT', 'GA_FUND'].map(withLabel('admin.fundSources'))

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

// ---------------------------------------------------------------- 双语显示
// 字段 label / placeholder 是配置中心里的中文配置数据：中文模式原样显示；
// 英文模式按字段 key 查 admin.schema.fields.<key> 词条（种子字段），查不到（管理员自定义字段）时显示配置原文

function lookupEn(key) {
  if (!isEn.value) return null
  const v = t(key)
  return v === key ? null : v
}

export function fieldLabel(field) {
  return lookupEn(`admin.schema.fields.${field?.key}.label`) || field?.label || field?.key || ''
}

export function fieldPlaceholder(field) {
  if (!field?.placeholder) return ''
  return lookupEn(`admin.schema.fields.${field.key}.placeholder`) || field.placeholder
}

// 下拉选项：语言分数选项按取值翻译（如 0 → 无特殊语言限制），其余显示配置原文
export function optionLabel(field, opt) {
  if (field?.key === 'minLangScore') return lookupEn(`admin.schema.langOptions.${String(opt?.value).replace('.', '_')}`) || opt?.label
  return opt?.label
}

// 岗位类型配置里的名称、说明、国际生开关文案
export function schemaText(type, schema, part) {
  const map = { label: 'label', desc: 'desc', intlLabel: 'intlToggle.label', intlHint: 'intlToggle.hint' }
  const raw = map[part].split('.').reduce((o, k) => (o ? o[k] : undefined), schema)
  return lookupEn(`admin.schema.types.${type}.${part}`) || raw || (part === 'label' ? type : '')
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
    if (f.required && empty) errors.push(t('admin.schema.required', { label: fieldLabel(f) }))
    if (f.type === 'select' && !empty && !(f.options || []).some((o) => String(o.value) === String(v))) {
      errors.push(t('admin.schema.invalidOption', { label: fieldLabel(f) }))
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
  const v = t(`admin.fundSources.${value}`)
  return v === `admin.fundSources.${value}` ? value : v
}

// 仅 mock 模式使用：模拟后端生成的派生字段（http 模式由后端统一生成）
export function deriveExtraFieldsForMock(type, extraFields, { weeks = 16, deadline } = {}) {
  const extra = { ...(extraFields || {}) }
  if (type === 'TA') {
    const score = Number(extra.minLangScore || 0)
    // mock 后端生成的数据文案，与 Java 版一致，保持中文
    extra.languageRequirement = score > 0 ? `IELTS ${score.toFixed(1)} 及以上` : '无特殊语言限制' // i18n-ignore
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
