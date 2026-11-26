<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div class="flex items-center gap-12">
        <h1 class="text-title">周期性考核评价</h1>
        <button type="button" class="f-expand-trigger" @click="showRules = !showRules">
          <span>分档规则</span>
          <FluentIcon :name="showRules ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <button v-if="canEvaluate" type="button" class="f-btn f-btn-primary" @click="openNewEvalModal()">
        <FluentIcon name="award" :size="15" />
        <span>发起考核</span>
      </button>
    </header>

    <div v-if="showRules" class="f-expand-panel" style="margin-top: -8px;">
      维度权重动态读取校级配置：≥90 分（优秀 · 可续聘 · 1.1x），60–89 分（合格 · 可续聘 · 1.0x），&lt;60 分（不合格 · 不予续聘 · 0.85x）。
      服务对象评价开启后，考核时 service 维度默认取“平均星级 × 20”，修改须填写理由。
    </div>

    <!-- 在岗协议：服务对象评价与续聘（P1） -->
    <section v-if="canEvaluate" class="acrylic-card table-card">
      <div class="section-head">
        <h2 class="text-sm fw-600">在岗协议 · 服务评价与续聘</h2>
        <span class="text-xs text-muted">评价码发给课程学生或来访师生，在 #/feedback 页面免登录评价</span>
      </div>
      <div v-if="activeContracts.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>受聘学生</th>
              <th>岗位 / 聘期</th>
              <th>最近考核</th>
              <th>服务对象评价</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="c in activeContracts" :key="c.id">
              <tr>
                <td class="fw-600">{{ store.getUser(c.studentId)?.name }}</td>
                <td>
                  <span class="f-type-tag mr-6" :class="store.getJob(c.jobId)?.type?.toLowerCase()">{{ store.getJob(c.jobId)?.type }}</span>
                  <span>{{ store.getJob(c.jobId)?.title }}</span>
                  <div class="text-xs text-muted tabular-nums mt-2">{{ c.startDate }} ~ {{ c.endDate }}</div>
                </td>
                <td>
                  <template v-if="latestEval(c.id)">
                    <span class="f-pill" :class="latestEval(c.id).renewalEligible ? 'f-pill-success' : 'f-pill-error'">
                      {{ latestEval(c.id).grade }} · {{ latestEval(c.id).totalScore }}
                    </span>
                  </template>
                  <span v-else class="text-xs text-muted">暂无</span>
                </td>
                <td>
                  <template v-if="activeRound(c.id)">
                    <div class="flex items-center gap-6 flex-wrap">
                      <span class="code-chip tabular-nums">{{ activeRound(c.id).code }}</span>
                      <span class="text-xs text-muted">截止 {{ activeRound(c.id).deadline }}</span>
                    </div>
                  </template>
                  <div class="text-xs mt-2" :class="roundsOf(c.id).length ? '' : 'text-muted'">
                    {{ feedbackStatsText(c.id) }}
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-8 flex-wrap">
                    <template v-if="activeRound(c.id)">
                      <button type="button" class="f-btn-link text-xs" @click="copyLink(activeRound(c.id))">复制评价链接</button>
                      <button type="button" class="f-btn-link text-xs" :disabled="busy" @click="closeRound(activeRound(c.id))">关闭评价</button>
                    </template>
                    <button v-else type="button" class="f-btn-link text-xs" :disabled="busy" @click="openRoundModal(c)">开启评价</button>
                    <button
                      v-if="roundsOf(c.id).some((r) => r.items?.length)"
                      type="button"
                      class="f-btn-link text-xs"
                      @click="toggleFeedback(c.id)"
                    >
                      {{ expandedFeedback.has(c.id) ? '收起评价' : '查看评价' }}
                    </button>
                    <button type="button" class="f-btn-link text-xs" @click="openNewEvalModal(c.id)">考核</button>
                    <button
                      v-if="renewState(c).show"
                      type="button"
                      class="f-btn f-btn-sm"
                      :disabled="busy || !renewState(c).enabled"
                      :title="renewState(c).hint"
                      @click="renew(c)"
                    >
                      {{ renewState(c).label }}
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="expandedFeedback.has(c.id)" class="expand-row">
                <td colspan="5">
                  <div class="expand-inner flex-col gap-4">
                    <template v-for="r in roundsOf(c.id)" :key="r.id">
                      <div v-for="item in r.items" :key="item.id" class="text-xs">
                        <span class="stars">{{ '★'.repeat(item.rating) }}{{ '☆'.repeat(5 - item.rating) }}</span>
                        <span class="ml-6">{{ item.comment || '（未填写文字）' }}</span>
                        <span class="text-muted ml-6 tabular-nums">{{ item.createdAt }}</span>
                      </div>
                    </template>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="file" description="暂无生效中的聘用协议" />
    </section>

    <section class="acrylic-card table-card">
      <div v-if="visibleEvaluations.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>受聘学生</th>
              <th>岗位</th>
              <th>周期</th>
              <th>得分 / 分档</th>
              <th>续聘与系数</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="ev in visibleEvaluations" :key="ev.id">
              <tr>
                <td class="fw-600">{{ store.getUser(ev.studentId)?.name }}</td>
                <td>
                  <span class="f-type-tag mr-6" :class="ev.jobType.toLowerCase()">{{ ev.jobType }}</span>
                  <span>{{ store.getJob(ev.jobId)?.title }}</span>
                </td>
                <td class="text-muted text-xs">{{ ev.period }}</td>
                <td>
                  <div class="flex items-center gap-6">
                    <span class="fw-600 tabular-nums">{{ ev.totalScore }}</span>
                    <span class="f-pill" :class="ev.grade === '优秀' ? 'f-pill-success' : ev.grade === '合格' ? 'f-pill-blue' : 'f-pill-error'">
                      {{ ev.grade }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-6">
                    <span class="f-pill" :class="ev.renewalEligible ? 'f-pill-success' : 'f-pill-error'">
                      {{ ev.renewalEligible ? '可续聘' : '不续聘' }}
                    </span>
                    <span class="text-xs text-blue fw-600 tabular-nums">{{ ev.coefficient }}x</span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-8">
                    <button type="button" class="f-expand-trigger" @click="toggleEval(ev.id)">
                      <span>{{ expandedIds.has(ev.id) ? '收起' : '明细' }}</span>
                      <FluentIcon :name="expandedIds.has(ev.id) ? 'chevron-up' : 'chevron-down'" :size="13" />
                    </button>
                    <button type="button" class="f-btn-link text-xs" @click="store.openArchive(ev.contractId)">归档</button>
                  </div>
                </td>
              </tr>
              <tr v-if="expandedIds.has(ev.id)" class="expand-row">
                <td colspan="6">
                  <div class="expand-inner">
                    <div class="flex flex-wrap gap-16 text-xs">
                      <span v-for="d in ev.dimensions" :key="d.key">
                        {{ d.label }}（{{ d.weight }}%）：<strong class="tabular-nums">{{ d.score }} 分</strong>
                        <span v-if="d.feedbackCount" class="text-blue">（服务对象评价 {{ d.feedbackCount }} 条，折算 {{ d.feedbackScore }} 分）</span>
                        <span v-if="d.teachingEvalScore !== undefined" class="text-blue">（课程评教折算 {{ d.teachingEvalScore }} 分）</span>
                        <span v-if="d.defaultSource" class="text-muted">· 默认取{{ d.defaultSource === 'TEACHING_EVAL' ? '课程评教' : '服务对象评价' }}</span>
                      </span>
                      <span v-if="ev.courseEvalSync" class="text-blue">
                        课程评教（{{ ev.courseEvalSync.source || '模拟评教系统' }}）：
                        <template v-if="ev.courseEvalSync.courseCode">{{ ev.courseEvalSync.courseCode }} · {{ ev.courseEvalSync.semester }} · </template>
                        {{ ev.courseEvalSync.responseCount }} 份 · 均分 {{ ev.courseEvalSync.avgRating }} · 满意度 {{ ev.courseEvalSync.satisfactionRate }}%
                        <template v-if="ev.courseEvalSync.syncedAt"> · 同步于 {{ ev.courseEvalSync.syncedAt }}</template>
                      </span>
                    </div>
                    <div v-for="d in ev.dimensions.filter((x) => x.overrideReason)" :key="`r_${d.key}`" class="text-xs text-caution mt-6">
                      {{ d.label }}修改理由：{{ d.overrideReason }}
                    </div>
                    <div v-if="ev.comment" class="text-xs text-muted mt-6">评语：{{ ev.comment }}</div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="award" description="暂无考核评价记录" />
    </section>

    <!-- Open Feedback Round Modal -->
    <div v-if="roundModal.contract" class="f-overlay" @click.self="roundModal.contract = null">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="开启服务评价">
        <header class="f-modal-header">
          <h2 class="text-sub">开启服务对象评价</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="roundModal.contract = null">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-12">
          <div class="text-sm">
            {{ store.getUser(roundModal.contract.studentId)?.name }} · {{ store.getJob(roundModal.contract.jobId)?.title }}
          </div>
          <div class="f-field">
            <label class="f-label">评价截止日期 <span class="req">*</span></label>
            <input v-model="roundModal.deadline" type="date" class="f-input" :min="today" />
          </div>
          <div class="text-xs text-muted">系统将生成 8 位评价码，服务对象凭码匿名打 1–5 星；同一浏览器每轮只能提交一次。助理本人只能看到平均分和条数。</div>
        </div>
        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="roundModal.contract = null">取消</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy || !roundModal.deadline" @click="openRound">生成评价码</button>
        </footer>
      </div>
    </div>

    <!-- Evaluation Modal -->
    <div v-if="showModal" class="f-overlay" @click.self="showModal = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="填写考核评分">
        <header class="f-modal-header">
          <h2 class="text-sub">发起周期考核评价</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showModal = false">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>

        <div class="f-modal-body flex-col gap-16">
          <div class="f-field">
            <label class="f-label">选择在岗协议 <span class="req">*</span></label>
            <select v-model="evalForm.contractId" class="f-select" @change="initScoresForContract">
              <option v-for="c in activeContracts" :key="c.id" :value="c.id">
                {{ store.getUser(c.studentId)?.name }} · [{{ store.getJob(c.jobId)?.type }}] {{ store.getJob(c.jobId)?.title }}
              </option>
            </select>
          </div>

          <div class="f-field">
            <label class="f-label">考核周期</label>
            <input v-model="evalForm.period" type="text" class="f-input" />
          </div>

          <!-- 课程评教联动（P2 C.1）：仅 TA 协议；数据由后端从评教系统拉取 -->
          <div v-if="selectedContractJob?.type === 'TA'" class="eval-sync-panel">
            <div class="sync-head">
              <div class="min-w-0">
                <div class="text-sm fw-600">课程评教联动</div>
                <div class="text-xs text-muted">数据来源：{{ teachingEval?.source || '模拟评教系统' }}（演示环境），按课程号 + 学期同步</div>
              </div>
              <button
                type="button"
                class="f-btn f-btn-sm"
                :disabled="teachingLoading || !isHttpMode"
                @click="syncTeachingEval"
              >
                {{ teachingLoading ? '同步中…' : teachingEval ? '重新同步' : '同步评教结果' }}
              </button>
            </div>
            <div v-if="!isHttpMode" class="text-xs text-muted">评教联动需要连接后端</div>
            <template v-else-if="teachingEval">
              <div v-if="!teachingEval.available" class="f-banner caution">
                <div class="text-xs">
                  {{ teachingEval.message || '该课程暂无评教数据' }}
                  <span v-if="teachingEval.courseCode">（{{ teachingEval.courseCode }} · {{ teachingEval.semester }}）</span>
                </div>
              </div>
              <template v-else>
                <div class="sync-stats">
                  <div class="sync-stat wide">
                    <span class="stat-label">课程 / 学期</span>
                    <span class="stat-value">{{ teachingEval.courseCode }} {{ teachingEval.courseName }} · {{ teachingEval.semester }}</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">回收问卷</span>
                    <span class="stat-value tabular-nums">
                      {{ teachingEval.responseCount }} 份<template v-if="teachingEval.enrolledCount"> / {{ teachingEval.enrolledCount }} 人</template>
                    </span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">助教题目均分</span>
                    <span class="stat-value tabular-nums">{{ teachingEval.avgRating }} / 5</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">满意度</span>
                    <span class="stat-value tabular-nums">{{ teachingEval.satisfactionRate }}%</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">折算分（均分 × 20）</span>
                    <span class="stat-value tabular-nums text-blue">{{ teachingEval.serviceScore }} 分</span>
                  </div>
                </div>
                <div v-if="teachingEval.questions?.length" class="text-xs text-muted">
                  <span v-for="(q, i) in teachingEval.questions" :key="i" class="q-item">{{ q.title }} {{ q.avg }}</span>
                </div>
                <div class="flex items-center justify-between gap-8 flex-wrap">
                  <span class="text-xs text-muted tabular-nums">{{ teachingEval.round }} · 获取于 {{ teachingEval.fetchedAt }}</span>
                  <button v-if="!teachingConfirmed" type="button" class="f-btn f-btn-primary f-btn-sm" @click="confirmTeachingEval">
                    确认采用
                  </button>
                  <span v-else class="f-pill f-pill-success">已确认，提交时写入考核记录</span>
                </div>
              </template>
            </template>
          </div>

          <div v-if="hasFeedback" class="f-banner success">
            <div class="text-xs flex-1">
              服务对象评价 {{ feedbackSummary.count }} 条，平均 {{ feedbackSummary.avgRating }} 星，折算 {{ feedbackSummary.serviceScore }} 分
            </div>
          </div>

          <!-- 服务对象评价与课程评教同时存在时，由导师选定服务维度的默认值 -->
          <div v-if="hasServiceDim && hasFeedback && teachingConfirmed" class="default-picker">
            <div class="text-xs fw-600">「{{ serviceDimLabel }}」默认值来源</div>
            <label class="radio-row">
              <input v-model="serviceSource" type="radio" value="FEEDBACK" @change="applyServiceDefault" />
              <span>服务对象评价（{{ feedbackSummary.serviceScore }} 分）</span>
            </label>
            <label class="radio-row">
              <input v-model="serviceSource" type="radio" value="TEACHING_EVAL" @change="applyServiceDefault" />
              <span>课程评教（{{ teachingEval.serviceScore }} 分）</span>
            </label>
          </div>
          <div v-else-if="hasServiceDim && serviceDefault !== null" class="text-xs text-muted">
            「{{ serviceDimLabel }}」已默认填入{{ serviceSourceLabel }} {{ serviceDefault }} 分，修改须填写理由
          </div>

          <div class="dim-box">
            <div v-for="dim in activeTemplate" :key="dim.key" class="dim-row">
              <div class="flex-1 text-xs">
                <span class="fw-600">{{ dim.label }}</span>
                <span class="text-muted">（{{ dim.weight }}%）</span>
              </div>
              <input v-model.number="evalForm.dimensionScores[dim.key]" type="number" min="0" max="100" class="f-input score-input tabular-nums" />
            </div>

            <div class="preview-result mt-12">
              <span class="fw-600 tabular-nums">加权总分：{{ liveScore }} 分</span>
              <div class="flex items-center gap-6">
                <span class="f-pill" :class="`f-pill-${liveGrade.tone}`">{{ liveGrade.grade }}</span>
                <span class="f-pill f-pill-blue tabular-nums">{{ liveGrade.coefficient }}x</span>
              </div>
            </div>
          </div>

          <div v-if="serviceOverridden" class="f-field">
            <label class="f-label">修改「{{ serviceDimLabel }}」分数的理由 <span class="req">*</span></label>
            <textarea v-model="evalForm.serviceOverrideReason" class="f-textarea" rows="2" maxlength="500" :placeholder="`默认分数来自${serviceSourceLabel}，修改须说明理由（写入审计日志）`" />
          </div>

          <div class="f-field">
            <label class="f-label">综合评语</label>
            <textarea v-model="evalForm.comment" class="f-textarea" rows="2" />
          </div>
        </div>

        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="showModal = false">取消</button>
          <button
            type="button"
            class="f-btn f-btn-primary"
            :disabled="!evalForm.contractId || busy || (serviceOverridden && !evalForm.serviceOverrideReason.trim())"
            @click="submitEval"
          >
            提交考核
          </button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, gradeFromScore, localDateStr } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const SERVICE_KEY = 'service'

