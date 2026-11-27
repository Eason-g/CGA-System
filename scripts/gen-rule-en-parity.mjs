// 规则引擎中英文案一致性：用前端 src/services/ruleEngine.js 计算一组典型输入的输出，
// 写入 server/src/test/resources/rule-en-parity.json，后端 RuleEngineEnParityTest 用同样的输入调用 Java RuleEngine，
// 逐字比较 label/labelEn/detail/detailEn、errors/errorsEn、message/messageEn 等（docs/双语设计.md 第 3 节）。
//
//   node scripts/gen-rule-en-parity.mjs          重新生成期望值（改了 JS 文案后执行，再跑 mvn test 核对 Java）
//   node scripts/gen-rule-en-parity.mjs --check  只核对：JS 当前输出与 JSON 不一致时退出码 1
import { registerHooks } from 'node:module'
import { pathToFileURL, fileURLToPath } from 'node:url'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcUrl = pathToFileURL(path.join(root, 'src') + path.sep)
const outPath = path.join(root, 'server', 'src', 'test', 'resources', 'rule-en-parity.json')

// 让 Node 认识 Vite 的 @/ 别名和省略扩展名的导入（同 verify-p0-fixes.mjs）
registerHooks({
  resolve(spec, ctx, next) {
    let target = spec.startsWith('@/') ? new URL(spec.slice(2), srcUrl).href : spec
    const fromSrc = spec.startsWith('@/') || (spec.startsWith('.') && (ctx.parentURL || '').startsWith(srcUrl.href))
    if (fromSrc && !/\.\w+$/.test(spec)) {
      const base = spec.startsWith('@/') ? target : new URL(spec, ctx.parentURL).href
      target = existsSync(fileURLToPath(base + '.js')) ? base + '.js' : base + '/index.js'
    }
    return next(target, ctx)
  }
})

const rules = await import(new URL('services/ruleEngine.js', srcUrl).href)

const TODAY = '2026-11-20'
const RULES = { maxWeeklyHoursDefault: 20, maxWeeklyHoursIntl: 20 }

const student = (over = {}) => ({
  id: 'S1', isInternational: false, visaType: '', visaValid: true, visaExpiry: null,
  languageCert: 'IELTS 7.0', languageLevel: 7, languageValid: true, languageCertStatus: 'VERIFIED',
  maxWeeklyHours: 20, enrollmentStatus: '在读', ...over
})
const intl = (over = {}) => student({ isInternational: true, visaType: 'X1 student residence permit', visaExpiry: '2027-07-31', ...over })
const job = (over = {}) => ({
  id: 'J1', deptId: 'DEPT_CS', quota: 2, hiredCount: 0, weeklyHours: 8, weeks: 16, allowInternational: true,
  workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'INTL_REVIEW', 'PUBLISHED'], currentStepIndex: 3,
  deadline: '2026-12-31', extraFields: {}, ...over
})
const contract = (weeklyHours, over = {}) => ({
  studentId: 'S1', status: 'ACTIVE', startDate: '2026-11-02', endDate: '2027-02-22', weeklyHours, ...over
})
const langJob = (over = {}) => job({ extraFields: { minLangScore: 7, languageRequirement: 'IELTS ≥ 7.0' }, ...over })

