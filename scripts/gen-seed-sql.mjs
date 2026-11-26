// 由前端种子数据生成后端 data.sql：node scripts/gen-seed-sql.mjs
// 读取 src/mock/seedData.js 的 createSeedData()，输出 server/src/main/resources/db/data.sql，
// 保证后端演示数据与前端 mock 完全一致。列名为前端字段的 snake_case。
import { writeFileSync } from 'node:fs'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const seedPath = path.join(root, 'src', 'mock', 'seedData.js')
const outPath = path.join(root, 'server', 'src', 'main', 'resources', 'db', 'data.sql')

const { createSeedData } = await import(pathToFileURL(seedPath).href)
const db = createSeedData()

// 与 ruleEngine.js 的 calcJobBudgetCost 相同：quota × 时薪 × 周工时 × 周数
const calcJobBudgetCost = ({ quota = 0, hourlyRate = 0, weeklyHours = 0, weeks = 0 }) =>
  Math.max(0, Number(quota) * Number(hourlyRate) * Number(weeklyHours) * Number(weeks))

// 岗位预留（reserved_amount）：后端维护的不变量是 经费冻结额 = Σ max(岗位预留 − 该岗位已发放, 0)。
// 前端种子里的冻结额是手填的演示数字，岗位按全额成本算会超出经费总额（如 B_CS_TA 可用额变成负数）。
// 这里以演示冻结额为准，按各岗位"剩余成本"的比例分摊，得到每个岗位的预留，保证上面的不变量从一开始就成立。
// 已驳回岗位不占预留；没有岗位的经费（如 B_CS_RA_TIGHT）冻结额保持原样。
const seedReserved = new Map()
for (const budget of db.budgets) {
  const jobs = db.jobs.filter((j) => j.budgetId === budget.id && j.status !== 'REJECTED')
  const paidOf = (job) => db.payrolls
    .filter((p) => p.jobId === job.id && p.financeStatus === 'PAID')
    .reduce((sum, p) => sum + Number(p.amount || 0), 0)
  const rows = jobs.map((job) => ({ job, paid: paidOf(job), remaining: Math.max(0, calcJobBudgetCost(job) - paidOf(job)) }))
  const totalRemaining = rows.reduce((sum, r) => sum + r.remaining, 0)
  const frozen = Number(budget.frozenAmount || 0)
  let allocated = 0
  rows.forEach((r, i) => {
    // 最后一个岗位取差额，保证分摊之和正好等于冻结额（以分为单位取整）
    const share = i === rows.length - 1
      ? Math.round((frozen - allocated) * 100) / 100
      : totalRemaining > 0 ? Math.round((frozen * r.remaining / totalRemaining) * 100) / 100 : 0
    allocated += share
    seedReserved.set(r.job.id, Math.round((r.paid + share) * 100) / 100)
  })
}

const snakeToCamel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase())

// 把 JS 值转成 MySQL 字面量（默认 sql_mode 下反斜杠是转义符，需要一并转义）
function literal(value, isJson = false) {
  if (value === undefined || value === null) return 'NULL'
  if (isJson) return quote(JSON.stringify(value))
  if (typeof value === 'boolean') return value ? '1' : '0'
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) throw new Error(`非法数值：${value}`)
    return String(value)
  }
  return quote(String(value))
}

