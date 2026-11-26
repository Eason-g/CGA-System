import { TODAY_STR } from '@/mock/seedData'

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
  PAID: { label: '已发放', tone: 'success' }
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

export function getStudentAllocatedWeeklyHours(db, studentId) {
  return db.contracts
    .filter((c) => c.studentId === studentId && (c.status === 'ACTIVE' || c.status === 'PENDING_SIGN'))
    .reduce((sum, c) => sum + Number(c.weeklyHours || 0), 0)
}

export function getStudentActiveContracts(db, studentId) {
  return db.contracts.filter((c) => c.studentId === studentId && (c.status === 'ACTIVE' || c.status === 'PENDING_SIGN'))
}

export function getStudentWeekLoggedHours(db, studentId, weekNo) {
  return db.timesheets
    .filter((t) => t.studentId === studentId && Number(t.weekNo) === Number(weekNo) && t.status !== 'REJECTED')
    .reduce((sum, t) => sum + Number(t.hours || 0), 0)
}

export function validateStudentApplication(db, student, job) {
  const checks = []
  const existingApp = db.applications.find(
    (a) => a.studentId === student.id && a.jobId === job.id && a.status !== 'REJECTED' && a.status !== 'SPECIAL_REJECTED'
  )
  const quotaFull = Number(job.hiredCount || 0) >= Number(job.quota || 0)

  const currentHours = getStudentAllocatedWeeklyHours(db, student.id)
  const jobHours = Number(job.weeklyHours || 0)
  const projectedHours = currentHours + jobHours
  const maxHours = Number(student.maxWeeklyHours || db.configs.globalRules.maxWeeklyHoursDefault || 20)

  if (student.isInternational) {
    // 1. Visa Check
    const visaOk = Boolean(student.visaValid && student.visaType)
    checks.push({
      key: 'visa',
      label: '签证类型校验（有效学习居留许可）',
      passed: visaOk,
      detail: visaOk
        ? `${student.visaType}有效（有效期至 ${student.visaExpiry || '2027-07-31'}）`
        : '未检测到有效学习类居留许可，禁止从事校内助教/助研活动'
    })

    // 2. Job allows international check
    const allowIntlOk = Boolean(job.allowInternational)
    checks.push({
      key: 'allowIntl',
      label: '岗位国际生开放资格校验',
      passed: allowIntlOk,
      detail: allowIntlOk
        ? '该岗位已通过国际生办会签，面向国际学生开放'
        : '该岗位未面向国际学生开放（仅限中国籍学生申请）'
    })
  }

  // Language requirement check (if job specifies language score)
  if (job.extraFields?.minLangScore) {
    const reqScore = Number(job.extraFields.minLangScore)
    const stuScore = Number(student.languageLevel || 0)
    const langOk = Boolean(student.languageValid) && stuScore >= reqScore
    checks.push({
      key: 'lang',
      label: '语言能力证明校验',
      passed: langOk,
      detail: langOk
        ? `已认证 ${student.languageCert}，满足岗位要求（${job.extraFields.languageRequirement}）`
        : `当前认证 ${student.languageCert || '未提交'}（等效 ${stuScore}），低于岗位最低要求 ${job.extraFields.languageRequirement}`
    })
  }

  // Cross-job cumulative weekly hours check (both intl and domestic)
  const hoursOk = projectedHours <= maxHours
  checks.push({
    key: 'hours',
    label: '跨岗累计周工时校验',
    passed: hoursOk,
    detail: hoursOk
      ? `当前已占 ${currentHours}h + 本岗 ${jobHours}h = ${projectedHours}h ≤ 上限 ${maxHours}h/周`
      : `当前已占 ${currentHours}h + 本岗 ${jobHours}h = ${projectedHours}h > 上限 ${maxHours}h/周（超出 ${projectedHours - maxHours}h）`
  })

  const allPassed = checks.every((c) => c.passed) && !existingApp && !quotaFull
  const hasFailedCompliance = checks.some((c) => !c.passed)
  // Special approval can be initiated when blocked by compliance (hours or language), provided not duplicate or full
  const canSpecialApprove = !existingApp && !quotaFull && hasFailedCompliance && (!student.isInternational || student.visaValid)

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
    canSpecialApprove
  }
}

export function validateTimesheetSubmission(db, student, weekNo, newHours) {
  const maxHours = Number(student.maxWeeklyHours || 20)
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

export function daysUntil(dateStr, fromStr = TODAY_STR) {
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
    const maxH = Number(stu.maxWeeklyHours || 20)
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