// 每个用例：fn 为函数名，input 为 Java 端同样能构造的输入
const cases = [
  { name: '国际生跨岗工时超限（可特批）', fn: 'application',
    input: { student: intl(), job: job({ weeklyHours: 14 }), contracts: [contract(12)], applications: [] } },
  { name: '国际生签证无效且岗位未开放（硬性条件）', fn: 'application',
    input: { student: intl({ visaValid: false }), job: job({ allowInternational: false, workflowSteps: ['MENTOR_SUBMIT', 'DEPT_REVIEW', 'PUBLISHED'], currentStepIndex: 2 }), contracts: [], applications: [] } },
  { name: '签证早于聘期结束、语言不达标', fn: 'application',
    input: { student: intl({ visaExpiry: '2027-01-15', languageCert: 'IELTS 6.0', languageLevel: 6 }), job: langJob(), contracts: [], applications: [] } },
  { name: '语言证明待核验', fn: 'application',
    input: { student: intl({ languageValid: false, languageCertStatus: 'PENDING' }), job: langJob(), contracts: [], applications: [] } },
  { name: '语言未提交', fn: 'application',
    input: { student: intl({ languageCert: '', languageLevel: 0, languageValid: false, languageCertStatus: null }), job: langJob(), contracts: [], applications: [] } },
  { name: '签证有效期未登记', fn: 'application',
    input: { student: intl({ visaExpiry: null }), job: langJob(), contracts: [], applications: [] } },
  { name: '休学学生且已过截止日', fn: 'application',
    input: { student: student({ enrollmentStatus: '休学' }), job: job({ deadline: '2026-11-01' }), contracts: [], applications: [] } },
  { name: '已申请过', fn: 'application',
    input: { student: student(), job: job(), contracts: [], applications: [{ studentId: 'S1', jobId: 'J1', status: 'SUBMITTED' }] } },
  { name: '名额已满', fn: 'application',
    input: { student: student(), job: job({ hiredCount: 2 }), contracts: [], applications: [] } },
  { name: '全部通过', fn: 'application',
    input: { student: intl(), job: langJob(), contracts: [contract(8)], applications: [] } },

  { name: '录用：状态、名额、学籍、签证、开放资格', fn: 'hire',
    input: { student: intl({ visaValid: false, enrollmentStatus: '毕业' }), job: job({ hiredCount: 2, allowInternational: false }), contracts: [],
      app: { studentId: 'S1', jobId: 'J1', status: 'HIRED' } } },
  { name: '录用：签证未覆盖聘期', fn: 'hire',
    input: { student: intl({ visaExpiry: '2027-01-15' }), job: job(), contracts: [], app: { studentId: 'S1', jobId: 'J1', status: 'SUBMITTED' } } },
  { name: '录用：签证有效期未登记', fn: 'hire',
    input: { student: intl({ visaExpiry: null }), job: job(), contracts: [], app: { studentId: 'S1', jobId: 'J1', status: 'INTERVIEW_SCHEDULED' } } },
  { name: '录用：跨岗工时超限', fn: 'hire',
    input: { student: student(), job: job({ weeklyHours: 10 }), contracts: [contract(12)], app: { studentId: 'S1', jobId: 'J1', status: 'SUBMITTED' } } },
  { name: '录用：特批覆盖超限', fn: 'hire',
    input: { student: student(), job: job({ weeklyHours: 10 }), contracts: [contract(12)],
      app: { studentId: 'S1', jobId: 'J1', status: 'SPECIAL_APPROVED', specialStatus: 'APPROVED', complianceCheckResult: { projectedHours: 22 } } } },

  { name: '工时填报：为 0', fn: 'timesheet', input: { student: student(), weekNo: 12, hours: 0, timesheets: [] } },
  { name: '工时填报：超限', fn: 'timesheet',
    input: { student: intl(), weekNo: 12, hours: 10,
      timesheets: [{ studentId: 'S1', weekNo: 12, hours: 10.5, status: 'APPROVED' }, { studentId: 'S1', weekNo: 12, hours: 4, status: 'REJECTED' }] } },
  { name: '工时填报：通过', fn: 'timesheet',
    input: { student: student(), weekNo: 3, hours: 7.5, timesheets: [{ studentId: 'S1', weekNo: 3, hours: 6, status: 'PENDING' }] } },

  { name: '建岗：超预算且超名额', fn: 'jobPublish',
    input: { form: { deptId: 'DEPT_CS', budgetId: 'B1', quota: 3, hourlyRate: 45, weeklyHours: 8, weeks: 16 },
      budget: { id: 'B1', totalAmount: 18000, usedAmount: 2000, frozenAmount: 1000 },
      dept: { id: 'DEPT_CS', name: '计算机科学与技术学院', quotaLimit: 4 },
      jobs: [{ deptId: 'DEPT_CS', quota: 2, status: 'PUBLISHED' }, { deptId: 'DEPT_CS', quota: 5, status: 'REJECTED' }] } },
  { name: '建岗：未选经费', fn: 'jobPublish',
    input: { form: { deptId: 'DEPT_CS', budgetId: 'NONE', quota: 1, hourlyRate: 40, weeklyHours: 8, weeks: 10 },
      budget: null, dept: { id: 'DEPT_CS', name: '计算机科学与技术学院', quotaLimit: 10 }, jobs: [] } },

  { name: '履约资格：休学', fn: 'eligibility', input: { student: student({ enrollmentStatus: '休学' }), through: '2026-11-30' } },
  { name: '履约资格：无有效签证', fn: 'eligibility', input: { student: intl({ visaValid: false }), through: '2026-11-30' } },
  { name: '履约资格：签证未覆盖', fn: 'eligibility', input: { student: intl({ visaExpiry: '2026-11-25' }), through: '2026-11-30' } },
  { name: '履约资格：通过', fn: 'eligibility', input: { student: intl(), through: '2026-11-30' } }
]

