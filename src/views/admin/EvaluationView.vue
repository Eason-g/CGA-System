<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div class="flex items-center gap-12">
        <h1 class="text-title">{{ $t('admin.evaluation.title') }}</h1>
        <button type="button" class="f-expand-trigger" @click="showRules = !showRules">
          <span>{{ $t('admin.evaluation.rules') }}</span>
          <FluentIcon :name="showRules ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <button v-if="canEvaluate" type="button" class="f-btn f-btn-primary" @click="openNewEvalModal()">
        <FluentIcon name="award" :size="15" />
        <span>{{ $t('admin.evaluation.start') }}</span>
      </button>
    </header>

    <div v-if="showRules" class="f-expand-panel" style="margin-top: -8px;">
      {{ $t('admin.evaluation.rulesText1') }}
      {{ $t('admin.evaluation.rulesText2') }}
    </div>

    <!-- 在岗协议：服务对象评价与续聘（P1） -->
    <section v-if="canEvaluate" class="acrylic-card table-card">
      <div class="section-head">
        <h2 class="text-sm fw-600">{{ $t('admin.evaluation.activeTitle') }}</h2>
        <span class="text-xs text-muted">{{ $t('admin.evaluation.activeHint') }}</span>
      </div>
      <div v-if="activeContracts.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.evaluation.colStudent') }}</th>
              <th>{{ $t('admin.evaluation.colJobTerm') }}</th>
              <th>{{ $t('admin.evaluation.colLatest') }}</th>
              <th>{{ $t('admin.evaluation.colFeedback') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
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
                      {{ gradeLabel(latestEval(c.id).grade) }} · {{ latestEval(c.id).totalScore }}
                    </span>
                  </template>
                  <span v-else class="text-xs text-muted">{{ $t('admin.common.none') }}</span>
                </td>
                <td>
                  <template v-if="activeRound(c.id)">
                    <div class="flex items-center gap-6 flex-wrap">
                      <span class="code-chip tabular-nums">{{ activeRound(c.id).code }}</span>
                      <span class="text-xs text-muted">{{ $t('admin.evaluation.deadline', { d: activeRound(c.id).deadline }) }}</span>
                    </div>
                  </template>
                  <div class="text-xs mt-2" :class="roundsOf(c.id).length ? '' : 'text-muted'">
                    {{ feedbackStatsText(c.id) }}
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-8 flex-wrap">
                    <template v-if="activeRound(c.id)">
                      <button type="button" class="f-btn-link text-xs" @click="copyLink(activeRound(c.id))">{{ $t('admin.evaluation.copyLink') }}</button>
                      <button type="button" class="f-btn-link text-xs" :disabled="busy" @click="closeRound(activeRound(c.id))">{{ $t('admin.evaluation.closeRound') }}</button>
                    </template>
                    <button v-else type="button" class="f-btn-link text-xs" :disabled="busy" @click="openRoundModal(c)">{{ $t('admin.evaluation.openRound') }}</button>
                    <button
                      v-if="roundsOf(c.id).some((r) => r.items?.length)"
                      type="button"
                      class="f-btn-link text-xs"
                      @click="toggleFeedback(c.id)"
                    >
                      {{ expandedFeedback.has(c.id) ? $t('admin.evaluation.hideFeedback') : $t('admin.evaluation.showFeedback') }}
                    </button>
                    <button type="button" class="f-btn-link text-xs" @click="openNewEvalModal(c.id)">{{ $t('admin.evaluation.evaluate') }}</button>
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
                        <span class="ml-6">{{ item.comment || $t('admin.evaluation.noComment') }}</span>
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
      <EmptyState v-else icon="file" :description="$t('admin.evaluation.noContracts')" />
    </section>

    <section class="acrylic-card table-card">
      <div v-if="visibleEvaluations.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.evaluation.colStudent') }}</th>
              <th>{{ $t('admin.common.job') }}</th>
              <th>{{ $t('admin.evaluation.colPeriod') }}</th>
              <th>{{ $t('admin.evaluation.colScore') }}</th>
              <th>{{ $t('admin.evaluation.colRenewal') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
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
                    <span class="f-pill" :class="gradeCode(ev.grade) === 'EXCELLENT' ? 'f-pill-success' : gradeCode(ev.grade) === 'PASS' ? 'f-pill-blue' : 'f-pill-error'">
                      {{ gradeLabel(ev.grade) }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-6">
                    <span class="f-pill" :class="ev.renewalEligible ? 'f-pill-success' : 'f-pill-error'">
                      {{ ev.renewalEligible ? $t('admin.evaluation.renewable') : $t('admin.evaluation.notRenewable') }}
                    </span>
                    <span class="text-xs text-blue fw-600 tabular-nums">{{ ev.coefficient }}x</span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-8">
                    <button type="button" class="f-expand-trigger" @click="toggleEval(ev.id)">
                      <span>{{ expandedIds.has(ev.id) ? $t('admin.common.collapse') : $t('admin.evaluation.breakdown') }}</span>
                      <FluentIcon :name="expandedIds.has(ev.id) ? 'chevron-up' : 'chevron-down'" :size="13" />
                    </button>
                    <button type="button" class="f-btn-link text-xs" @click="store.openArchive(ev.contractId)">{{ $t('admin.common.archive') }}</button>
                  </div>
                </td>
              </tr>
              <tr v-if="expandedIds.has(ev.id)" class="expand-row">
                <td colspan="6">
                  <div class="expand-inner">
                    <div class="flex flex-wrap gap-16 text-xs">
                      <span v-for="d in ev.dimensions" :key="d.key">
                        {{ $t('admin.evaluation.dimHead', { label: evalDimLabel(ev.jobType, d), w: d.weight }) }}<strong class="tabular-nums">{{ $t('admin.evaluation.points', { n: d.score }) }}</strong>
                        <span v-if="d.feedbackCount" class="text-blue">{{ $t('admin.evaluation.feedbackScore', { n: d.feedbackCount, s: d.feedbackScore }) }}</span>
                        <span v-if="d.teachingEvalScore !== undefined" class="text-blue">{{ $t('admin.evaluation.teachingScore', { s: d.teachingEvalScore }) }}</span>
                        <span v-if="d.defaultSource" class="text-muted">{{ $t('admin.evaluation.defaultFrom', { src: d.defaultSource === 'TEACHING_EVAL' ? $t('admin.evaluation.srcTeaching') : $t('admin.evaluation.srcFeedback') }) }}</span>
                      </span>
                      <span v-if="ev.courseEvalSync" class="text-blue">
                        {{ $t('admin.evaluation.courseEval', { source: ev.courseEvalSync.source || $t('admin.evaluation.mockEvalSystem') }) }}
                        <template v-if="ev.courseEvalSync.courseCode">{{ ev.courseEvalSync.courseCode }} · {{ ev.courseEvalSync.semester }} · </template>
                        {{ $t('admin.evaluation.courseEvalStats', { n: ev.courseEvalSync.responseCount, avg: ev.courseEvalSync.avgRating, rate: ev.courseEvalSync.satisfactionRate }) }}
                        <template v-if="ev.courseEvalSync.syncedAt">{{ $t('admin.evaluation.syncedAt', { t: ev.courseEvalSync.syncedAt }) }}</template>
                      </span>
                    </div>
                    <div v-for="d in ev.dimensions.filter((x) => x.overrideReason)" :key="`r_${d.key}`" class="text-xs text-caution mt-6">
                      {{ $t('admin.evaluation.overrideReason', { label: evalDimLabel(ev.jobType, d), r: d.overrideReason }) }}
                    </div>
                    <div v-if="ev.comment" class="text-xs text-muted mt-6">{{ $t('admin.evaluation.comment', { c: ev.comment }) }}</div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="award" :description="$t('admin.evaluation.empty')" />
    </section>

    <!-- Open Feedback Round Modal -->
    <div v-if="roundModal.contract" class="f-overlay" @click.self="roundModal.contract = null">
      <div class="f-modal" role="dialog" aria-modal="true" :aria-label="$t('admin.evaluation.roundDialog')">
        <header class="f-modal-header">
          <h2 class="text-sub">{{ $t('admin.evaluation.roundTitle') }}</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="roundModal.contract = null">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-12">
          <div class="text-sm">
            {{ store.getUser(roundModal.contract.studentId)?.name }} · {{ store.getJob(roundModal.contract.jobId)?.title }}
          </div>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.evaluation.roundDeadline') }} <span class="req">*</span></label>
            <input v-model="roundModal.deadline" type="date" class="f-input" :min="today" />
          </div>
          <div class="text-xs text-muted">{{ $t('admin.evaluation.roundHint') }}</div>
        </div>
        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="roundModal.contract = null">{{ $t('admin.common.cancel') }}</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy || !roundModal.deadline" @click="openRound">{{ $t('admin.evaluation.generateCode') }}</button>
        </footer>
      </div>
    </div>

    <!-- Evaluation Modal -->
    <div v-if="showModal" class="f-overlay" @click.self="showModal = false">
      <div class="f-modal" role="dialog" aria-modal="true" :aria-label="$t('admin.evaluation.evalDialog')">
        <header class="f-modal-header">
          <h2 class="text-sub">{{ $t('admin.evaluation.evalTitle') }}</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showModal = false">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>

        <div class="f-modal-body flex-col gap-16">
          <div class="f-field">
            <label class="f-label">{{ $t('admin.evaluation.chooseContract') }} <span class="req">*</span></label>
            <select v-model="evalForm.contractId" class="f-select" @change="initScoresForContract">
              <option v-for="c in activeContracts" :key="c.id" :value="c.id">
                {{ store.getUser(c.studentId)?.name }} · [{{ store.getJob(c.jobId)?.type }}] {{ store.getJob(c.jobId)?.title }}
              </option>
            </select>
          </div>

          <div class="f-field">
            <label class="f-label">{{ $t('admin.evaluation.period') }}</label>
            <input v-model="evalForm.period" type="text" class="f-input" />
          </div>

          <!-- 课程评教联动（P2 C.1）：仅 TA 协议；数据由后端从评教系统拉取 -->
          <div v-if="selectedContractJob?.type === 'TA'" class="eval-sync-panel">
            <div class="sync-head">
              <div class="min-w-0">
                <div class="text-sm fw-600">{{ $t('admin.evaluation.syncTitle') }}</div>
                <div class="text-xs text-muted">{{ $t('admin.evaluation.syncSource', { s: teachingEval?.source || $t('admin.evaluation.mockEvalSystem') }) }}</div>
              </div>
              <button
                type="button"
                class="f-btn f-btn-sm"
                :disabled="teachingLoading || !isHttpMode"
                @click="syncTeachingEval"
              >
                {{ teachingLoading ? $t('admin.evaluation.syncing') : teachingEval ? $t('admin.evaluation.resync') : $t('admin.evaluation.sync') }}
              </button>
            </div>
            <div v-if="!isHttpMode" class="text-xs text-muted">{{ $t('admin.evaluation.syncNeedBackend') }}</div>
            <template v-else-if="teachingEval">
              <div v-if="!teachingEval.available" class="f-banner caution">
                <div class="text-xs">
                  {{ localized(teachingEval, 'message') || $t('admin.evaluation.noEvalData') }}
                  <span v-if="teachingEval.courseCode"> ({{ teachingEval.courseCode }} · {{ teachingEval.semester }})</span>
                </div>
              </div>
              <template v-else>
                <div class="sync-stats">
                  <div class="sync-stat wide">
                    <span class="stat-label">{{ $t('admin.evaluation.courseSemester') }}</span>
                    <span class="stat-value">{{ teachingEval.courseCode }} {{ teachingEval.courseName }} · {{ teachingEval.semester }}</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">{{ $t('admin.evaluation.responses') }}</span>
                    <span class="stat-value tabular-nums">
                      {{ $t('admin.evaluation.responsesVal', { n: teachingEval.responseCount }) }}<template v-if="teachingEval.enrolledCount">{{ $t('admin.evaluation.enrolled', { n: teachingEval.enrolledCount }) }}</template>
                    </span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">{{ $t('admin.evaluation.avgRating') }}</span>
                    <span class="stat-value tabular-nums">{{ teachingEval.avgRating }} / 5</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">{{ $t('admin.evaluation.satisfaction') }}</span>
                    <span class="stat-value tabular-nums">{{ teachingEval.satisfactionRate }}%</span>
                  </div>
                  <div class="sync-stat">
                    <span class="stat-label">{{ $t('admin.evaluation.converted') }}</span>
                    <span class="stat-value tabular-nums text-blue">{{ $t('admin.evaluation.points', { n: teachingEval.serviceScore }) }}</span>
                  </div>
                </div>
                <div v-if="teachingEval.questions?.length" class="text-xs text-muted">
                  <span v-for="(q, i) in teachingEval.questions" :key="i" class="q-item">{{ q.title }} {{ q.avg }}</span>
                </div>
                <div class="flex items-center justify-between gap-8 flex-wrap">
                  <span class="text-xs text-muted tabular-nums">{{ $t('admin.evaluation.fetchedAt', { round: teachingEval.round, t: teachingEval.fetchedAt }) }}</span>
                  <button v-if="!teachingConfirmed" type="button" class="f-btn f-btn-primary f-btn-sm" @click="confirmTeachingEval">
                    {{ $t('admin.evaluation.adopt') }}
                  </button>
                  <span v-else class="f-pill f-pill-success">{{ $t('admin.evaluation.adopted') }}</span>
                </div>
              </template>
            </template>
          </div>

          <div v-if="hasFeedback" class="f-banner success">
            <div class="text-xs flex-1">
              {{ $t('admin.evaluation.feedbackSummary', { n: feedbackSummary.count, avg: feedbackSummary.avgRating, s: feedbackSummary.serviceScore }) }}
            </div>
          </div>

          <!-- 服务对象评价与课程评教同时存在时，由导师选定服务维度的默认值 -->
          <div v-if="hasServiceDim && hasFeedback && teachingConfirmed" class="default-picker">
            <div class="text-xs fw-600">{{ $t('admin.evaluation.defaultSourceTitle', { label: serviceDimLabel }) }}</div>
            <label class="radio-row">
              <input v-model="serviceSource" type="radio" value="FEEDBACK" @change="applyServiceDefault" />
              <span>{{ $t('admin.evaluation.srcFeedbackOpt', { s: feedbackSummary.serviceScore }) }}</span>
            </label>
            <label class="radio-row">
              <input v-model="serviceSource" type="radio" value="TEACHING_EVAL" @change="applyServiceDefault" />
              <span>{{ $t('admin.evaluation.srcTeachingOpt', { s: teachingEval.serviceScore }) }}</span>
            </label>
          </div>
          <div v-else-if="hasServiceDim && serviceDefault !== null" class="text-xs text-muted">
            {{ $t('admin.evaluation.defaultFilled', { label: serviceDimLabel, src: serviceSourceLabel, s: serviceDefault }) }}
          </div>

          <div class="dim-box">
            <div v-for="dim in activeTemplate" :key="dim.key" class="dim-row">
              <div class="flex-1 text-xs">
                <span class="fw-600">{{ evalDimLabel(selectedContractJob?.type || 'TA', dim) }}</span>
                <span class="text-muted"> ({{ dim.weight }}%)</span>
              </div>
              <input v-model.number="evalForm.dimensionScores[dim.key]" type="number" min="0" max="100" class="f-input score-input tabular-nums" />
            </div>

            <div class="preview-result mt-12">
              <span class="fw-600 tabular-nums">{{ $t('admin.evaluation.weighted', { n: liveScore }) }}</span>
              <div class="flex items-center gap-6">
                <span class="f-pill" :class="`f-pill-${liveGrade.tone}`">{{ gradeLabel(liveGrade.grade) }}</span>
                <span class="f-pill f-pill-blue tabular-nums">{{ liveGrade.coefficient }}x</span>
              </div>
            </div>
          </div>

          <div v-if="serviceOverridden" class="f-field">
            <label class="f-label">{{ $t('admin.evaluation.overrideLabel', { label: serviceDimLabel }) }} <span class="req">*</span></label>
            <textarea v-model="evalForm.serviceOverrideReason" class="f-textarea" rows="2" maxlength="500" :placeholder="$t('admin.evaluation.overridePh', { src: serviceSourceLabel })" />
          </div>

          <div class="f-field">
            <label class="f-label">{{ $t('admin.evaluation.overallComment') }}</label>
            <textarea v-model="evalForm.comment" class="f-textarea" rows="2" />
          </div>
        </div>

        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="showModal = false">{{ $t('admin.common.cancel') }}</button>
          <button
            type="button"
            class="f-btn f-btn-primary"
            :disabled="!evalForm.contractId || busy || (serviceOverridden && !evalForm.serviceOverrideReason.trim())"
            @click="submitEval"
          >
            {{ $t('admin.evaluation.submit') }}
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
import { useI18n } from '@/i18n'
import { gradeCode, gradeLabel, evalDimLabel } from './adminI18n'

const SERVICE_KEY = 'service'

const store = useAppStore()
const { t, localized } = useI18n()
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
  if (!list.length) return isHttpMode ? t('admin.evaluation.notOpened') : t('admin.common.needBackend')
  const items = list.flatMap((r) => r.items || [])
  if (!items.length) return t('admin.evaluation.roundsNoItems', { n: list.length })
  const avg = items.reduce((s, i) => s + i.rating, 0) / items.length
  return t('admin.evaluation.itemsStats', { n: items.length, avg: avg.toFixed(2) })
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
  if (!window.confirm(t('admin.evaluation.confirmClose', { code: round.code }))) return
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
    store.notify(t('admin.evaluation.copied', { code: round.code }), 'success')
  } catch {
    window.prompt(t('admin.evaluation.copyPrompt'), link)
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
  if (renewedContract(c)) return { show: true, enabled: false, label: t('admin.evaluation.renewed'), hint: t('admin.evaluation.renewedHint') }
  if (!ev?.renewalEligible || job?.status !== 'PUBLISHED') return { show: false }
  return { show: true, enabled: true, label: t('admin.evaluation.renew'), hint: t('admin.evaluation.renewHint') }
}

async function renew(c) {
  const job = store.getJob(c.jobId)
  if (!window.confirm(t('admin.evaluation.confirmRenew', { name: store.getUser(c.studentId)?.name, job: job?.title }))) return
  busy.value = true
  try {
    await store.mutate(() => api.renewContract({ contractId: c.id }))
  } finally { busy.value = false }
}

// ---------------------------------------------------------------- 考核

// 考核周期名称按当前月份生成，不写死日期
function defaultPeriod() {
  return t('admin.evaluation.defaultPeriod', { month: store.db.meta?.currentMonth || today.value.slice(0, 7) })
}

const evalForm = reactive({
  contractId: '',
  period: '',
  dimensionScores: {},
  comment: '',
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
const serviceDimLabel = computed(() => {
  const dim = activeTemplate.value.find((d) => d.key === SERVICE_KEY)
  return dim ? evalDimLabel(selectedContractJob.value?.type || 'TA', dim) : t('admin.evaluation.serviceDim')
})
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
const serviceSourceLabel = computed(() => (serviceSource.value === 'TEACHING_EVAL' ? t('admin.evaluation.srcTeachingScore') : t('admin.evaluation.srcFeedbackScore')))

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
      store.notify(localized(res, 'message') || t('admin.evaluation.syncFailed'), 'error')
    }
  } finally { teachingLoading.value = false }
}

// 导师确认后，服务维度默认取“均分 × 20”；同时有服务对象评价时可在下方切换来源
function confirmTeachingEval() {
  teachingConfirmed.value = true
  serviceSource.value = 'TEACHING_EVAL'
  applyServiceDefault()
  store.notify(t('admin.evaluation.adoptedToast', { label: serviceDimLabel.value, s: teachingEval.value.serviceScore }), 'info')
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
  evalForm.comment = t('admin.evaluation.defaultComment')
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
