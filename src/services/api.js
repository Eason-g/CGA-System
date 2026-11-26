import { getDB, persistDB, resetDB, generateId, nowTimestamp, addAuditLog } from '@/mock/mockDB'
import {
  validateJobPublish,
  buildWorkflowStepsForJob,
  STEP_TO_JOB_STATUS,
  validateStudentApplication,
  validateTimesheetSubmission,
  validateHire,
  calcJobBudgetCost,
  gradeFromScore,
  contractStartFrom,
  predictContractEnd,
  defaultJobDeadline
} from './ruleEngine'
import { http, isHttpMode, download } from './http'
import { deriveExtraFieldsForMock } from './jobSchema'

// Simulate 150ms ~ 280ms backend latency with standard { code, data, message } response envelope
function mockAsync(executor) {
  const delay = 150 + Math.floor(Math.random() * 130)
  return new Promise((resolve) => {
    setTimeout(() => {
      try {
        const result = executor(getDB())
        if (result && typeof result === 'object' && 'code' in result) {
          resolve(result)
        } else {
          resolve({ code: 200, data: result, message: '操作成功' })
        }
      } catch (err) {
        resolve({ code: 500, data: null, message: err?.message || '系统内部错误' })
      }
    }, delay)
  })
}

// ===== mock 模式：直接读写 localStorage 中的演示数据库（保留，VITE_API_MODE=mock 时使用）=====
const mockApi = {
  // Fetch full reactive DB snapshot
  fetchSnapshot() {
    return mockAsync((db) => JSON.parse(JSON.stringify(db)))
  },

  resetDemoData() {
    return mockAsync(() => {
      const fresh = resetDB()
      return { code: 200, data: JSON.parse(JSON.stringify(fresh)), message: '已重置为初始演示数据' }
    })
  },

  loginByUserId(userId) {
    return mockAsync((db) => {
      const user = db.users.find((u) => u.id === userId)
      if (!user) return { code: 404, data: null, message: '演示账号不存在' }
      return { code: 200, data: user, message: `已通过统一身份认证登录：${user.name}` }
    })
  },

  loginByCredentials(account) {
    return mockAsync((db) => {
      const q = (account || '').trim().toLowerCase()
      const user = db.users.find(
        (u) =>
          (u.username && u.username.toLowerCase() === q) ||
          (u.studentNo && u.studentNo.toLowerCase() === q) ||
          (u.email && u.email.toLowerCase() === q) ||
          (u.phone && u.phone.replace(/-/g, '') === q.replace(/-/g, '')) ||
          (u.name && u.name.toLowerCase() === q)
      )
      if (!user) return { code: 401, data: null, message: '学工号或校园邮箱未匹配，请核对后重试（或点击右下角查看体验账号）' }
      return { code: 200, data: user, message: `欢迎回来，${user.name}` }
    })
  },

  // P0-1: Create & submit a new TA/RA/GA job with hard budget & quota checks
  createJob(payload, operator) {
    return mockAsync((db) => {
      const check = validateJobPublish(db, payload)
      if (!check.ok) {
        return { code: 400, data: check, message: check.errors.join('；') }
      }

      const steps = buildWorkflowStepsForJob(db, payload.type, payload.allowInternational)
      // Step 0 is MENTOR_SUBMIT, advance to Step 1
      const nextIndex = Math.min(1, steps.length - 1)
      const nextStepKey = steps[nextIndex]
      const initialStatus = STEP_TO_JOB_STATUS[nextStepKey] || 'PUBLISHED'

      const newJob = {
        id: generateId('JOB'),
        type: payload.type,
        title: payload.title.trim(),
        deptId: payload.deptId,
        mentorId: operator.id,
        budgetId: payload.budgetId,
        quota: Number(payload.quota),
        hiredCount: 0,
        hourlyRate: Number(payload.hourlyRate),
        weeklyHours: Number(payload.weeklyHours),
        weeks: Number(payload.weeks),
        allowInternational: Boolean(payload.allowInternational),
        status: initialStatus,
        workflowInstanceId: generateId('WF'),
        workflowSteps: steps,
        currentStepIndex: nextIndex,
        deadline: payload.deadline || defaultJobDeadline(),
        createdAt: nowTimestamp(),
        skills: payload.skills || [],
        description: payload.description || '',
        // 派生字段（TA 语言要求、RA 里程碑）在 http 模式由后端生成，这里模拟同样的规则
        extraFields: deriveExtraFieldsForMock(payload.type, payload.extraFields, {
          weeks: payload.weeks,
          deadline: payload.deadline || defaultJobDeadline()
        })
      }

      // Freeze estimated budget in selected budget pool
      const budget = db.budgets.find((b) => b.id === payload.budgetId)
      const cost = calcJobBudgetCost(newJob)
      const beforeFrozen = budget.frozenAmount
      budget.frozenAmount += cost

      db.jobs.unshift(newJob)
      addAuditLog(db, {
        targetType: 'JOB',
        targetId: newJob.id,
        operator,
        action: '提交岗位发布申请',
        opinion: `发布 ${newJob.type} 岗位「${newJob.title}」，招聘 ${newJob.quota} 人，冻结预计预算 ¥${cost.toLocaleString()}。`,
        beforeValue: `冻结经费 ¥${beforeFrozen.toLocaleString()}`,
        afterValue: `${initialStatus}（冻结经费 ¥${budget.frozenAmount.toLocaleString()}）`
      })
      persistDB()
      return { code: 200, data: newJob, message: '岗位已提交并进入审批流' }
    })
  },

  // Approve or reject job in workflow
  reviewJob({ jobId, decision, opinion }, operator) {
    return mockAsync((db) => {
      const job = db.jobs.find((j) => j.id === jobId)
      if (!job) return { code: 404, data: null, message: '岗位不存在' }

      const beforeStatus = job.status
      if (decision === 'REJECT') {
        job.status = 'REJECTED'
        // Release frozen budget
        const budget = db.budgets.find((b) => b.id === job.budgetId)
        if (budget) {
          budget.frozenAmount = Math.max(0, budget.frozenAmount - calcJobBudgetCost(job))
        }
        addAuditLog(db, {
          targetType: 'JOB',
          targetId: job.id,
          operator,
          action: '驳回岗位申请',
          opinion: opinion || '审核未通过，退回修改并释放冻结预算',
          beforeValue: beforeStatus,
          afterValue: 'REJECTED'
        })
      } else {
        const nextIdx = Math.min((job.currentStepIndex || 1) + 1, job.workflowSteps.length - 1)
        job.currentStepIndex = nextIdx
        const stepKey = job.workflowSteps[nextIdx]
        job.status = STEP_TO_JOB_STATUS[stepKey] || 'PUBLISHED'

        const actionMap = {
          DEPT_ADMIN: '院系初审通过',
          INTL_OFFICER: '国际生办会签通过',
          SCHOOL_ADMIN: '校级终审通过'
        }
        addAuditLog(db, {
          targetType: 'JOB',
          targetId: job.id,
          operator,
          action: actionMap[operator.role] || '审批通过',
          opinion: opinion || '核验无误，同意进入下一流程节点',
          beforeValue: beforeStatus,
          afterValue: job.status
        })
      }

      persistDB()
      return { code: 200, data: job, message: decision === 'REJECT' ? '已驳回该岗位申请' : '审批通过并已流转' }
    })
  },

  // P0-2: Student applies for a job (with hard compliance check & special approval branch)
  submitApplication({ jobId, statement, resumeFileName, isSpecialApproval = false, specialReason = '' }, studentRef) {
    return mockAsync((db) => {
      const job = db.jobs.find((j) => j.id === jobId)
      if (!job) return { code: 404, data: null, message: '岗位不存在' }
      // 以库中学籍为准，不信任调用方传入的签证、工时等字段
      const student = db.users.find((u) => u.id === studentRef?.id && u.role === 'STUDENT')
      if (!student) return { code: 403, data: null, message: '仅学生本人可提交岗位申请' }
      if (job.status !== 'PUBLISHED') return { code: 400, data: null, message: '该岗位未发布或已关闭，不能申请' }

      const check = validateStudentApplication(db, student, job)
      if (check.alreadyApplied) {
        return { code: 400, data: check, message: '您已申请过该岗位，请勿重复投递' }
      }
      if (check.quotaFull) {
        return { code: 400, data: check, message: '该岗位名额已招满' }
      }
      if (!check.passed && !isSpecialApproval) {
        return { code: 403, data: check, message: '合规前置校验未通过，系统已拦截提交' }
      }
      if (isSpecialApproval && !check.canSpecialApprove) {
        return { code: 403, data: check, message: `不能发起特批：${check.specialBlockedReason}` }
      }
      if (isSpecialApproval && (!specialReason || specialReason.trim().length < 6)) {
        return { code: 400, data: check, message: '发起特批申请必须填写不少于 6 个字的特批理由' }
      }

      const app = {
        id: generateId('APP'),
        jobId: job.id,
        studentId: student.id,
        status: isSpecialApproval ? 'PENDING_SPECIAL_APPROVAL' : 'SUBMITTED',
        appliedAt: nowTimestamp(),
        statement: statement || '已认真阅读岗位职责要求，申请加入团队。',
        resumeFileName: resumeFileName || `${student.name}_个人简历.pdf`,
        // projectedHours 记录申请时的累计工时，特批通过后录用复核以此为上限
        complianceCheckResult: {
          passed: check.passed,
          checks: check.checks,
          currentHours: check.currentHours,
          projectedHours: check.projectedHours,
          maxHours: check.maxHours
        },
        isSpecialApproval: Boolean(isSpecialApproval),
        specialReason: isSpecialApproval ? specialReason.trim() : '',
        specialStatus: isSpecialApproval ? 'PENDING' : null,
        interviewInfo: null,
        score: null,
        mentorComment: ''
      }

      db.applications.unshift(app)
      addAuditLog(db, {
        targetType: 'APPLICATION',
        targetId: app.id,
        operator: student,
        action: isSpecialApproval ? '发起国际生办特批申请' : '提交岗位申请',
        opinion: isSpecialApproval
          ? `触发硬校验拦截，特批理由：${app.specialReason}`
          : `通过合规校验并申请「${job.title}」`,
        beforeValue: isSpecialApproval ? '合规拦截' : '未申请',
        afterValue: app.status
      })

      persistDB()
      return {
        code: 200,
        data: app,
        message: isSpecialApproval ? '已提交特批申请，等待国际生办审核留痕' : '投递成功，等待导师筛选'
      }
    })
  },

  // International Officer reviews special approval application
  reviewSpecialApproval({ applicationId, decision, opinion }, operator) {
    return mockAsync((db) => {
      const app = db.applications.find((a) => a.id === applicationId)
      if (!app) return { code: 404, data: null, message: '申请单不存在' }

      const beforeStatus = app.status
      if (decision === 'APPROVE') {
        app.status = 'SPECIAL_APPROVED'
        app.specialStatus = 'APPROVED'
      } else {
        app.status = 'SPECIAL_REJECTED'
        app.specialStatus = 'REJECTED'
      }

      const job = db.jobs.find((j) => j.id === app.jobId)
      db.notifications.unshift({
        id: generateId('NOTIF'),
        userId: app.studentId,
        channel: '站内信 + 模拟邮件',
        title: `特批申请${decision === 'APPROVE' ? '已批准' : '未通过'}：${job?.title || ''}`,
        content: opinion || (decision === 'APPROVE' ? '国际合作交流处已批准您的特批申请，单据已进入导师筛选环节。' : '您的特批申请未获批准。'),
        read: false,
        createdAt: nowTimestamp()
      })

      addAuditLog(db, {
        targetType: 'APPLICATION',
        targetId: app.id,
        operator,
        action: decision === 'APPROVE' ? '特批申请审核通过（留痕）' : '特批申请驳回（留痕）',
        opinion: opinion || '已完成合规复核',
        beforeValue: beforeStatus,
        afterValue: app.status
      })

      persistDB()
      return { code: 200, data: app, message: decision === 'APPROVE' ? '特批已通过并流转至导师选拔' : '已驳回特批申请' }
    })
  },

  // Mentor schedules interview
  scheduleInterview({ applicationId, time, location, note }, operator) {
    return mockAsync((db) => {
      const app = db.applications.find((a) => a.id === applicationId)
      if (!app) return { code: 404, data: null, message: '申请单不存在' }
      const job = db.jobs.find((j) => j.id === app.jobId)

      const beforeStatus = app.status
      app.status = 'INTERVIEW_SCHEDULED'
      app.interviewInfo = { time, location, note }

      db.notifications.unshift({
        id: generateId('NOTIF'),
        userId: app.studentId,
        channel: '站内信 + 模拟邮件',
        title: `面试邀请：${job?.title || ''}`,
        content: `面试时间：${time}，地点：${location}。备注：${note || '请准时参加'}`,
        read: false,
        createdAt: nowTimestamp()
      })

      addAuditLog(db, {
        targetType: 'APPLICATION',
        targetId: app.id,
        operator,
        action: '安排面试并发送通知',
        opinion: `时间：${time}，地点：${location}`,
        beforeValue: beforeStatus,
        afterValue: 'INTERVIEW_SCHEDULED'
      })

      persistDB()
      return { code: 200, data: app, message: '已安排面试并生成站内信与模拟邮件通知' }
    })
  },

  // Mentor scores & hires/rejects applicant (creates unsigned contract upon HIRE)
  decideApplication({ applicationId, score, decision, comment }, operator) {
    return mockAsync((db) => {
      const app = db.applications.find((a) => a.id === applicationId)
      if (!app) return { code: 404, data: null, message: '申请单不存在' }
      const job = db.jobs.find((j) => j.id === app.jobId)
      if (!job) return { code: 404, data: null, message: '关联岗位不存在' }
      const student = db.users.find((u) => u.id === app.studentId)
      if (!student) return { code: 404, data: null, message: '申请人不存在' }

      // 先做全部校验，通过后才修改数据，避免拦截时留下半截状态
      let hireCheck = null
      if (decision === 'HIRE') {
        hireCheck = validateHire(db, app, job, student)
        if (!hireCheck.ok) {
          return { code: 403, data: hireCheck, message: hireCheck.errors.join('；') }
        }
      }

      const beforeStatus = app.status
      app.score = Number(score)
      app.mentorComment = comment || ''

      if (decision === 'HIRE') {
        app.status = 'HIRED'
        job.hiredCount += 1

        // Auto-create electronic contract waiting for student signature
        // 聘期与后端一致：下周一开始，持续岗位约定周数（mock 模式“今天”为 TODAY_STR）
        const startDate = contractStartFrom()
        const endDate = predictContractEnd(job.weeks)
        const contract = {
          id: generateId('CON'),
          applicationId: app.id,
          studentId: app.studentId,
          jobId: job.id,
          deptId: job.deptId,
          startDate,
          endDate,
          weeklyHours: job.weeklyHours,
          hourlyRate: job.hourlyRate,
          status: 'PENDING_SIGN',
          signedAt: null,
          signatureDataUrl: null,
          signatureText: null,
          complianceAgreed: false
        }
        db.contracts.unshift(contract)

        db.notifications.unshift({
          id: generateId('NOTIF'),
          userId: app.studentId,
          channel: '站内信 + 模拟邮件',
          title: `录用通知：${job.title}`,
          content: `恭喜您通过选拔（面试评分 ${score} 分）！系统已生成电子聘用协议（${contract.id}），请尽快前往「工时与合同」手写签名确认。`,
          read: false,
          createdAt: nowTimestamp()
        })

        addAuditLog(db, {
          targetType: 'APPLICATION',
          targetId: app.id,
          operator,
          action: '评分并确认录用',
          opinion:
            `面试评分 ${score} 分，评语：${comment || '同意录用'}；自动生成电子协议 ${contract.id}；` +
            `录用复核跨岗累计 ${hireCheck.projectedHours}h/${hireCheck.maxHours}h` +
            (hireCheck.hoursOverridden ? '（超限部分依据国际生办特批放行）' : ''),
          beforeValue: beforeStatus,
          afterValue: 'HIRED'
        })
      } else {
        app.status = 'REJECTED'
        db.notifications.unshift({
          id: generateId('NOTIF'),
          userId: app.studentId,
          channel: '站内信',
          title: `申请结果通知：${job?.title || ''}`,
          content: `感谢您的投递，经综合评估本次未能录用。评语：${comment || '名额有限，欢迎关注其他岗位'}`,
          read: false,
          createdAt: nowTimestamp()
        })

        addAuditLog(db, {
          targetType: 'APPLICATION',
          targetId: app.id,
          operator,
          action: '选拔未录用',
          opinion: `评分 ${score} 分，原因：${comment || '未通过选拔'}`,
          beforeValue: beforeStatus,
          afterValue: 'REJECTED'
        })
      }

      persistDB()
      return { code: 200, data: app, message: decision === 'HIRE' ? '已录用并自动下发电子聘用协议与通知' : '已更新选拔结果' }
    })
  },

  // P0-3: Sign electronic contract with Canvas signature + intl compliance declaration
  signContract({ contractId, signatureDataUrl, complianceAgreed }, student) {
    return mockAsync((db) => {
      const con = db.contracts.find((c) => c.id === contractId)
      if (!con) return { code: 404, data: null, message: '电子协议不存在' }
      if (!signatureDataUrl) return { code: 400, data: null, message: '请先在签名板手写确认签名' }
      if (student.isInternational && !complianceAgreed) {
        return { code: 400, data: null, message: '国际生必须勾选《国际生合法工时合规声明》方可生效' }
      }

      con.status = 'ACTIVE'
      con.signedAt = nowTimestamp()
      con.signatureDataUrl = signatureDataUrl
      con.signatureText = student.name
      con.complianceAgreed = true

      addAuditLog(db, {
        targetType: 'CONTRACT',
        targetId: con.id,
        operator: student,
        action: '签署电子聘用协议',
        opinion: student.isInternational
          ? '已完成手写电子签名并勾选确认《国际生合法工时合规声明》'
          : '已完成手写电子签名，聘用协议正式生效',
        beforeValue: 'PENDING_SIGN',
        afterValue: 'ACTIVE'
      })

      persistDB()
      return { code: 200, data: con, message: '电子聘用协议签署成功，已正式生效' }
    })
  },

  // Submit weekly timesheet with cross-job cumulative cap check
  submitTimesheet({ contractId, weekNo, hours, workLog, milestoneRef }, studentRef) {
    return mockAsync((db) => {
      const student = db.users.find((u) => u.id === studentRef?.id && u.role === 'STUDENT')
      if (!student) return { code: 403, data: null, message: '仅学生本人可填报工时' }
      const con = db.contracts.find((c) => c.id === contractId && c.studentId === student.id)
      if (!con) return { code: 404, data: null, message: '有效聘用协议不存在' }
      if (con.status !== 'ACTIVE') return { code: 400, data: null, message: '请先签署电子聘用协议后再填报工时' }

      const check = validateTimesheetSubmission(db, student, weekNo, hours)
      if (!check.ok) {
        return { code: 403, data: check, message: check.message }
      }

      const ts = {
        id: generateId('TS'),
        contractId: con.id,
        studentId: student.id,
        jobId: con.jobId,
        deptId: con.deptId,
        weekNo: Number(weekNo),
        month: db.meta.currentMonth || '2026-11',
        hours: Number(hours),
        workLog: workLog.trim(),
        milestoneRef: milestoneRef || '',
        status: 'PENDING',
        submittedAt: nowTimestamp(),
        mentorComment: ''
      }

      db.timesheets.unshift(ts)
      addAuditLog(db, {
        targetType: 'TIMESHEET',
        targetId: ts.id,
        operator: student,
        action: '填报周工时记录',
        opinion: `第 ${weekNo} 周填报 ${hours}h（跨岗累计 ${check.projectedHours}h/${check.maxHours}h）`,
        beforeValue: `当周已报 ${check.existingHours}h`,
        afterValue: `待导师审核（当周累计 ${check.projectedHours}h）`
      })

      persistDB()
      return { code: 200, data: ts, message: '工时已提交，等待导师审核' }
    })
  },

  // Mentor reviews timesheet (approve / reject)
  reviewTimesheet({ timesheetId, decision, comment }, operator) {
    return mockAsync((db) => {
      const ts = db.timesheets.find((t) => t.id === timesheetId)
      if (!ts) return { code: 404, data: null, message: '工时单不存在' }

      const beforeStatus = ts.status
      ts.status = decision === 'APPROVE' ? 'APPROVED' : 'REJECTED'
      ts.mentorComment = comment || (decision === 'APPROVE' ? '工时核实无误，同意通过' : '工作记录不完整，请补充后重提')

      addAuditLog(db, {
        targetType: 'TIMESHEET',
        targetId: ts.id,
        operator,
        action: decision === 'APPROVE' ? '审核通过周工时' : '驳回周工时填报',
        opinion: ts.mentorComment,
        beforeValue: beforeStatus,
        afterValue: ts.status
      })

      persistDB()
      return { code: 200, data: ts, message: decision === 'APPROVE' ? '已审核通过，工时将纳入本月薪酬核算' : '已驳回该工时单' }
    })
  },

  // Toggle RA milestone status
  toggleMilestone({ jobId, milestoneId }, operator) {
    return mockAsync((db) => {
      const job = db.jobs.find((j) => j.id === jobId)
      const ms = job?.extraFields?.milestones?.find((m) => m.id === milestoneId)
      if (!ms) return { code: 404, data: null, message: '里程碑不存在' }
      const before = ms.done ? '已完成' : '进行中'
      ms.done = !ms.done
      addAuditLog(db, {
        targetType: 'JOB',
        targetId: job.id,
        operator,
        action: '更新助研阶段里程碑状态',
        opinion: `里程碑「${ms.title}」`,
        beforeValue: before,
        afterValue: ms.done ? '已完成' : '进行中'
      })
      persistDB()
      return { code: 200, data: ms, message: '已更新里程碑状态' }
    })
  },

  // P0-4: Auto calculate payroll from approved timesheets & evaluation coefficient
  generateMonthlyPayrolls({ month }, operator) {
    return mockAsync((db) => {
      const targetMonth = month || db.meta.currentMonth || '2026-11'
      const activeContracts = db.contracts.filter((c) => c.status === 'ACTIVE')
      let createdOrUpdated = 0

      activeContracts.forEach((con) => {
        const approvedTs = db.timesheets.filter(
          (t) => t.contractId === con.id && t.month === targetMonth && t.status === 'APPROVED'
        )
        if (approvedTs.length === 0) return

        const totalHours = approvedTs.reduce((sum, t) => sum + Number(t.hours || 0), 0)
        const job = db.jobs.find((j) => j.id === con.jobId)
        if (!job) return
        const budget = db.budgets.find((b) => b.id === job.budgetId)

        // Check latest evaluation for coefficient
        const latestEval = db.evaluations.find((e) => e.contractId === con.id)
        const coeff = latestEval ? Number(latestEval.coefficient) : 1.0
        const amount = Math.round(totalHours * Number(con.hourlyRate) * coeff * 100) / 100

        const existing = db.payrolls.find((p) => p.contractId === con.id && p.month === targetMonth)
        if (existing) {
          if (existing.financeStatus === 'PAID') return
          existing.totalHours = totalHours
          existing.coefficient = coeff
          existing.amount = amount
          existing.financeStatus = 'PENDING_PAY'
          existing.calculatedAt = nowTimestamp()
          createdOrUpdated += 1
        } else {
          db.payrolls.unshift({
            id: generateId('PAY'),
            studentId: con.studentId,
            contractId: con.id,
            jobId: con.jobId,
            deptId: con.deptId,
            month: targetMonth,
            totalHours,
            hourlyRate: con.hourlyRate,
            coefficient: coeff,
            amount,
            budgetId: job.budgetId,
            sourceType: budget?.sourceType || 'TA_FUND',
            financeStatus: 'PENDING_PAY',
            calculatedAt: nowTimestamp(),
            paidAt: null,
            voucherNo: null
          })
          createdOrUpdated += 1
        }
      })

      addAuditLog(db, {
        targetType: 'PAYROLL',
        targetId: `BATCH_${targetMonth}`,
        operator,
        action: '执行月度薪酬自动核算',
        opinion: `汇总 ${targetMonth} 已审核通过工时并应用考核绩效系数，共核算 ${createdOrUpdated} 笔单据转入待财务打款。`,
        beforeValue: '待核算',
        afterValue: `待财务打款（${createdOrUpdated} 笔）`
      })

      persistDB()
      return { code: 200, data: { count: createdOrUpdated }, message: `已完成 ${targetMonth} 薪酬自动核算（更新 ${createdOrUpdated} 笔）` }
    })
  },

  // Confirm finance payout -> real-time deducts budget pool usedAmount & updates status to PAID
  confirmPayrollPayout({ payrollId }, operator) {
    return mockAsync((db) => {
      const pay = db.payrolls.find((p) => p.id === payrollId)
      if (!pay) return { code: 404, data: null, message: '薪酬单不存在' }
      if (pay.financeStatus === 'PAID') return { code: 400, data: null, message: '该笔薪酬已发放，请勿重复打款' }

      const budget = db.budgets.find((b) => b.id === pay.budgetId)
      if (!budget) return { code: 404, data: null, message: '关联经费池不存在' }

      const remaining = budget.totalAmount - budget.usedAmount
      if (pay.amount > remaining) {
        return { code: 400, data: null, message: `经费池「${budget.name}」剩余总额不足，拦截打款` }
      }

      const beforeUsed = budget.usedAmount
      budget.usedAmount = Math.round((budget.usedAmount + pay.amount) * 100) / 100
      budget.frozenAmount = Math.max(0, Math.round((budget.frozenAmount - pay.amount) * 100) / 100)

      const voucherNo = `CW-${pay.month.replace('-', '')}-${Math.floor(1000 + Math.random() * 9000)}`
      pay.financeStatus = 'PAID'
      pay.paidAt = nowTimestamp()
      pay.voucherNo = voucherNo

      db.notifications.unshift({
        id: generateId('NOTIF'),
        userId: pay.studentId,
        channel: '站内信 + 模拟邮件',
        title: `${pay.month} 三助薪酬已发放：¥${pay.amount.toFixed(2)}`,
        content: `财务打款凭证号 ${voucherNo}，审核工时 ${pay.totalHours}h × ¥${pay.hourlyRate}/h × 考核系数 ${pay.coefficient}x。`,
        read: false,
        createdAt: nowTimestamp()
      })

      addAuditLog(db, {
        targetType: 'PAYROLL',
        targetId: pay.id,
        operator,
        action: '确认财务发放并联动扣减经费池',
        opinion: `发放金额 ¥${pay.amount.toFixed(2)}，凭证号 ${voucherNo}，经费池「${budget.name}」实时扣减。`,
        beforeValue: `待打款（经费已用 ¥${beforeUsed.toLocaleString()}）`,
        afterValue: `已发放（经费已用 ¥${budget.usedAmount.toLocaleString()}）`
      })

      persistDB()
      return { code: 200, data: pay, message: `财务打款成功（凭证 ${voucherNo}），经费池余额已实时扣减` }
    })
  },

  // P1-2: Submit periodic evaluation using configurable weights
  submitEvaluation({ contractId, period, dimensionScores, comment, courseEvalSync }, operator) {
    return mockAsync((db) => {
      const con = db.contracts.find((c) => c.id === contractId)
      if (!con) return { code: 404, data: null, message: '聘用协议不存在' }
      const job = db.jobs.find((j) => j.id === con.jobId)
      if (!job) return { code: 404, data: null, message: '关联岗位不存在' }

      const template = db.configs.evaluationTemplates[job.type] || []
      const dimensions = template.map((dim) => ({
        key: dim.key,
        label: dim.label,
        weight: dim.weight,
        score: Number(dimensionScores[dim.key] ?? 85)
      }))

      const totalScore = Math.round(
        dimensions.reduce((sum, d) => sum + (d.score * d.weight) / 100, 0) * 10
      ) / 10
      const gradeInfo = gradeFromScore(totalScore)

      const evaluation = {
        id: generateId('EV'),
        contractId: con.id,
        studentId: con.studentId,
        jobId: con.jobId,
        deptId: con.deptId,
        period: period || '2026-11 周期考核',
        jobType: job.type,
        dimensions,
        totalScore,
        grade: gradeInfo.grade,
        coefficient: gradeInfo.coefficient,
        renewalEligible: gradeInfo.renewalEligible,
        courseEvalSync: courseEvalSync || null,
        evaluatorName: operator.name,
        comment: comment || '',
        evaluatedAt: nowTimestamp()
      }

      db.evaluations.unshift(evaluation)

      // Update any un-paid payroll for this contract in current month to reflect new coefficient
      const pendingPay = db.payrolls.find(
        (p) => p.contractId === con.id && p.financeStatus !== 'PAID'
      )
      if (pendingPay) {
        pendingPay.coefficient = gradeInfo.coefficient
        pendingPay.amount = Math.round(pendingPay.totalHours * pendingPay.hourlyRate * gradeInfo.coefficient * 100) / 100
      }

      db.notifications.unshift({
        id: generateId('NOTIF'),
        userId: con.studentId,
        channel: '站内信',
        title: `周期考核结果公布：${gradeInfo.grade}（${totalScore} 分）`,
        content: `岗位「${job.title}」考核分档为「${gradeInfo.grade}」，续聘资格：${gradeInfo.renewalEligible ? '可续聘' : '不予续聘'}，下周期薪酬系数：${gradeInfo.coefficient}x。`,
        read: false,
        createdAt: nowTimestamp()
      })

      addAuditLog(db, {
        targetType: 'EVALUATION',
        targetId: evaluation.id,
        operator,
        action: '提交周期性考核评价',
        opinion: `加权得分 ${totalScore} 分，等级「${gradeInfo.grade}」，薪酬系数 ${gradeInfo.coefficient}x，续聘资格：${gradeInfo.renewalEligible ? '是' : '否'}`,
        beforeValue: '待考核',
        afterValue: `${gradeInfo.grade}（${gradeInfo.coefficient}x）`
      })

      persistDB()
      return { code: 200, data: evaluation, message: `考核已提交：${gradeInfo.grade}（${totalScore}分，系数 ${gradeInfo.coefficient}x）` }
    })
  },

  // P1-1: Save configurable approval workflows & evaluation weights (School Admin)
  saveConfigs({ workflows, evaluationTemplates, globalRules, jobTypeSchemas }, operator) {
    return mockAsync((db) => {
      if (jobTypeSchemas) {
        db.configs.jobTypeSchemas = { ...(db.configs.jobTypeSchemas || {}), ...JSON.parse(JSON.stringify(jobTypeSchemas)) }
      }
      if (evaluationTemplates) {
        for (const [type, dims] of Object.entries(evaluationTemplates)) {
          const sum = dims.reduce((s, d) => s + Number(d.weight || 0), 0)
          if (sum !== 100) {
            return { code: 400, data: null, message: `${type} 考核评价维度权重之和必须等于 100%（当前 ${sum}%）` }
          }
        }
        db.configs.evaluationTemplates = JSON.parse(JSON.stringify(evaluationTemplates))
      }
      if (workflows) {
        db.configs.workflows = JSON.parse(JSON.stringify(workflows))
      }
      if (globalRules) {
        db.configs.globalRules = { ...db.configs.globalRules, ...globalRules }
      }

      addAuditLog(db, {
        targetType: 'CONFIG',
        targetId: 'SYSTEM_CONFIG',
        operator,
        action: '更新校级审批流与考核模板配置',
        opinion: '新配置已即时生效，将应用于所有新建岗位审批与周期考核单据。',
        beforeValue: '原校级规则配置',
        afterValue: '新校级规则配置（已生效）'
      })

      persistDB()
      return { code: 200, data: db.configs, message: '配置已保存并即时生效' }
    })
  },

  markNotificationsRead(userId) {
    return mockAsync((db) => {
      db.notifications.forEach((n) => {
        if (n.userId === userId) n.read = true
      })
      persistDB()
      return { code: 200, data: true, message: '已全部标为已读' }
    })
  },

  updateUserProfile({ userId, email, phone, skills, bio }, operator) {
    return mockAsync((db) => {
      const target = db.users.find((u) => u.id === userId)
      if (!target) return { code: 404, data: null, message: '用户不存在' }
      if (email !== undefined) target.email = email.trim()
      if (phone !== undefined) target.phone = phone.trim()
      if (Array.isArray(skills)) target.skills = skills
      if (bio !== undefined) target.bio = bio.trim()
      addAuditLog(db, {
        targetType: 'USER',
        targetId: target.id,
        operator,
        action: '更新个人主页信息',
        opinion: '已更新联系方式与技能标签',
        beforeValue: '原档案',
        afterValue: '已更新'
      })
      persistDB()
      return { code: 200, data: target, message: '个人主页信息已保存' }
    })
  }
}