const store = useAppStore()
const user = computed(() => store.currentUser)
const today = computed(() => store.db.meta?.today || localDateStr())

const canEvaluate = computed(() => ['MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN'].includes(user.value?.role))
const visibleEvaluations = computed(() => filterByRoleDept(store.db, store.db.evaluations, user.value, 'EVALUATION'))
const activeContracts = computed(() =>
  filterByRoleDept(store.db, store.db.contracts, user.value, 'CONTRACT').filter((c) => c.status === 'ACTIVE')
)

const showRules = ref(false)
const showModal = ref(false)
const busy = ref(false)
const expandedIds = ref(new Set())
const expandedFeedback = ref(new Set())

// ---------------------------------------------------------------- 服务对象评价轮

const rounds = ref([])

async function loadRounds() {
  if (!isHttpMode || !canEvaluate.value) return
  const res = await api.listFeedbackRounds()
  if (res.code === 200) rounds.value = res.data || []
}

onMounted(loadRounds)

function roundsOf(contractId) {
  return rounds.value.filter((r) => r.contractId === contractId)
}

function activeRound(contractId) {
  return roundsOf(contractId).find((r) => r.status === 'OPEN') || null
}

function feedbackStatsText(contractId) {
  const list = roundsOf(contractId)
  if (!list.length) return isHttpMode ? '未开启' : '需要连接后端'
  const items = list.flatMap((r) => r.items || [])
  if (!items.length) return `共 ${list.length} 轮 · 暂无评价`
  const avg = items.reduce((s, i) => s + i.rating, 0) / items.length
  return `共 ${items.length} 条 · 均分 ${avg.toFixed(2)} 星`
}

