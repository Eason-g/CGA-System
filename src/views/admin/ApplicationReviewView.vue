<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ user?.role === 'INTL_OFFICER' ? '国际生特批审核' : '选拔面试与特批' }}</h1>
      <div class="f-segmented">
        <button type="button" :class="{ active: filterTab === 'ALL' }" @click="filterTab = 'ALL'">全部</button>
        <button type="button" :class="{ active: filterTab === 'SPECIAL' }" @click="filterTab = 'SPECIAL'">特批（{{ specialCount }}）</button>
        <button type="button" :class="{ active: filterTab === 'PENDING' }" @click="filterTab = 'PENDING'">待处理</button>
      </div>
    </header>

    <section class="acrylic-card table-card">
      <div v-if="filteredApps.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>申请人</th>
              <th>申请岗位</th>
              <th>合规标识</th>
              <th>评分</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="app in filteredApps" :key="app.id">
              <td>
                <div class="flex items-center gap-6">
                  <span class="fw-600">{{ store.getUser(app.studentId)?.name }}</span>
                  <span v-if="store.getUser(app.studentId)?.isInternational" class="f-pill f-pill-blue">国际生</span>
                </div>
              </td>
              <td>{{ store.getJob(app.jobId)?.title }}</td>
              <td>
                <span v-if="app.isSpecialApproval" class="f-pill f-pill-caution">特批留痕</span>
                <span
                  v-else
                  class="f-pill"
                  :class="app.complianceCheckResult?.passed ? 'f-pill-success' : 'f-pill-error'"
                >
                  {{ app.complianceCheckResult?.passed ? '合规通过' : '拦截' }}
                </span>
              </td>
              <td class="tabular-nums text-muted">{{ app.score !== null && app.score !== undefined ? `${app.score} 分` : '-' }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${APP_STATUS_MAP[app.status]?.tone || 'neutral'}`">
                  {{ APP_STATUS_MAP[app.status]?.label || app.status }}
                </span>
              </td>
              <td>
                <button
                  type="button"
                  class="f-btn f-btn-sm"
                  :class="needsAction(app) ? 'f-btn-primary' : ''"
                  @click="openDrawer(app)"
                >
                  {{ needsAction(app) ? '处理' : '详情' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="users" description="暂无申请记录" />
    </section>

    <AuditDrawer
      v-if="selectedApp"
      :title="`${selectedStudent?.name} · 申请详情`"
      :subtitle="`${selectedApp.id} · ${selectedJob?.title}`"
      :audit-logs="selectedAppLogs"
      @close="selectedAppId = null"
    >
      <div class="drawer-card">
        <div class="kv-grid">
          <div><span class="text-muted">学号：</span><strong>{{ selectedStudent?.studentNo }}</strong></div>
          <div><span class="text-muted">专业：</span><strong>{{ selectedStudent?.major }}</strong></div>
          <div><span class="text-muted">语言：</span><strong>{{ selectedStudent?.languageCert || '-' }}</strong></div>
          <div><span class="text-muted">签证：</span><strong>{{ selectedStudent?.isInternational ? selectedStudent.visaType : '中国籍' }}</strong></div>
        </div>
        <div class="quote-box mt-10 text-xs">{{ selectedApp.statement }}</div>
        <div class="mt-8 text-xs text-blue">附件：{{ selectedApp.resumeFileName }}</div>
      </div>

      <div class="drawer-card">
        <div class="fw-600 text-xs mb-8">前置合规校验</div>
        <div class="flex-col gap-6">
          <div v-for="c in selectedApp.complianceCheckResult?.checks || []" :key="c.key" class="check-mini" :class="c.passed ? 'ok' : 'fail'">
            <FluentIcon :name="c.passed ? 'check-circle' : 'x-circle'" :size="14" />
            <div class="text-xs"><strong>{{ c.label }}</strong>：{{ c.detail }}</div>
          </div>
        </div>
        <div v-if="selectedApp.isSpecialApproval" class="f-banner caution mt-10 text-xs">
          <span><strong>特批理由：</strong>{{ selectedApp.specialReason }}</span>
        </div>
      </div>

      <template #footer>
        <div v-if="canReviewSpecial" class="flex-col gap-10">
          <input v-model="specialOpinion" type="text" class="f-input" placeholder="填写特批审批意见（写入留痕）..." />
          <div class="flex gap-8 justify-end">
            <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleSpecialDecision('REJECT')">驳回</button>
            <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleSpecialDecision('APPROVE')">批准特批</button>
          </div>
        </div>

        <div v-else-if="canMentorProcess" class="flex-col gap-10">
          <div class="f-segmented">
            <button type="button" :class="{ active: actionMode === 'INTERVIEW' }" @click="actionMode = 'INTERVIEW'">安排面试</button>
            <button type="button" :class="{ active: actionMode === 'DECIDE' }" @click="actionMode = 'DECIDE'">评分录用</button>
          </div>

          <div v-if="actionMode === 'INTERVIEW'" class="flex-col gap-8">
            <div class="grid-2">
              <input v-model="interviewForm.time" type="text" class="f-input" placeholder="面试时间" />
              <input v-model="interviewForm.location" type="text" class="f-input" placeholder="面试地点" />
            </div>
            <div class="flex justify-end">
              <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleScheduleInterview">发送面试通知</button>
            </div>
          </div>

          <div v-else class="flex-col gap-8">
            <div class="grid-2">
              <input v-model.number="decideForm.score" type="number" min="0" max="100" class="f-input tabular-nums" placeholder="评分" />
              <input v-model="decideForm.comment" type="text" class="f-input" placeholder="录用评语" />
            </div>
            <div class="flex gap-8 justify-end">
              <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleDecide('REJECT')">不予录用</button>
              <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleDecide('HIRE')">确认录用</button>
            </div>
          </div>
        </div>
      </template>
    </AuditDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, APP_STATUS_MAP } from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuditDrawer from '@/components/AuditDrawer.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const filterTab = ref('ALL')
const selectedAppId = ref(null)
const actionMode = ref('INTERVIEW')
const specialOpinion = ref('')
const busy = ref(false)

const interviewForm = reactive({ time: '2026-11-24 14:30', location: '信息楼 412', note: '准备 5 分钟试讲' })
const decideForm = reactive({ score: 90, comment: '符合岗位要求，同意录用。' })

const visibleApps = computed(() => filterByRoleDept(store.db, store.db.applications, user.value, 'APPLICATION'))
const specialCount = computed(() => visibleApps.value.filter((a) => a.isSpecialApproval).length)

const filteredApps = computed(() => {
  return visibleApps.value.filter((a) => {
    if (filterTab.value === 'SPECIAL') return a.isSpecialApproval
    if (filterTab.value === 'PENDING') return ['SUBMITTED', 'PENDING_SPECIAL_APPROVAL', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(a.status)
    return true
  })
})

const selectedApp = computed(() => store.db.applications.find((a) => a.id === selectedAppId.value) || null)
const selectedStudent = computed(() => (selectedApp.value ? store.getUser(selectedApp.value.studentId) : null))
const selectedJob = computed(() => (selectedApp.value ? store.getJob(selectedApp.value.jobId) : null))
const selectedAppLogs = computed(() =>
  selectedAppId.value ? store.db.auditLogs.filter((l) => l.targetType === 'APPLICATION' && l.targetId === selectedAppId.value) : []
)

const canReviewSpecial = computed(() =>
  selectedApp.value?.status === 'PENDING_SPECIAL_APPROVAL' && ['INTL_OFFICER', 'SCHOOL_ADMIN'].includes(user.value?.role)
)
const canMentorProcess = computed(() =>
  ['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(selectedApp.value?.status) && ['MENTOR', 'SCHOOL_ADMIN'].includes(user.value?.role)
)

function needsAction(app) {
  if (app.status === 'PENDING_SPECIAL_APPROVAL' && ['INTL_OFFICER', 'SCHOOL_ADMIN'].includes(user.value?.role)) return true
  if (['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(app.status) && ['MENTOR', 'SCHOOL_ADMIN'].includes(user.value?.role)) return true
  return false
}

function openDrawer(app) {
  selectedAppId.value = app.id
  specialOpinion.value = ''
  actionMode.value = app.status === 'INTERVIEW_SCHEDULED' ? 'DECIDE' : 'INTERVIEW'
}

async function handleSpecialDecision(decision) {
  busy.value = true
  try {
    await store.mutate(() => api.reviewSpecialApproval({ applicationId: selectedApp.value.id, decision, opinion: specialOpinion.value.trim() }, user.value))
  } finally { busy.value = false }
}

async function handleScheduleInterview() {
  busy.value = true
  try {
    await store.mutate(() => api.scheduleInterview({ applicationId: selectedApp.value.id, ...interviewForm }, user.value))
    actionMode.value = 'DECIDE'
  } finally { busy.value = false }
}

async function handleDecide(decision) {
  busy.value = true
  try {
    await store.mutate(() => api.decideApplication({ applicationId: selectedApp.value.id, score: decideForm.score, decision, comment: decideForm.comment }, user.value))
  } finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.drawer-card { padding: 14px 16px; background: rgba(255, 255, 255, 0.72); border: 1px solid var(--color-border-light); border-radius: 6px; }
.kv-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; font-size: var(--text-label); }
.quote-box { padding: 8px 10px; background: #ffffff; border: 1px solid var(--color-border-light); border-radius: 4px; }
.check-mini { display: flex; align-items: flex-start; gap: 8px; padding: 6px 8px; background: #ffffff; border-radius: 4px; border: 1px solid var(--color-border-light); }
.check-mini.ok svg { color: var(--color-success); margin-top: 2px; flex-shrink: 0; }
.check-mini.fail svg { color: var(--color-error); margin-top: 2px; flex-shrink: 0; }
.grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.mt-8 { margin-top: 8px; }
.mt-10 { margin-top: 10px; }
.mb-8 { margin-bottom: 8px; }
.justify-end { justify-content: flex-end; }
</style>
