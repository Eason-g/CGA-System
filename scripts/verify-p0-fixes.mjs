// 复现并验证三个 P0 漏洞的修复：node scripts/verify-p0-fixes.mjs
// 可用 SRC_DIR 指向另一份 src（例如修复前的版本）做对照。
import { registerHooks } from 'node:module'
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const srcDir = path.resolve(process.env.SRC_DIR || new URL('../src', import.meta.url).pathname.replace(/^\/(\w:)/, '$1'))
const srcUrl = pathToFileURL(srcDir + path.sep)

// 让 Node 认识 Vite 的 @/ 别名和省略扩展名的相对导入
registerHooks({
  resolve(spec, ctx, next) {
    let target = spec.startsWith('@/') ? new URL(spec.slice(2), srcUrl).href : spec
    if ((spec.startsWith('@/') || spec.startsWith('.')) && !/\.\w+$/.test(spec)) target += '.js'
    return next(target, ctx)
  }
})

const store = new Map()
globalThis.localStorage = {
  getItem: (k) => (store.has(k) ? store.get(k) : null),
  setItem: (k, v) => store.set(k, String(v)),
  removeItem: (k) => store.delete(k)
}

// 显式取 mock 后端（新版 api.js 默认走 http 模式）；旧版只导出 api，退回使用它
const apiModule = await import(new URL('services/api.js', srcUrl).href)
const api = apiModule.mockApi ?? apiModule.api
const { getDB } = await import(new URL('mock/mockDB.js', srcUrl).href)
const rules = await import(new URL('services/ruleEngine.js', srcUrl).href)

let failures = 0
function expect(name, ok, detail = '') {
  console.log(`${ok ? '  PASS' : '  FAIL'}  ${name}${detail ? `  —  ${detail}` : ''}`)
  if (!ok) failures += 1
}

const db = getDB()
const mentor = db.users.find((u) => u.id === 'U_MENTOR')
const schoolAdmin = db.users.find((u) => u.role === 'SCHOOL_ADMIN')
const template = db.jobs.find((j) => j.status === 'PUBLISHED')

function addJob(id, weeklyHours, extra = {}) {
  const job = {
    ...JSON.parse(JSON.stringify(template)),
    id, title: `测试岗位 ${id}`, quota: 5, hiredCount: 0, weeklyHours,
    allowInternational: true, status: 'PUBLISHED', extraFields: {}, ...extra
  }
  db.jobs.push(job)
  return job
}
function addStudent(id, extra = {}) {
  const stu = {
    id, username: id.toLowerCase(), name: id, role: 'STUDENT', roleLabel: '测试学生', portal: 'student',
    deptId: 'DEPT_CS', isInternational: false, visaType: '', visaValid: true,
    languageCert: '', languageLevel: 0, languageValid: false, maxWeeklyHours: 20, skills: [], ...extra
  }
  db.users.push(stu)
  return stu
}

// ---------------------------------------------------------------- 漏洞 1
console.log('\n漏洞 1：录用时复核跨岗累计工时')
const s1 = addStudent('T_MULTI')
const jobs1 = [addJob('T_J1', 8), addJob('T_J2', 8), addJob('T_J3', 8)]
const apps1 = []
for (const job of jobs1) {
  const res = await api.submitApplication({ jobId: job.id, statement: '测试' }, s1)
  apps1.push(res.data)
}
expect('三个 8h 岗位单独申请都能通过（申请时合同工时为 0）', apps1.every((a) => a?.status === 'SUBMITTED'))
const hires = []
for (const app of apps1) {
  hires.push(await api.decideApplication({ applicationId: app.id, score: 90, decision: 'HIRE', comment: '' }, mentor))
}
const allocated1 = rules.getStudentAllocatedWeeklyHours(db, s1.id)
expect('前两次录用成功（8h + 8h = 16h）', hires[0].code === 200 && hires[1].code === 200)
expect('第三次录用被拦截（16h + 8h = 24h > 20h）', hires[2].code === 403, hires[2].message)
expect('合同累计周工时没有超过 20h', allocated1 <= 20, `实际 ${allocated1}h`)
expect('被拦截的申请没有留下评分或状态变化', apps1[2].status === 'SUBMITTED' && apps1[2].score === null && jobs1[2].hiredCount === 0)