function toggleFeedback(id) {
  const s = new Set(expandedFeedback.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedFeedback.value = s
}

const roundModal = reactive({ contract: null, deadline: '' })

function addDays(dateStr, days) {
  const d = new Date(`${dateStr}T00:00:00`)
  d.setDate(d.getDate() + days)
  return localDateStr(d)
}

function openRoundModal(contract) {
  roundModal.contract = contract
  roundModal.deadline = addDays(today.value, 14)
}

async function openRound() {
  busy.value = true
  try {
    const res = await store.mutate(() => api.openFeedbackRound({ contractId: roundModal.contract.id, deadline: roundModal.deadline }))
    if (res.code === 200) {
      roundModal.contract = null
      await loadRounds()
    }
  } finally { busy.value = false }
}

async function closeRound(round) {
  if (!window.confirm(`确定关闭评价码 ${round.code} 吗？关闭后服务对象将无法再提交评价。`)) return
  busy.value = true
  try {
    const res = await store.mutate(() => api.closeFeedbackRound({ roundId: round.id }))
    if (res.code === 200) await loadRounds()
  } finally { busy.value = false }
}

function feedbackLink(round) {
  const { origin, pathname } = window.location
  return `${origin}${pathname}#/feedback?code=${round.code}`
}

async function copyLink(round) {
  const link = feedbackLink(round)
  try {
    await navigator.clipboard.writeText(link)
    store.notify(`已复制评价链接（评价码 ${round.code}）`, 'success')
  } catch {
    window.prompt('复制下面的评价链接', link)
  }
}

// ---------------------------------------------------------------- 续聘

function latestEval(contractId) {
  return store.db.evaluations
    .filter((e) => e.contractId === contractId)
    .sort((a, b) => String(b.evaluatedAt).localeCompare(String(a.evaluatedAt)))[0] || null
}

// 是否已续聘：同一学生、同一岗位存在开始日晚于本合同结束日的生效/待签合同
function renewedContract(c) {
  return store.db.contracts.find(
    (x) => x.id !== c.id && x.studentId === c.studentId && x.jobId === c.jobId &&
      ['ACTIVE', 'PENDING_SIGN'].includes(x.status) && String(x.startDate) > String(c.endDate)
  )
}

function renewState(c) {
  const job = store.getJob(c.jobId)
  const canRenewRole = user.value?.role === 'SCHOOL_ADMIN' || (user.value?.role === 'MENTOR' && job?.mentorId === user.value?.id)
  if (!canRenewRole) return { show: false }
  const ev = latestEval(c.id)
  if (renewedContract(c)) return { show: true, enabled: false, label: '已发起续聘', hint: '已生成续聘协议，等待学生签署或已生效' }
  if (!ev?.renewalEligible || job?.status !== 'PUBLISHED') return { show: false }
  return { show: true, enabled: true, label: '发起续聘', hint: '考核合格，可续聘：新聘期从原协议结束日次日开始' }
}

async function renew(c) {
  const job = store.getJob(c.jobId)
  if (!window.confirm(`确定为 ${store.getUser(c.studentId)?.name} 发起「${job?.title}」续聘吗？系统将复核跨岗工时与签证有效期，并生成新的待签协议。`)) return
  busy.value = true
  try {
    await store.mutate(() => api.renewContract({ contractId: c.id }))
  } finally { busy.value = false }
}

// ---------------------------------------------------------------- 考核

// 考核周期名称按当前月份生成，不写死日期
function defaultPeriod() {
  return `${store.db.meta?.currentMonth || today.value.slice(0, 7)} 月度考核`
}

const evalForm = reactive({
  contractId: '',
  period: '',
  dimensionScores: {},
  comment: '履职尽责，按期完成任务。',
  serviceOverrideReason: ''
})
const feedbackSummary = ref(null)

// 课程评教联动（P2 C.1）：预览结果、导师是否确认采用、服务维度默认值来源（FEEDBACK / TEACHING_EVAL）
const teachingEval = ref(null)
const teachingLoading = ref(false)
const teachingConfirmed = ref(false)
const serviceSource = ref(null)

const selectedContract = computed(() => store.db.contracts.find((c) => c.id === evalForm.contractId) || null)
const selectedContractJob = computed(() => (selectedContract.value ? store.getJob(selectedContract.value.jobId) : null))
const activeTemplate = computed(() => store.db.configs.evaluationTemplates[selectedContractJob.value?.type || 'TA'] || [])
const serviceDimLabel = computed(() => activeTemplate.value.find((d) => d.key === SERVICE_KEY)?.label || '服务评价')
const hasServiceDim = computed(() => activeTemplate.value.some((d) => d.key === SERVICE_KEY))
const hasFeedback = computed(() => {
  const s = feedbackSummary.value
  return !!(s && s.count > 0 && s.serviceScore !== null && s.serviceScore !== undefined)
})

// 当前选定来源的默认分；没有可用来源时为 null
const serviceDefault = computed(() => {
  if (serviceSource.value === 'FEEDBACK' && hasFeedback.value) return Number(feedbackSummary.value.serviceScore)
  if (serviceSource.value === 'TEACHING_EVAL' && teachingConfirmed.value && teachingEval.value?.available) {
    return Number(teachingEval.value.serviceScore)
  }
  return null
})
const serviceSourceLabel = computed(() => (serviceSource.value === 'TEACHING_EVAL' ? '课程评教折算分' : '服务对象评价折算分'))

// 服务维度有默认值时，改动了默认分就要填理由（与后端规则一致）
const serviceOverridden = computed(() => {
  if (serviceDefault.value === null || !hasServiceDim.value) return false
  return Number(evalForm.dimensionScores[SERVICE_KEY]) !== serviceDefault.value
})

function applyServiceDefault() {
  if (serviceDefault.value !== null && hasServiceDim.value) {
    evalForm.dimensionScores[SERVICE_KEY] = serviceDefault.value
  }
}

async function syncTeachingEval() {
  const contractId = evalForm.contractId
  if (!contractId) return
  teachingLoading.value = true
  try {
    const res = await api.getTeachingEval({ contractId })
    if (contractId !== evalForm.contractId) return
    if (res.code === 200) {
      teachingEval.value = res.data
      teachingConfirmed.value = false
      if (serviceSource.value === 'TEACHING_EVAL') {
        serviceSource.value = hasFeedback.value ? 'FEEDBACK' : null
        applyServiceDefault()
      }
    } else {
      store.notify(res.message || '同步评教结果失败', 'error')
    }
  } finally { teachingLoading.value = false }
}

// 导师确认后，服务维度默认取“均分 × 20”；同时有服务对象评价时可在下方切换来源
function confirmTeachingEval() {
  teachingConfirmed.value = true
  serviceSource.value = 'TEACHING_EVAL'
  applyServiceDefault()
  store.notify(`已采用课程评教结果：「${serviceDimLabel.value}」默认 ${teachingEval.value.serviceScore} 分`, 'info')
}

const liveScore = computed(() => {
  const total = activeTemplate.value.reduce((s, d) => s + (Number(evalForm.dimensionScores[d.key] || 0) * d.weight) / 100, 0)
  return Math.round(total * 10) / 10
})
const liveGrade = computed(() => gradeFromScore(liveScore.value))

function toggleEval(id) {
  const s = new Set(expandedIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedIds.value = s
}

async function initScoresForContract() {
  evalForm.serviceOverrideReason = ''
  feedbackSummary.value = null
  teachingEval.value = null
  teachingConfirmed.value = false
  serviceSource.value = null
  const scores = {}
  activeTemplate.value.forEach((d) => { scores[d.key] = 90 })
  evalForm.dimensionScores = scores
  if (!isHttpMode || !evalForm.contractId) return
  const contractId = evalForm.contractId
  const res = await api.getFeedbackSummary({ contractId })
  if (res.code === 200 && res.data && contractId === evalForm.contractId) {
    feedbackSummary.value = res.data
    // 评教结果尚未确认采用时，有服务对象评价就先用它作默认值（P1 规则）
    if (hasFeedback.value && serviceSource.value === null) {
      serviceSource.value = 'FEEDBACK'
      applyServiceDefault()
    }
  }
}

function openNewEvalModal(contractId) {
  evalForm.contractId = contractId || activeContracts.value[0]?.id || ''
  evalForm.period = defaultPeriod()
  initScoresForContract()
  showModal.value = true
}

async function submitEval() {
  busy.value = true
  try {
    const res = await store.mutate(() =>
      api.submitEvaluation(
        {
          contractId: evalForm.contractId,
          period: evalForm.period,
          dimensionScores: evalForm.dimensionScores,
          comment: evalForm.comment,
          serviceOverrideReason: serviceOverridden.value ? evalForm.serviceOverrideReason.trim() : null,
          // 评教数字由后端重新拉取，前端只告知“导师已确认同步”与选定的默认值来源
          syncTeachingEval: teachingConfirmed.value && !!teachingEval.value?.available,
          serviceDefaultSource: serviceSource.value
        },
        user.value
      )
    )
    if (res.code === 200) showModal.value = false
  } finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; padding: 14px 16px 6px; }
.expand-row { background: rgba(243, 242, 241, 0.55); }
.expand-inner { padding: 4px 2px; }
.dim-box { padding: 14px; border-radius: 4px; background: #faf9f8; border: 1px solid var(--color-border-light); }
.dim-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid var(--color-border-light); }
.dim-row:last-of-type { border-bottom: none; }
.score-input { width: 88px; }
.preview-result { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: #ffffff; border: 1px solid var(--color-blue-border); border-radius: 4px; }
.code-chip { font-family: ui-monospace, Consolas, monospace; font-weight: 600; letter-spacing: 0.08em; padding: 2px 8px; border-radius: 4px; background: var(--color-blue-subtle); color: var(--color-fluent-blue); }
.stars { color: #d9a400; letter-spacing: 1px; }
/* 课程评教联动面板（P2 C.1） */
.eval-sync-panel { display: flex; flex-direction: column; gap: 10px; padding: 12px 14px; border-radius: 4px; border: 1px solid var(--color-blue-border); background: var(--color-blue-subtle); min-width: 0; }
.sync-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
.sync-head .f-btn { flex-shrink: 0; }
.sync-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 8px; }
.sync-stat { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; background: #ffffff; border-radius: 4px; border: 1px solid var(--color-border-light); min-width: 0; }
.sync-stat.wide { grid-column: 1 / -1; }
.stat-label { font-size: 12px; color: var(--color-muted); }
.stat-value { font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.q-item { display: inline-block; margin-right: 10px; }
.default-picker { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border-radius: 4px; border: 1px solid var(--color-border-light); background: #faf9f8; }
.radio-row { display: flex; align-items: center; gap: 8px; font-size: 13px; min-height: 32px; cursor: pointer; }
.min-w-0 { min-width: 0; }
@media (max-width: 768px) {
  .eval-sync-panel { padding: 10px 12px; }
  .sync-head .f-btn { width: 100%; min-height: 40px; }
  .sync-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .stat-label, .stat-value, .radio-row { font-size: 14px; }
  .radio-row { min-height: 40px; }
}
.flex-1 { flex: 1; }
.mr-6 { margin-right: 6px; }
.ml-6 { margin-left: 6px; }
.mt-2 { margin-top: 2px; }
.mt-6 { margin-top: 6px; }
</style>