const pickCheck = (c) => ({ key: c.key, label: c.label, labelEn: c.labelEn, passed: c.passed, detail: c.detail, detailEn: c.detailEn })

function run({ fn, input }) {
  const base = { meta: { today: TODAY }, configs: { globalRules: RULES }, applications: [], contracts: [], timesheets: [], jobs: [], budgets: [], departments: [] }
  if (fn === 'application') {
    const r = rules.validateStudentApplication({ ...base, applications: input.applications, contracts: input.contracts }, input.student, input.job)
    return { passed: r.passed, canSpecialApprove: r.canSpecialApprove, checks: r.checks.map(pickCheck), hints: r.hints.map(pickCheck),
      specialBlockedReason: r.specialBlockedReason, specialBlockedReasonEn: r.specialBlockedReasonEn }
  }
  if (fn === 'hire') {
    const r = rules.validateHire({ ...base, contracts: input.contracts }, input.app, input.job, input.student)
    return { ok: r.ok, hoursOverridden: r.hoursOverridden, errors: r.errors, errorsEn: r.errorsEn }
  }
  if (fn === 'timesheet') {
    const r = rules.validateTimesheetSubmission({ ...base, timesheets: input.timesheets }, input.student, input.weekNo, input.hours)
    return { ok: r.ok, message: r.message, messageEn: r.messageEn }
  }
  if (fn === 'jobPublish') {
    const db = { ...base, budgets: input.budget ? [input.budget] : [], departments: [input.dept], jobs: input.jobs }
    const r = rules.validateJobPublish(db, input.form)
    return { ok: r.ok, errors: r.errors, errorsEn: r.errorsEn }
  }
  if (fn === 'eligibility') {
    return { zh: rules.checkWorkingEligibility(input.student, input.through), en: rules.checkWorkingEligibilityEn(input.student, input.through),
      enrollment: rules.checkEnrollment(input.student), enrollmentEn: rules.checkEnrollmentEn(input.student) }
  }
  throw new Error(`未知用例类型 ${fn}`)
}

const fixture = {
  comment: '由 scripts/gen-rule-en-parity.mjs 从 src/services/ruleEngine.js 生成，请勿手改；RuleEngineEnParityTest 用它核对 Java 版文案',
  today: TODAY,
  globalRules: RULES,
  cases: cases.map((c) => ({ ...c, expected: run(c) }))
}
const text = JSON.stringify(fixture, null, 2) + '\n'

if (process.argv.includes('--check')) {
  const current = existsSync(outPath) ? readFileSync(outPath, 'utf8').replace(/\r\n/g, '\n') : ''
  if (current !== text) {
    console.error(`JS 规则引擎输出与 ${path.relative(root, outPath)} 不一致，请执行 node scripts/gen-rule-en-parity.mjs 后再跑 mvn test`)
    process.exit(1)
  }
  console.log(`JS 规则引擎输出与 ${path.relative(root, outPath)} 一致（${cases.length} 个用例）`)
} else {
  writeFileSync(outPath, text)
  console.log(`已生成 ${path.relative(root, outPath)}（${cases.length} 个用例）`)
}