// P1 新功能（岗位模板保存/删除、服务评价、续聘）只在 http 模式实现，mock 模式统一提示
const NEED_BACKEND = { code: 501, data: null, message: '该功能需要连接后端' }
const backendOnly = () => Promise.resolve({ ...NEED_BACKEND })
Object.assign(mockApi, {
  listJobTemplates: () => mockAsync((db) => db.jobTemplates || []),
  saveJobTemplate: backendOnly,
  deleteJobTemplate: backendOnly,
  listFeedbackRounds: backendOnly,
  openFeedbackRound: backendOnly,
  closeFeedbackRound: backendOnly,
  getFeedbackSummary: backendOnly,
  renewContract: backendOnly,
  getPublicFeedback: backendOnly,
  submitPublicFeedback: backendOnly
})

// ===== http 模式：调用 Java 后端（docs/后端设计.md 第 5 节）=====
// 方法名与入参签名与 mockApi 保持一致；最后一个 operator / student 参数不发给后端，身份一律取自 token
const enc = (id) => encodeURIComponent(id ?? '')

// P1 第二批（报表导出、审计校验）只在 http 模式实现，mock 模式下统一提示（NEED_BACKEND 定义见上）
mockApi.exportReport = backendOnly
mockApi.verifyAuditChain = backendOnly

