<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">岗位管理与审批</h1>
      <router-link
        v-if="['MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN'].includes(user?.role)"
        to="/admin/jobs/publish"
        class="f-btn f-btn-primary"
      >
        <FluentIcon name="plus" :size="15" />
        <span>发布岗位</span>
      </router-link>
    </header>

    <!-- Filter Bar -->
    <section class="acrylic-card p-16 filter-bar">
      <div class="search-wrap">
        <FluentIcon name="search" :size="15" class="search-icon" />
        <input
          v-model="keyword"
          type="text"
          class="f-input search-input"
          placeholder="搜索岗位名称或编号..."
        />
      </div>

      <div class="f-segmented" role="group" aria-label="按类型筛选">
        <button
          v-for="t in typeOptions"
          :key="t.value"
          type="button"
          :class="{ active: typeFilter === t.value }"
          @click="typeFilter = t.value"
        >
          {{ t.label }}
        </button>
      </div>

      <select v-model="statusFilter" class="f-select status-select">
        <option value="ALL">全部状态</option>
        <option value="PENDING_DEPT_REVIEW">待院系初审</option>
        <option value="PENDING_INTL_REVIEW">待国际生办会签</option>
        <option value="PENDING_SCHOOL_REVIEW">待校级终审</option>
        <option value="PUBLISHED">已正式发布</option>
        <option value="REJECTED">已驳回</option>
      </select>
    </section>

    <!-- Clean Single-Line Density Table -->
    <section class="acrylic-card table-card">
      <div v-if="filteredJobs.length" class="f-table-wrap">
        <table class="f-table f-table-sticky-action">
          <thead>
            <tr>
              <th>岗位名称</th>
              <th>类型</th>
              <th>院系</th>
              <th>名额</th>
              <th style="min-width: 170px;">预计经费与池进度</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="job in filteredJobs" :key="job.id">
              <td>
                <div class="flex items-center gap-8">
                  <span class="fw-600">{{ job.title }}</span>
                  <span v-if="job.allowInternational" class="f-pill f-pill-blue">国际生</span>
                </div>
              </td>
              <td><span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span></td>
              <td class="text-muted">{{ store.getDeptShortName(job.deptId) }}</td>
              <td class="tabular-nums">{{ job.hiredCount }}/{{ job.quota }}</td>
              <td>
                <div class="flex items-center justify-between text-xs tabular-nums">
                  <span class="fw-600">¥{{ calcJobBudgetCost(job).toLocaleString() }}</span>
                  <span class="text-muted">{{ budgetPercent(job.budgetId) }}%</span>
                </div>
                <div class="f-progress-track mt-4">
                  <div
                    class="f-progress-fill"
                    :class="budgetTone(job.budgetId)"
                    :style="{ width: budgetPercent(job.budgetId) + '%' }"
                  />
                </div>
              </td>
              <td>
                <span class="f-pill" :class="`f-pill-${JOB_STATUS_MAP[job.status]?.tone || 'neutral'}`">
                  {{ JOB_STATUS_MAP[job.status]?.label || job.status }}
                </span>
              </td>
              <td>
                <button
                  type="button"
                  class="f-btn f-btn-sm"
                  :class="canReviewJob(job) ? 'f-btn-primary' : ''"
                  @click="openJobDrawer(job)"
                >
                  {{ canReviewJob(job) ? '审批' : '详情' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState
        v-else
        icon="briefcase"
        description="暂无匹配的岗位"
        action-text="重置筛选"
        @action="resetFilters"
      />
    </section>

    <!-- Right-side 520px Acrylic Drawer (All Details & Audit Trail live here) -->
    <AuditDrawer
      v-if="selectedJob"
      :title="selectedJob.title"
      :subtitle="`${selectedJob.id} · ${selectedJob.type}`"
      :audit-logs="selectedJobLogs"
      @close="selectedJobId = null"
    >
      <div class="drawer-card">
        <div class="text-xs text-muted fw-600 mb-8">审批流程</div>
        <div class="wf-steps">
          <div
            v-for="(stepKey, idx) in selectedJob.workflowSteps"
            :key="stepKey"
            class="wf-step"
            :class="{
              done: idx < selectedJob.currentStepIndex || selectedJob.status === 'PUBLISHED',
              current: idx === selectedJob.currentStepIndex && selectedJob.status !== 'PUBLISHED' && selectedJob.status !== 'REJECTED',
              rejected: selectedJob.status === 'REJECTED' && idx === selectedJob.currentStepIndex
            }"
          >
            <span class="wf-dot">{{ idx + 1 }}</span>
            <span class="text-xs">{{ stepLabel(stepKey) }}</span>
          </div>
        </div>
      </div>

      <div class="drawer-card">
        <div class="kv-grid">
          <div><span class="text-muted">院系：</span><strong>{{ store.getDeptName(selectedJob.deptId) }}</strong></div>
          <div><span class="text-muted">导师：</span><strong>{{ store.getUser(selectedJob.mentorId)?.name }}</strong></div>
          <div><span class="text-muted">名额：</span><strong class="tabular-nums">{{ selectedJob.hiredCount }}/{{ selectedJob.quota }} 人</strong></div>
          <div><span class="text-muted">时薪：</span><strong class="tabular-nums">¥{{ selectedJob.hourlyRate }}/h · {{ selectedJob.weeklyHours }}h/周</strong></div>
          <div><span class="text-muted">总支出：</span><strong class="text-blue tabular-nums">¥{{ calcJobBudgetCost(selectedJob).toLocaleString() }}</strong></div>
          <div><span class="text-muted">国际生：</span><strong>{{ selectedJob.allowInternational ? '面向开放' : '否' }}</strong></div>
        </div>

        <div class="mt-12 pt-12 border-t text-xs">
          <div class="text-muted mb-4">经费账号：{{ store.getBudget(selectedJob.budgetId)?.name }}</div>
          <template v-if="selectedJob.type === 'TA'">
            <div>课程：{{ selectedJob.extraFields?.courseCode }} {{ selectedJob.extraFields?.courseName }}</div>
            <div class="mt-4">语言：{{ selectedJob.extraFields?.languageRequirement || '无限制' }} · {{ selectedJob.extraFields?.scheduleText }}</div>
          </template>
          <template v-else-if="selectedJob.type === 'RA'">
            <div>课题：{{ selectedJob.extraFields?.projectCode }} · {{ selectedJob.extraFields?.researchDirection }}</div>
          </template>
          <template v-else>
            <div>科室：{{ selectedJob.extraFields?.officeDept }}（{{ selectedJob.extraFields?.location }}）</div>
          </template>
        </div>
      </div>

      <template v-if="canReviewJob(selectedJob)" #footer>
        <input
          v-model="reviewOpinion"
          type="text"
          class="f-input"
          placeholder="填写审批意见（选填，将写入审计日志）..."
        />
        <div class="flex gap-8 justify-end">
          <button type="button" class="f-btn f-btn-danger-outline" :disabled="reviewing" @click="submitReview('REJECT')">
            驳回
          </button>
          <button type="button" class="f-btn f-btn-primary" :disabled="reviewing" @click="submitReview('APPROVE')">
            {{ user?.role === 'INTL_OFFICER' ? '会签通过' : '审批通过' }}
          </button>
        </div>
      </template>
    </AuditDrawer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import {
  filterJobsByRole,
  calcJobBudgetCost,
  getBudgetUtilization,
  JOB_STATUS_MAP
} from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuditDrawer from '@/components/AuditDrawer.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const keyword = ref('')
const typeFilter = ref('ALL')
const statusFilter = ref('ALL')
const selectedJobId = ref(null)
const reviewOpinion = ref('')
const reviewing = ref(false)

const typeOptions = [
  { value: 'ALL', label: '全部' },
  { value: 'TA', label: 'TA' },
  { value: 'RA', label: 'RA' },
  { value: 'GA', label: 'GA' }
]

const visibleJobs = computed(() => filterJobsByRole(store.db, user.value))

const filteredJobs = computed(() => {
  return visibleJobs.value.filter((job) => {
    if (typeFilter.value !== 'ALL' && job.type !== typeFilter.value) return false
    if (statusFilter.value !== 'ALL' && job.status !== statusFilter.value) return false
    if (keyword.value.trim()) {
      const q = keyword.value.trim().toLowerCase()
      const hay = `${job.title} ${job.id} ${JSON.stringify(job.extraFields || {})}`.toLowerCase()
      if (!hay.includes(q)) return false
    }
    return true
  })
})

const selectedJob = computed(() => store.db.jobs.find((j) => j.id === selectedJobId.value) || null)
const selectedJobLogs = computed(() =>
  selectedJobId.value ? store.db.auditLogs.filter((l) => l.targetType === 'JOB' && l.targetId === selectedJobId.value) : []
)

function budgetPercent(budgetId) { return getBudgetUtilization(store.getBudget(budgetId)) }
function budgetTone(budgetId) {
  const p = budgetPercent(budgetId)
  if (p >= 95) return 'error'
  if (p >= 85) return 'caution'
  return ''
}

function canReviewJob(job) {
  if (!user.value || !job) return false
  if (user.value.role === 'DEPT_ADMIN' && job.status === 'PENDING_DEPT_REVIEW') return true
  if (user.value.role === 'INTL_OFFICER' && job.status === 'PENDING_INTL_REVIEW') return true
  if (user.value.role === 'SCHOOL_ADMIN' && ['PENDING_SCHOOL_REVIEW', 'PENDING_DEPT_REVIEW', 'PENDING_INTL_REVIEW'].includes(job.status)) return true
  return false
}

function stepLabel(key) {
  return { MENTOR_SUBMIT: '导师提交', DEPT_REVIEW: '院系审核', INTL_REVIEW: '国际生办会签', SCHOOL_REVIEW: '校级终审', PUBLISHED: '正式发布' }[key] || key
}

function openJobDrawer(job) {
  selectedJobId.value = job.id
  reviewOpinion.value = ''
}

async function submitReview(decision) {
  if (!selectedJob.value) return
  reviewing.value = true
  try {
    const res = await store.mutate(() =>
      api.reviewJob({ jobId: selectedJob.value.id, decision, opinion: reviewOpinion.value.trim() }, user.value)
    )
    if (res.code === 200) reviewOpinion.value = ''
  } finally {
    reviewing.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  typeFilter.value = 'ALL'
  statusFilter.value = 'ALL'
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-bar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.search-wrap { position: relative; flex: 1; min-width: 200px; }
.search-icon { position: absolute; left: 10px; top: 50%; transform: translateY(-50%); color: var(--color-muted); pointer-events: none; }
.search-input { padding-left: 34px; }
.status-select { width: 150px; }
.table-card { overflow: hidden; }

.drawer-card {
  padding: 16px;
  background: rgba(255, 255, 255, 0.72);
  border: 1px solid var(--color-border-light);
  border-radius: 6px;
}

.wf-steps { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.wf-step { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: var(--radius-pills); background: rgba(96, 94, 92, 0.08); color: var(--color-muted); }
.wf-step.done { background: var(--color-success-bg); color: var(--color-success-text); }
.wf-step.current { background: var(--color-caution-bg); color: var(--color-caution-text); font-weight: 600; }
.wf-step.rejected { background: var(--color-error-bg); color: var(--color-error); font-weight: 600; }
.wf-dot { width: 18px; height: 18px; border-radius: 50%; background: rgba(255, 255, 255, 0.85); display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 600; }
.kv-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; font-size: var(--text-label); }
.border-t { border-top: 1px solid var(--color-border-light); }
.pt-12 { padding-top: 12px; }
.mt-4 { margin-top: 4px; }
.mb-4 { margin-bottom: 4px; }
.mb-8 { margin-bottom: 8px; }
.justify-end { justify-content: flex-end; }
</style>
