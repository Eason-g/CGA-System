import { TODAY_STR } from '@/mock/seedData'

// “今天”：mock 模式固定为种子数据锚定日期；http 模式由 store 在拉取快照后设为后端 meta.today（缺省为系统日期）
let todayStr = TODAY_STR

export function localDateStr(d = new Date()) {
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function setTodayStr(dateStr) {
  todayStr = dateStr || localDateStr()
}

export function getTodayStr() {
  return todayStr
}

export const JOB_STATUS_MAP = {
  DRAFT: { label: '草稿', tone: 'neutral' },
  PENDING_DEPT_REVIEW: { label: '待院系初审', tone: 'caution' },
  PENDING_INTL_REVIEW: { label: '待国际生办会签', tone: 'caution' },
  PENDING_SCHOOL_REVIEW: { label: '待校级终审', tone: 'caution' },
  PUBLISHED: { label: '已正式发布', tone: 'success' },
  REJECTED: { label: '已驳回', tone: 'error' },
  CLOSED: { label: '已招满关闭', tone: 'neutral' }
}

export const APP_STATUS_MAP = {
  SUBMITTED: { label: '待导师筛选', tone: 'blue' },
  PENDING_SPECIAL_APPROVAL: { label: '待国际生办特批', tone: 'caution' },
  SPECIAL_APPROVED: { label: '特批通过·待筛选', tone: 'caution' },
  SPECIAL_REJECTED: { label: '特批驳回', tone: 'error' },
  INTERVIEW_SCHEDULED: { label: '已安排面试', tone: 'blue' },
  HIRED: { label: '已录用', tone: 'success' },
  REJECTED: { label: '未录用', tone: 'error' }
}

export const FINANCE_STATUS_MAP = {
  PENDING_CALC: { label: '待核算', tone: 'neutral' },
  PENDING_PAY: { label: '待财务打款', tone: 'caution' },
  SUBMITTED: { label: '已提交财务', tone: 'blue' },
  PAY_FAILED: { label: '发放失败', tone: 'error' },
  PAID: { label: '已发放', tone: 'success' }
}

// ===== P1 第三批：合规强化（与后端 RuleEngine 同名函数、文案一致）=====

// 签证到期预警天数（scanAnomalies 的“签证 30 天内到期”）
export const VISA_EXPIRY_WARNING_DAYS = 30

function parseDateStr(s) {
  const [y, m, d] = String(s).split('-').map(Number)
  return new Date(y, m - 1, d)
}

// 聘期开始日：今天之后的下一个周一
export function contractStartFrom(fromStr = todayStr) {
  const d = parseDateStr(fromStr)
  const day = d.getDay() // 0=周日
  d.setDate(d.getDate() + ((8 - day) % 7 || 7))
  return localDateStr(d)
}

// 预计聘期结束日：开始日 + 岗位约定周数（至少 1 周）
export function predictContractEnd(weeks, fromStr = todayStr) {
  const d = parseDateStr(contractStartFrom(fromStr))
  d.setDate(d.getDate() + 7 * Math.max(1, Number(weeks) || 0))
  return localDateStr(d)
}

// 岗位默认申请截止日：今天 + 30 天（同后端 JobService.DEFAULT_DEADLINE_DAYS）
export const DEFAULT_DEADLINE_DAYS = 30
export function defaultJobDeadline(fromStr = todayStr) {
  const d = parseDateStr(fromStr)
  d.setDate(d.getDate() + DEFAULT_DEADLINE_DAYS)
  return localDateStr(d)
}

// 签证有效期是否覆盖到 endDate：覆盖返回 null，否则返回拦截文案（不可特批）
export function checkVisaCoverage(student, endDate) {
  if (!student.visaExpiry) {
    return `该国际生未登记签证有效期，无法确认覆盖聘期结束日 ${endDate}，不能录用（不可特批）`
  }
  if (student.visaExpiry < endDate) {
    return `该国际生签证有效期至 ${student.visaExpiry}，早于聘期结束日 ${endDate}，签证须覆盖整个聘期，不能录用（不可特批）`
  }
  return null
}

// 语言证明核验状态文案
export const LANG_CERT_STATUS_MAP = {
  PENDING: { label: '待核验', tone: 'caution' },
  VERIFIED: { label: '已核验', tone: 'success' },
  REJECTED: { label: '核验未通过', tone: 'error' }
}

export const STEP_TO_JOB_STATUS = {
  DEPT_REVIEW: 'PENDING_DEPT_REVIEW',
  INTL_REVIEW: 'PENDING_INTL_REVIEW',
  SCHOOL_REVIEW: 'PENDING_SCHOOL_REVIEW',
  PUBLISHED: 'PUBLISHED'
}

export function calcJobBudgetCost({ quota = 0, hourlyRate = 0, weeklyHours = 0, weeks = 0 }) {
  return Math.max(0, Number(quota) * Number(hourlyRate) * Number(weeklyHours) * Number(weeks))
}

export function getBudgetAvailable(budget) {
  if (!budget) return 0
  return Number(budget.totalAmount) - Number(budget.usedAmount) - Number(budget.frozenAmount)
}

export function getBudgetUtilization(budget) {
  if (!budget || !budget.totalAmount) return 0
  return Math.min(100, Math.round(((Number(budget.usedAmount) + Number(budget.frozenAmount)) / Number(budget.totalAmount)) * 100))
}

export function getDeptQuotaUsage(db, deptId) {
  const dept = db.departments.find((d) => d.id === deptId)
  const used = db.jobs
    .filter((j) => j.deptId === deptId && j.status !== 'REJECTED')
    .reduce((sum, j) => sum + Number(j.quota || 0), 0)
  const limit = dept ? dept.quotaLimit : 20
  return {
    deptName: dept?.name || deptId,
    used,
    limit,
    remaining: Math.max(0, limit - used)
  }
}

export function validateJobPublish(db, form) {
  const budget = db.budgets.find((b) => b.id === form.budgetId)
  const estimatedCost = calcJobBudgetCost(form)
  const availableBudget = getBudgetAvailable(budget)
  const deficit = Math.max(0, estimatedCost - availableBudget)
  const budgetExceeded = !budget || estimatedCost > availableBudget

  const quotaInfo = getDeptQuotaUsage(db, form.deptId)
  const requestedQuota = Number(form.quota || 0)
  const quotaExceeded = requestedQuota > quotaInfo.remaining
  const quotaDeficit = Math.max(0, requestedQuota - quotaInfo.remaining)

  const errors = []
  if (!budget) errors.push('请选择有效的经费来源账号')
  if (budget && budgetExceeded) {
    errors.push(`预计总支出 ¥${estimatedCost.toLocaleString()} 超出可用预算 ¥${deficit.toLocaleString()}，系统已拦截发布`)
  }
  if (quotaExceeded) {
    errors.push(`招聘人数 ${requestedQuota} 人超出院系剩余名额 ${quotaInfo.remaining} 人，系统已拦截发布`)
  }

  return {
    ok: errors.length === 0,
    estimatedCost,
    availableBudget,
    remainingAfter: availableBudget - estimatedCost,
    deficit,
    budgetExceeded,
    budget,
    quotaInfo,
    quotaExceeded,
    quotaDeficit,
    errors
  }
}

export function buildWorkflowStepsForJob(db, jobType, allowInternational) {
  const template = db.configs?.workflows?.[jobType] || []
  const steps = template
    .filter((node) => {
      if (node.conditional === 'allowInternational') {
        return Boolean(allowInternational)
      }
      return true
    })
    .map((node) => node.key)
  if (!steps.includes('PUBLISHED')) {
    steps.push('PUBLISHED')
  }
  return steps
}

// 周工时上限：按国际生/国内生读取配置中心 globalRules，个人上限（学籍/签证给出）只能更严，不能放宽
export function getMaxWeeklyHours(db, student) {
  const rules = db?.configs?.globalRules || {}
  const fallback = Number(rules.maxWeeklyHoursDefault) || 20
  const ruleCap = student?.isInternational ? Number(rules.maxWeeklyHoursIntl) || fallback : fallback
  const personalCap = Number(student?.maxWeeklyHours)
  return personalCap > 0 ? Math.min(personalCap, ruleCap) : ruleCap
}

// 已占周工时 = 生效/待签合同在同一时间叠加的峰值（与后端 RuleEngine 一致）。
// 续聘的新合同从原合同结束次日开始，两者不重叠，不能简单相加；没有起止日期的合同视为一直有效。
export function getStudentAllocatedWeeklyHours(db, studentId) {
  const mine = db.contracts.filter(
    (c) => c.studentId === studentId && (c.status === 'ACTIVE' || c.status === 'PENDING_SIGN')
  )
  let peak = 0
  for (const c of mine) {
    const day = c.startDate || ''
    const load = mine
      .filter((d) => (!d.startDate || d.startDate <= day) && (!d.endDate || d.endDate >= day))
      .reduce((sum, d) => sum + Number(d.weeklyHours || 0), 0)
    peak = Math.max(peak, load)
  }
  return peak
}

// 学生跨岗周工时负荷（展示用）：快照里学生 user 带 hoursSummary（后端按全部合同累计）时优先使用，
// 导师/院系看不到外院合同明细时也能显示真实总负荷；没有时退回按当前可见合同计算
export function getStudentHoursSummary(db, student) {
  const summary = student?.hoursSummary
  if (summary && summary.allocated != null && summary.max != null) {
    return { allocated: Number(summary.allocated) || 0, max: Number(summary.max) || getMaxWeeklyHours(db, student) }
  }
  return {
    allocated: student ? getStudentAllocatedWeeklyHours(db, student.id) : 0,
    max: getMaxWeeklyHours(db, student)
  }
}

export function getStudentActiveContracts(db, studentId) {
  return db.contracts.filter((c) => c.studentId === studentId && (c.status === 'ACTIVE' || c.status === 'PENDING_SIGN'))
}

export function getStudentWeekLoggedHours(db, studentId, weekNo) {
  return db.timesheets
    .filter((t) => t.studentId === studentId && Number(t.weekNo) === Number(weekNo) && t.status !== 'REJECTED')
    .reduce((sum, t) => sum + Number(t.hours || 0), 0)
}

// 学籍状态（P2 B.2）：必须为“在读”，硬性条件不可特批；未登记按在读处理（与后端 RuleEngine 一致）
export const ENROLLMENT_ACTIVE = '在读'
export const ENROLLMENT_STATUSES = ['在读', '休学', '毕业']

export function enrollmentStatusOf(student) {
  const s = student?.enrollmentStatus
  return s && String(s).trim() ? String(s).trim() : ENROLLMENT_ACTIVE
}

// 在读返回 null，否则返回拦截文案
export function checkEnrollment(student) {
  const s = enrollmentStatusOf(student)
  if (s === ENROLLMENT_ACTIVE) return null
  return `当前学籍状态为「${s}」，仅在读学生可申请三助岗位（硬性条件，不可特批）`
}

export function validateStudentApplication(db, student, job) {
  const checks = []
  // 提示性检查项（不影响 passed、不参与特批判断）
  const hints = []
  const existingApp = db.applications.find(
    (a) => a.studentId === student.id && a.jobId === job.id && a.status !== 'REJECTED' && a.status !== 'SPECIAL_REJECTED'
  )
  const quotaFull = Number(job.hiredCount || 0) >= Number(job.quota || 0)

  const currentHours = getStudentAllocatedWeeklyHours(db, student.id)
  const jobHours = Number(job.weeklyHours || 0)
  const projectedHours = currentHours + jobHours
  const maxHours = getMaxWeeklyHours(db, student)

  // 0. 学籍状态：在读时不额外列出，保持原有校验项顺序
  const enrollmentError = checkEnrollment(student)
  if (enrollmentError) {
    checks.push({
      key: 'enrollment',
      label: '学籍状态校验（在读）',
      passed: false,
      overridable: false,
      detail: enrollmentError
    })
  }

  // overridable: false 表示硬性条件，不允许通过特批绕过
  if (student.isInternational) {
    // 1. Visa Check
    const visaOk = Boolean(student.visaValid && student.visaType)
    checks.push({
      key: 'visa',
      label: '签证类型校验（有效学习居留许可）',
      passed: visaOk,
      overridable: false,
      detail: visaOk
        ? `${student.visaType}有效（有效期至 ${student.visaExpiry || '未登记'}）`
        : '未检测到有效学习类居留许可，禁止从事校内助教/助研活动'
    })

    // 2. Job allows international check
    const allowIntlOk = Boolean(job.allowInternational)
    checks.push({
      key: 'allowIntl',
      label: '岗位国际生开放资格校验',
      passed: allowIntlOk,
      overridable: false,
      detail: allowIntlOk
        ? '该岗位已通过国际生办会签，面向国际学生开放'
        : '该岗位未面向国际学生开放（仅限中国籍学生申请）'
    })

    // 3. 签证有效期覆盖预计聘期（提示性，不拦截；录用时 validateHire 硬拦截）
    if (visaOk) {
      const end = predictContractEnd(job.weeks)
      const covered = !checkVisaCoverage(student, end)
      const expiry = student.visaExpiry || '未登记'
      hints.push({
        key: 'visaPeriod',
        label: '签证有效期覆盖聘期（提示）',
        passed: covered,
        overridable: false,
        detail: covered
          ? `签证有效期至 ${expiry}，覆盖预计聘期（至 ${end}）`
          : `签证有效期至 ${expiry}，早于预计聘期结束日 ${end}，录用时将被系统拦截，请尽快办理续签并上传新的签证材料`
      })
    }
  }

  // Language requirement check (if job specifies language score)
  if (job.extraFields?.minLangScore) {
    const reqScore = Number(job.extraFields.minLangScore)
    const stuScore = Number(student.languageLevel || 0)
    const langOk = Boolean(student.languageValid) && stuScore >= reqScore
    // 新上传的语言证明在国际生办核验前视为未通过（可特批）
    const pending = student.languageCertStatus === 'PENDING'
    checks.push({
      key: 'lang',
      label: '语言能力证明校验',
      passed: langOk,
      overridable: true,
      detail: langOk
        ? `已认证 ${student.languageCert}，满足岗位要求（${job.extraFields.languageRequirement}）`
        : pending
        ? `语言证明已提交，待国际生办核验（核验前视为未通过），岗位要求 ${job.extraFields.languageRequirement}`
        : `当前认证 ${student.languageCert || '未提交'}（等效 ${stuScore}），低于岗位最低要求 ${job.extraFields.languageRequirement}`
    })
  }

  // Cross-job cumulative weekly hours check (both intl and domestic)
  const hoursOk = projectedHours <= maxHours
  checks.push({
    key: 'hours',
    label: '跨岗累计周工时校验',
    passed: hoursOk,
    overridable: true,
    detail: hoursOk
      ? `当前已占 ${currentHours}h + 本岗 ${jobHours}h = ${projectedHours}h ≤ 上限 ${maxHours}h/周`
      : `当前已占 ${currentHours}h + 本岗 ${jobHours}h = ${projectedHours}h > 上限 ${maxHours}h/周（超出 ${projectedHours - maxHours}h）`
  })

  const allPassed = checks.every((c) => c.passed) && !existingApp && !quotaFull
  const failedChecks = checks.filter((c) => !c.passed)
  const hardFailures = failedChecks.filter((c) => !c.overridable)
  // 只有可特批项（工时、语言）未通过时才能发起特批；签证、岗位开放资格属于硬性条件
  const canSpecialApprove = !existingApp && !quotaFull && failedChecks.length > 0 && hardFailures.length === 0
  let specialBlockedReason = ''
  if (!canSpecialApprove) {
    if (existingApp) specialBlockedReason = '已申请过该岗位'
    else if (quotaFull) specialBlockedReason = '该岗位名额已招满'
    else if (failedChecks.length === 0) specialBlockedReason = '合规校验已通过，无需特批'
    else specialBlockedReason = `${hardFailures.map((c) => c.label).join('、')}未通过，属于硬性条件，不可特批`
  }

  return {
    passed: allPassed,
    alreadyApplied: Boolean(existingApp),
    existingApp,
    quotaFull,
    checks,
    currentHours,
    jobHours,
    projectedHours,
    maxHours,
    canSpecialApprove,
    specialBlockedReason,
    hints
  }
}

export const HIREABLE_APP_STATUSES = ['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED']

// 录用时复核：申请后学生可能又被其他岗位录用，必须按录用时刻的累计工时重新判断
export function validateHire(db, app, job, student) {
  const errors = []
  if (!HIREABLE_APP_STATUSES.includes(app.status)) {
    errors.push(`申请当前状态为「${APP_STATUS_MAP[app.status]?.label || app.status}」，不能录用`)
  }
  if (Number(job.hiredCount || 0) >= Number(job.quota || 0)) {
    errors.push('该岗位招聘名额已满，无法继续录用')
  }
  // 学籍状态必须为在读（硬性条件，特批也不能放行）
  if (checkEnrollment(student)) {
    errors.push(`该学生当前学籍状态为「${enrollmentStatusOf(student)}」，仅在读学生可录用（不可特批）`)
  }
  if (student.isInternational) {
    if (!(student.visaValid && student.visaType)) errors.push('该国际生无有效学习类居留许可，不能录用')
    if (!job.allowInternational) errors.push('该岗位未面向国际学生开放，不能录用国际生')
    // 签证有效期必须覆盖整个聘期（硬性条件，特批也不能放行）
    if (student.visaValid) {
      const coverageError = checkVisaCoverage(student, predictContractEnd(job.weeks))
      if (coverageError) errors.push(coverageError)
    }
  }

  const currentHours = getStudentAllocatedWeeklyHours(db, student.id)
  const jobHours = Number(job.weeklyHours || 0)
  const projectedHours = currentHours + jobHours
  const maxHours = getMaxWeeklyHours(db, student)
  let hoursOverridden = false
  if (projectedHours > maxHours) {
    // 特批只覆盖审批时看到的超限幅度；之后又多出来的工时需要重新审批
    const approvedCeiling = Number(app.complianceCheckResult?.projectedHours)
    if (app.specialStatus === 'APPROVED' && Number.isFinite(approvedCeiling) && projectedHours <= approvedCeiling) {
      hoursOverridden = true
    } else {
      errors.push(
        `录用后跨岗累计周工时将达 ${projectedHours}h（已占 ${currentHours}h + 本岗 ${jobHours}h），超出上限 ${maxHours}h/周，系统已拦截录用`
      )
    }
  }

  return { ok: errors.length === 0, errors, currentHours, jobHours, projectedHours, maxHours, hoursOverridden }
}

export function validateTimesheetSubmission(db, student, weekNo, newHours) {
  const maxHours = getMaxWeeklyHours(db, student)
  const existingHours = getStudentWeekLoggedHours(db, student.id, weekNo)
  const hoursNum = Number(newHours || 0)
  const projectedHours = existingHours + hoursNum

  if (hoursNum <= 0) {
    return {
      ok: false,
      existingHours,
      projectedHours,
      maxHours,
      message: '填报工时必须大于 0 小时'
    }
  }
  if (projectedHours > maxHours) {
    return {
      ok: false,
      existingHours,
      projectedHours,
      maxHours,
      message: `第 ${weekNo} 周跨岗累计工时将达 ${projectedHours}h（已报 ${existingHours}h + 本次 ${hoursNum}h），超出学校红线 ${maxHours}h/周，系统已拦截提交！`
    }
  }
  return {
    ok: true,
    existingHours,
    projectedHours,
    maxHours,
    message: `校验通过：第 ${weekNo} 周跨岗累计 ${projectedHours}h / ${maxHours}h`
  }
}

export function daysUntil(dateStr, fromStr = todayStr) {
  if (!dateStr) return 999
  const d1 = new Date(fromStr + 'T00:00:00')
  const d2 = new Date(dateStr + 'T00:00:00')
  return Math.ceil((d2 - d1) / (1000 * 60 * 60 * 24))
}

export function filterJobsByRole(db, user) {
  if (!user) return []
  if (user.role === 'SCHOOL_ADMIN') return db.jobs
  if (user.role === 'DEPT_ADMIN') return db.jobs.filter((j) => j.deptId === user.deptId)
  if (user.role === 'MENTOR') return db.jobs.filter((j) => j.mentorId === user.id || j.deptId === user.deptId)
  if (user.role === 'INTL_OFFICER') return db.jobs.filter((j) => j.allowInternational)
  return db.jobs.filter((j) => j.status === 'PUBLISHED')
}

export function filterByRoleDept(db, items, user, type = 'GENERIC') {
  if (!user) return []
  if (user.role === 'SCHOOL_ADMIN') return items
  if (user.role === 'DEPT_ADMIN') {
    return items.filter((item) => {
      if (item.deptId) return item.deptId === user.deptId
      if (item.jobId) {
        const job = db.jobs.find((j) => j.id === item.jobId)
        return job?.deptId === user.deptId
      }
      return true
    })
  }
  if (user.role === 'MENTOR') {
    return items.filter((item) => {
      if (type === 'BUDGET') return item.deptId === user.deptId || item.sourceType === 'GA_FUND'
      const job = item.jobId ? db.jobs.find((j) => j.id === item.jobId) : null
      if (job) return job.mentorId === user.id
      if (item.mentorId) return item.mentorId === user.id
      return item.deptId === user.deptId
    })
  }
  if (user.role === 'INTL_OFFICER') {
    return items.filter((item) => {
      if (item.isSpecialApproval) return true
      if (item.studentId) {
        const stu = db.users.find((u) => u.id === item.studentId)
        if (stu?.isInternational) return true
      }
      if (item.jobId) {
        const job = db.jobs.find((j) => j.id === item.jobId)
        if (job?.allowInternational) return true
      }
      if (item.allowInternational) return true
      return false
    })
  }
  // Student sees own
  return items.filter((item) => item.studentId === user.id)
}

export function scanAnomalies(db, user) {
  const warnings = []
  const currentWeek = db.meta?.currentWeekNo || 12
  const expiryDaysThreshold = db.configs?.globalRules?.expiryWarningDays ?? 15
  const cautionRatio = db.configs?.globalRules?.cautionHoursRatio ?? 0.8

  const visibleContracts = filterByRoleDept(db, db.contracts, user, 'CONTRACT').filter((c) => c.status === 'ACTIVE')

  // Group by student for hours warning
  const studentSet = new Set(visibleContracts.map((c) => c.studentId))
  studentSet.forEach((studentId) => {
    const stu = db.users.find((u) => u.id === studentId)
    if (!stu) return
    const maxH = getMaxWeeklyHours(db, stu)
    const weekLogged = getStudentWeekLoggedHours(db, studentId, currentWeek)
    const allocated = getStudentAllocatedWeeklyHours(db, studentId)
    const peak = Math.max(weekLogged, allocated)
    const ratio = peak / maxH

    if (peak > maxH) {
      warnings.push({
        id: `WARN_HOUR_OVER_${studentId}`,
        category: 'HOURS_LIMIT',
        severity: 'error',
        title: `${stu.name} 周工时已超限`,
        detail: `第 ${currentWeek} 周实报 ${weekLogged}h / 排班 ${allocated}h，超出红线 ${maxH}h/周`,
        studentId,
        isInternational: stu.isInternational,
        deptId: stu.deptId
      })
    } else if (ratio >= cautionRatio) {
      warnings.push({
        id: `WARN_HOUR_NEAR_${studentId}`,
        category: 'HOURS_LIMIT',
        severity: 'caution',
        title: `${stu.name} 周工时临近上限（${Math.round(ratio * 100)}%）`,
        detail: `第 ${currentWeek} 周已报 ${weekLogged}h / 合同周工时 ${allocated}h（上限 ${maxH}h/周）`,
        studentId,
        isInternational: stu.isInternational,
        deptId: stu.deptId
      })
    }
  })

  // Per contract checks: missing timesheet & expiring soon
  visibleContracts.forEach((con) => {
    const stu = db.users.find((u) => u.id === con.studentId)
    const job = db.jobs.find((j) => j.id === con.jobId)
    if (!stu || !job) return

    const hasWeekTs = db.timesheets.some(
      (t) => t.contractId === con.id && Number(t.weekNo) === currentWeek && t.status !== 'REJECTED'
    )
    if (!hasWeekTs) {
      warnings.push({
        id: `WARN_MISS_TS_${con.id}`,
        category: 'MISSING_TIMESHEET',
        severity: 'caution',
        title: `${stu.name} 第 ${currentWeek} 周尚未填报工时`,
        detail: `在岗岗位：${job.title}（约定 ${con.weeklyHours}h/周）`,
        studentId: stu.id,
        contractId: con.id,
        isInternational: stu.isInternational,
        deptId: con.deptId
      })
    }

    const leftDays = daysUntil(con.endDate)
    if (leftDays <= expiryDaysThreshold) {
      warnings.push({
        id: `WARN_EXP_${con.id}`,
        category: 'EXPIRING_SOON',
        severity: leftDays <= 7 ? 'error' : 'caution',
        title: `${stu.name} 聘期距到期仅剩 ${Math.max(0, leftDays)} 天`,
        detail: `岗位「${job.title}」将于 ${con.endDate} 到期，请及时发起考核或续聘`,
        studentId: stu.id,
        contractId: con.id,
        isInternational: stu.isInternational,
        deptId: con.deptId
      })
    }
  })

  // 签证 30 天内到期（P1 第三批，与后端 RuleEngine.isVisaExpiringSoon 一致）：
  // 在岗/待签国际生；国际生办与校级管理员另看全部可见国际生
  const visaStudentIds = new Set(
    filterByRoleDept(db, db.contracts, user, 'CONTRACT')
      .filter((c) => c.status === 'ACTIVE' || c.status === 'PENDING_SIGN')
      .map((c) => c.studentId)
  )
  if (user?.role === 'INTL_OFFICER' || user?.role === 'SCHOOL_ADMIN') {
    db.users.filter((u) => u.role === 'STUDENT' && u.isInternational).forEach((u) => visaStudentIds.add(u.id))
  }
  visaStudentIds.forEach((studentId) => {
    const stu = db.users.find((u) => u.id === studentId)
    if (!stu?.isInternational || !stu.visaExpiry) return
    const leftDays = daysUntil(stu.visaExpiry)
    if (leftDays > VISA_EXPIRY_WARNING_DAYS) return
    warnings.push({
      id: `WARN_VISA_${studentId}`,
      category: 'VISA_EXPIRING',
      severity: leftDays <= 7 ? 'error' : 'caution',
      title: leftDays < 0 ? `${stu.name} 签证已过期` : `${stu.name} 签证 ${leftDays} 天内到期`,
      detail: `签证有效期至 ${stu.visaExpiry}，到期后不得继续从事三助工作，请提醒学生办理续签并上传新的签证材料`,
      studentId,
      isInternational: true,
      deptId: stu.deptId
    })
  })

  return warnings
}

export function calcSkillMatch(student, job) {
  if (!student || !Array.isArray(student.skills) || !Array.isArray(job.skills) || job.skills.length === 0) {
    return { score: 60, matched: [] }
  }
  const stuSkills = new Set(student.skills.map((s) => s.toLowerCase()))
  const matched = job.skills.filter((s) => stuSkills.has(s.toLowerCase()))
  let score = Math.round((matched.length / job.skills.length) * 80) + 20
  if (student.deptId === job.deptId) score = Math.min(100, score + 10)
  return { score, matched }
}

export function gradeFromScore(score) {
  const s = Number(score || 0)
  if (s >= 90) return { grade: '优秀', coefficient: 1.1, renewalEligible: true, tone: 'success' }
  if (s >= 60) return { grade: '合格', coefficient: 1.0, renewalEligible: true, tone: 'blue' }
  return { grade: '不合格', coefficient: 0.85, renewalEligible: false, tone: 'error' }
}