const httpApi = {
  fetchSnapshot() {
    return http.get('/api/snapshot')
  },

  // 仅校级管理员可成功，其他角色返回后端的 403 文案
  resetDemoData() {
    return http.post('/api/demo/reset')
  },

  // 真实后端没有“按用户 ID 免密登录”接口，体验账号改由 store.loginAs 用演示密码走正常登录
  loginByUserId() {
    return Promise.resolve({ code: 400, data: null, message: '真实后端模式不支持按用户 ID 登录，请使用账号密码' })
  },

  // 成功时 data 为 { token, user }；密码错误等 401 不触发全局登出跳转
  loginByCredentials(account, password) {
    return http.post(
      '/api/auth/login',
      { account: (account || '').trim(), password: password || '' },
      { skipAuthRedirect: true }
    )
  },

  // 用本地 token 恢复登录：返回当前用户（token 失效由 store 自行清理，不触发全局跳转）
  fetchCurrentUser() {
    return http.get('/api/auth/me', { skipAuthRedirect: true })
  },

  createJob(payload) {
    return http.post('/api/jobs', payload)
  },

  reviewJob({ jobId, decision, opinion }) {
    return http.post(`/api/jobs/${enc(jobId)}/review`, { decision, opinion })
  },

  // attachmentIds：P1 第三批，本人已上传的简历与其他材料
  submitApplication({ jobId, statement, resumeFileName, isSpecialApproval = false, specialReason = '', attachmentIds = [] }) {
    return http.post('/api/applications', {
      jobId, statement, resumeFileName, isSpecialApproval, specialReason, attachmentIds
    })
  },

  reviewSpecialApproval({ applicationId, decision, opinion }) {
    return http.post(`/api/applications/${enc(applicationId)}/special-review`, { decision, opinion })
  },

  scheduleInterview({ applicationId, time, location, note }) {
    return http.post(`/api/applications/${enc(applicationId)}/interview`, { time, location, note })
  },

  decideApplication({ applicationId, score, decision, comment }) {
    return http.post(`/api/applications/${enc(applicationId)}/decision`, { score, decision, comment })
  },

  signContract({ contractId, signatureDataUrl, complianceAgreed }) {
    return http.post(`/api/contracts/${enc(contractId)}/sign`, { signatureDataUrl, complianceAgreed })
  },

  submitTimesheet({ contractId, weekNo, hours, workLog, milestoneRef }) {
    return http.post('/api/timesheets', { contractId, weekNo, hours, workLog, milestoneRef })
  },

  reviewTimesheet({ timesheetId, decision, comment }) {
    return http.post(`/api/timesheets/${enc(timesheetId)}/review`, { decision, comment })
  },

  toggleMilestone({ jobId, milestoneId }) {
    return http.post(`/api/jobs/${enc(jobId)}/milestones/${enc(milestoneId)}/toggle`)
  },

  generateMonthlyPayrolls({ month } = {}) {
    return http.post('/api/payrolls/generate', { month })
  },

  confirmPayrollPayout({ payrollId }) {
    return http.post(`/api/payrolls/${enc(payrollId)}/payout`)
  },

  // P2 C.1：syncTeachingEval 为 true 时后端重新拉取评教结果写入 courseEvalSync（前端不再传评教数字）；
  // serviceDefaultSource 为服务维度默认值来源 FEEDBACK / TEACHING_EVAL
  submitEvaluation({ contractId, period, dimensionScores, comment, serviceOverrideReason, syncTeachingEval = false, serviceDefaultSource = null }) {
    return http.post('/api/evaluations', { contractId, period, dimensionScores, comment, serviceOverrideReason, syncTeachingEval, serviceDefaultSource })
  },

  saveConfigs({ workflows, evaluationTemplates, globalRules, jobTypeSchemas }) {
    return http.put('/api/configs', { workflows, evaluationTemplates, globalRules, jobTypeSchemas })
  },

  // userId 参数保留签名兼容，后端只标记当前登录用户的通知
  markNotificationsRead() {
    return http.post('/api/notifications/read-all')
  },

  // 只能修改本人资料，userId 不发给后端
  updateUserProfile({ email, phone, skills, bio }) {
    return http.put('/api/users/me', { email, phone, skills, bio })
  },

  // ----- P1 第一批：岗位模板、服务对象评价、续聘 -----

  listJobTemplates() {
    return http.get('/api/job-templates')
  },

  // global=true 为全校通用（仅校级管理员）；payload 为除岗位名称外的发布表单字段
  saveJobTemplate({ name, type, global = false, deptId, payload }) {
    return http.post('/api/job-templates', { name, type, global, deptId, payload })
  },

  deleteJobTemplate({ templateId }) {
    return http.del(`/api/job-templates/${enc(templateId)}`)
  },

  // 评价轮列表（含评价码、条数、均分与单条评价），仅导师/院系管理员/校级管理员
  listFeedbackRounds({ contractId } = {}) {
    return http.get(`/api/feedback-rounds${contractId ? `?contractId=${enc(contractId)}` : ''}`)
  },

  openFeedbackRound({ contractId, deadline }) {
    return http.post('/api/feedback-rounds', { contractId, deadline: deadline || null })
  },

  closeFeedbackRound({ roundId }) {
    return http.post(`/api/feedback-rounds/${enc(roundId)}/close`)
  },

  // 评价汇总 { count, avgRating, serviceScore }：助理本人也可查看
  getFeedbackSummary({ contractId }) {
    return http.get(`/api/contracts/${enc(contractId)}/feedback-summary`)
  },

  renewContract({ contractId }) {
    return http.post(`/api/contracts/${enc(contractId)}/renew`)
  },

  // 公开评价页（无需登录）：401 不触发登出跳转
  getPublicFeedback({ code }) {
    return http.get(`/api/public/feedback/${enc((code || '').trim())}`, { skipAuthRedirect: true })
  },

  submitPublicFeedback({ code, rating, comment, clientId }) {
    return http.post(`/api/public/feedback/${enc((code || '').trim())}`, { rating, comment, clientId }, { skipAuthRedirect: true })
  },

  // 报表导出（P1 第二批 2.1）：type 为 jobs/hires/timesheets/payrolls/budgets，format 为 xlsx/csv；带 token 下载
  exportReport({ type, format = 'xlsx', month, deptId } = {}) {
    const q = new URLSearchParams({ format })
    if (month) q.set('month', month)
    if (deptId) q.set('deptId', deptId)
    return download(`/api/reports/${enc(type)}/export?${q}`, `${type}.${format}`)
  },

  // 审计日志完整性校验（P1 第二批 2.2，校级管理员）：data 为 { ok, checked, brokenAt }
  verifyAuditChain() {
    return http.get('/api/audit/verify')
  }
}

// ----- P2 C 批：评教联动与智能推荐（只在 http 模式实现，mock 模式提示需要后端） -----
Object.assign(httpApi, {
  // 评教结果预览：{ available, source, courseCode, semester, responseCount, avgRating, satisfactionRate, serviceScore, message }
  getTeachingEval({ contractId }) {
    return http.get(`/api/integration/teaching-eval?contractId=${enc(contractId)}`)
  },
  // 学生本人的岗位推荐：{ weights, items: [{ jobId, score, applicable, reasons, blockers, factors }], topJobIds }
  getRecommendations() {
    return http.get('/api/recommendations')
  }
})
mockApi.getTeachingEval = backendOnly
mockApi.getRecommendations = backendOnly

export const api = isHttpMode ? httpApi : mockApi
// 供脚本直接测试 mock 后端（scripts/verify-p0-fixes.mjs）
export { mockApi }