function quote(s) {
  return "'" + s.replace(/\\/g, '\\\\').replace(/'/g, "''").replace(/\n/g, '\\n').replace(/\r/g, '\\r') + "'"
}

/**
 * 生成一张表的 INSERT。
 * columns: 列名数组；jsonColumns: JSON 列；overrides: { 列名: (row) => 值 }；defaults: 前端缺省字段的取值
 */
function insert(table, rows, { columns, jsonColumns = [], overrides = {}, defaults = {} }) {
  if (!rows.length) return `-- ${table}: 无数据\n`
  const values = rows.map((row) => {
    const cells = columns.map((col) => {
      let v = col in overrides ? overrides[col](row) : row[snakeToCamel(col)]
      if ((v === undefined || v === null) && col in defaults) v = defaults[col]
      return literal(v, jsonColumns.includes(col))
    })
    return `  (${cells.join(', ')})`
  })
  return `INSERT INTO ${table} (${columns.join(', ')}) VALUES\n${values.join(',\n')};\n`
}

const counts = Object.entries(db)
  .filter(([, v]) => Array.isArray(v))
  .map(([k, v]) => `${k}=${v.length}`)
  .join(' ')

const parts = []
// seed-counts 行供后端集成测试核对快照条数与 seedData.js 一致
parts.push(`-- =====================================================================
-- 种子数据：由 scripts/gen-seed-sql.mjs 从 src/mock/seedData.js 自动生成，请勿手改。
-- 用户 password_hash 为空，应用启动时由初始化器设为 app.demo-password 的 BCrypt 哈希。
-- seed-counts: ${counts}
-- =====================================================================

SET NAMES utf8mb4;
`)

parts.push(insert('departments', db.departments, {
  columns: ['id', 'name', 'short_name', 'quota_limit']
}))

parts.push(insert('users', db.users, {
  columns: [
    'id', 'username', 'password_hash', 'name', 'role', 'role_label', 'portal', 'preset', 'preset_desc', 'dept_id',
    'is_international', 'visa_type', 'visa_valid', 'visa_expiry', 'language_cert', 'language_level', 'language_valid',
    'max_weekly_hours', 'student_no', 'enrollment_status', 'degree', 'major', 'gpa', 'skills', 'email', 'phone', 'title', 'bio'
  ],
  jsonColumns: ['skills'],
  overrides: { password_hash: () => null },
  defaults: { preset: false, is_international: false }
}))

parts.push(insert('budgets', db.budgets, {
  columns: ['id', 'code', 'source_type', 'source_label', 'dept_id', 'name', 'total_amount', 'used_amount', 'frozen_amount']
}))

const configRows = Object.entries(db.configs).map(([key, value]) => ({ configKey: key, configValue: value }))
parts.push(insert('sys_config', configRows, {
  columns: ['config_key', 'config_value'],
  jsonColumns: ['config_value']
}))

parts.push(insert('jobs', db.jobs, {
  columns: [
    'id', 'type', 'title', 'dept_id', 'mentor_id', 'budget_id', 'quota', 'hired_count', 'hourly_rate', 'weekly_hours',
    'weeks', 'allow_international', 'status', 'workflow_instance_id', 'workflow_steps', 'current_step_index',
    'deadline', 'created_at', 'skills', 'description', 'extra_fields', 'reserved_amount'
  ],
  jsonColumns: ['workflow_steps', 'skills', 'extra_fields'],
  overrides: { reserved_amount: (job) => seedReserved.get(job.id) ?? 0 },
  defaults: { hired_count: 0, allow_international: false, current_step_index: 0 }
}))

parts.push(insert('applications', db.applications, {
  columns: [
    'id', 'job_id', 'student_id', 'status', 'applied_at', 'statement', 'resume_file_name', 'compliance_check_result',
    'is_special_approval', 'special_reason', 'special_status', 'interview_info', 'score', 'mentor_comment'
  ],
  jsonColumns: ['compliance_check_result', 'interview_info'],
  defaults: { is_special_approval: false }
}))

parts.push(insert('contracts', db.contracts, {
  columns: [
    'id', 'application_id', 'student_id', 'job_id', 'dept_id', 'start_date', 'end_date', 'weekly_hours', 'hourly_rate',
    'status', 'signed_at', 'signature_data_url', 'signature_text', 'compliance_agreed'
  ],
  defaults: { compliance_agreed: false }
}))

parts.push(insert('timesheets', db.timesheets, {
  columns: [
    'id', 'contract_id', 'student_id', 'job_id', 'dept_id', 'week_no', 'month', 'hours', 'work_log', 'milestone_ref',
    'status', 'submitted_at', 'mentor_comment'
  ]
}))

parts.push(insert('evaluations', db.evaluations, {
  columns: [
    'id', 'contract_id', 'student_id', 'job_id', 'dept_id', 'period', 'job_type', 'dimensions', 'total_score', 'grade',
    'coefficient', 'renewal_eligible', 'course_eval_sync', 'evaluator_name', 'comment', 'evaluated_at'
  ],
  jsonColumns: ['dimensions', 'course_eval_sync']
}))

parts.push(insert('payrolls', db.payrolls, {
  columns: [
    'id', 'student_id', 'contract_id', 'job_id', 'dept_id', 'month', 'total_hours', 'hourly_rate', 'coefficient',
    'amount', 'budget_id', 'source_type', 'finance_status', 'calculated_at', 'paid_at', 'voucher_no'
  ]
}))

parts.push(insert('notifications', db.notifications, {
  columns: ['id', 'user_id', 'channel', 'title', 'content', 'is_read', 'created_at'],
  overrides: { is_read: (n) => Boolean(n.read) }
}))

parts.push(insert('audit_log', db.auditLogs, {
  columns: [
    'id', 'target_type', 'target_id', 'operator_id', 'operator_name', 'operator_role', 'action', 'opinion',
    'before_value', 'after_value', 'log_time'
  ],
  overrides: { operator_id: () => null, log_time: (l) => l.timestamp }
}))

// P1：岗位模板（dept_id 为空表示全校通用）
parts.push(insert('job_templates', db.jobTemplates || [], {
  columns: ['id', 'name', 'type', 'dept_id', 'payload', 'created_by', 'created_at'],
  jsonColumns: ['payload']
}))

writeFileSync(outPath, parts.join('\n'), 'utf8')
console.log(`已生成 ${path.relative(root, outPath)}（${counts}）`)