// ---------------------------------------------------------------- 漏洞 2
console.log('\n漏洞 2：签证无效的国际生不能提交特批')
const s2 = addStudent('T_NOVISA', { isInternational: true, visaType: 'X1学习居留许可', visaValid: false })
const j2 = addJob('T_J4', 8)
const r2 = await api.submitApplication(
  { jobId: j2.id, statement: '测试', isSpecialApproval: true, specialReason: '签证续办中，申请先行上岗' }, s2
)
expect('直接调接口发起特批被拒绝', r2.code === 403, r2.message)
const forged = { ...s2, visaValid: true }
const r2b = await api.submitApplication(
  { jobId: j2.id, statement: '测试', isSpecialApproval: true, specialReason: '签证续办中，申请先行上岗' }, forged
)
expect('调用方伪造 visaValid: true 也被拒绝（以库中学籍为准）', r2b.code === 403, r2b.message)
expect('库里没有生成该生的申请单', !db.applications.some((a) => a.studentId === s2.id))

const s2ok = addStudent('T_SOFT', { isInternational: true, visaType: 'X1学习居留许可', visaValid: true })
const j2big = addJob('T_J5', 24)
const r2c = await api.submitApplication(
  { jobId: j2big.id, statement: '测试', isSpecialApproval: true, specialReason: '寒假集中排班，学期内平均不超标' }, s2ok
)
expect('签证有效、仅工时超限的国际生可以发起特批（需求：超限需特批留痕）', r2c.code === 200 && r2c.data?.status === 'PENDING_SPECIAL_APPROVAL', r2c.message)
const s2dom = addStudent('T_SOFT_DOM')
const r2d = await api.submitApplication(
  { jobId: j2big.id, statement: '测试', isSpecialApproval: true, specialReason: '申请国内生工时例外' }, s2dom
)
expect('国内生工时超限仍可发起特批', r2d.code === 200 && r2d.data?.status === 'PENDING_SPECIAL_APPROVAL', r2d.message)

// ---------------------------------------------------------------- 漏洞 3
console.log('\n漏洞 3：工时上限按国际生 / 国内生读取配置中心')
const cfg = await api.saveConfigs({ globalRules: { maxWeeklyHoursIntl: 12 } }, schoolAdmin)
expect('配置中心把国际生上限改为 12h', cfg.code === 200 && db.configs.globalRules.maxWeeklyHoursIntl === 12)
const intl = addStudent('T_INTL', { isInternational: true, visaType: 'X1学习居留许可', visaValid: true })
const dom = addStudent('T_DOM')
const j3 = addJob('T_J6', 14)
const ri = await api.submitApplication({ jobId: j3.id, statement: '测试' }, intl)
const rd = await api.submitApplication({ jobId: j3.id, statement: '测试' }, dom)
expect('国际生申请 14h 岗位被拦截（上限 12h）', ri.code === 403, ri.data?.checks?.find((c) => c.key === 'hours')?.detail)
expect('国内生申请同一岗位通过（上限 20h）', rd.code === 200, rd.message)
const ti = rules.validateTimesheetSubmission(db, intl, 99, 13)
const td = rules.validateTimesheetSubmission(db, dom, 99, 13)
expect('国际生单周填报 13h 被拦截', !ti.ok, ti.message)
expect('国内生单周填报 13h 通过', td.ok, td.message)

console.log(failures === 0 ? '\n全部通过' : `\n${failures} 项未通过`)
process.exit(failures === 0 ? 0 : 1)
