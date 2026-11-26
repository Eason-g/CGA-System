<template>
  <div class="flex-col gap-24">
    <!-- Greeting Header -->
    <header class="dash-header">
      <div>
        <h1 class="dash-greeting">你好，{{ user?.name }}</h1>
        <p class="text-xs text-muted mt-4">
          {{ user?.roleLabel }} · 2026-2027 秋季学期第 {{ store.db.meta.currentWeekNo }} 周
        </p>
      </div>
      <div class="flex items-center gap-8">
        <router-link v-if="canPublish" to="/admin/jobs/publish" class="f-btn f-btn-primary">
          <FluentIcon name="plus" :size="15" />
          <span>发布新岗位</span>
        </router-link>
        <router-link to="/admin/reports" class="f-btn">报表归档</router-link>
      </div>
    </header>

    <!-- 4 Luminous Metric Cards -->
    <section class="kpi-grid">
      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">预算执行率</span>
          <span class="kpi-icon blue"><FluentIcon name="wallet" :size="16" /></span>
        </div>
        <div class="kpi-num tabular-nums mt-12">{{ budgetStats.rate }}<span class="kpi-unit">%</span></div>
        <div class="f-progress-track mt-12">
          <div
            class="f-progress-fill"
            :class="{ caution: budgetStats.rate >= 80, error: budgetStats.rate >= 95 }"
            :style="{ width: budgetStats.rate + '%' }"
          />
        </div>
      </div>

      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">异常预警</span>
          <span class="kpi-icon" :class="anomalies.length ? 'error' : 'neutral'">
            <FluentIcon name="alert" :size="16" />
          </span>
        </div>
        <div class="kpi-num tabular-nums mt-12 flex items-center gap-8">
          <span>{{ anomalies.length }}</span>
          <span v-if="anomalies.length" class="f-dot-error" />
        </div>
        <div class="text-xs text-muted mt-10">其中涉及国际生 {{ intlWarningCount }} 条</div>
      </div>

      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">待我处理</span>
          <span class="kpi-icon blue"><FluentIcon name="briefcase" :size="16" /></span>
        </div>
        <div class="kpi-num tabular-nums mt-12">{{ pendingMyActions }}</div>
        <div class="text-xs text-muted mt-10">
          岗位 {{ pendingJobs.length }} · 申请 {{ pendingApps.length }} · 工时 {{ pendingTsCount }}
        </div>
      </div>

      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">在岗聘用</span>
          <span class="kpi-icon success"><FluentIcon name="users" :size="16" /></span>
        </div>
        <div class="kpi-num tabular-nums mt-12">{{ activeContracts.length }}<span class="kpi-unit">人</span></div>
        <div class="text-xs text-muted mt-10">待签电子协议 {{ pendingSignContracts.length }} 份</div>
      </div>
    </section>

    <!-- Two Balanced Panels -->
    <section class="bench-grid">
      <!-- Left: Anomalies (Clean list with click-to-expand) -->
      <div class="acrylic-card p-20 flex-col gap-12">
        <div class="flex items-center justify-between">
          <h2 class="text-sub">合规与工时预警</h2>
          <router-link to="/admin/timesheets" class="f-btn-link text-xs">工时审核 →</router-link>
        </div>

        <div v-if="anomalies.length" class="clean-list">
          <div
            v-for="w in anomalies"
            :key="w.id"
            class="clean-row"
            @click="toggleWarn(w.id)"
          >
            <div class="flex items-center justify-between gap-10">
              <div class="flex items-center gap-10 min-w-0">
                <span :class="w.severity === 'error' ? 'f-dot-error' : 'f-dot-caution'" />
                <span class="fw-600 text-sm truncate">{{ w.title }}</span>
              </div>
              <div class="flex items-center gap-6">
                <span class="f-pill" :class="w.severity === 'error' ? 'f-pill-error' : 'f-pill-caution'">
                  {{ categoryLabel(w.category) }}
                </span>
                <FluentIcon :name="expandedWarns.has(w.id) ? 'chevron-up' : 'chevron-down'" :size="14" class="text-subtle" />
              </div>
            </div>
            <div v-if="expandedWarns.has(w.id)" class="row-detail text-xs text-muted mt-8">
              {{ w.detail }}
            </div>
          </div>
        </div>
        <EmptyState v-else icon="check-circle" description="当前范围内一切正常，无异常预警" />
      </div>

      <!-- Right: Budget Pools -->
      <div class="acrylic-card p-20 flex-col gap-12">
        <div class="flex items-center justify-between">
          <h2 class="text-sub">经费池实时余额</h2>
          <router-link to="/admin/payroll" class="f-btn-link text-xs">财务发放 →</router-link>
        </div>

        <div class="clean-list">
          <div
            v-for="b in visibleBudgets"
            :key="b.id"
            class="clean-row"
            @click="toggleBudget(b.id)"
          >
            <div class="flex items-center justify-between gap-10">
              <span class="fw-600 text-sm truncate">{{ b.name }}</span>
              <span class="text-xs fw-600 tabular-nums" :class="budgetUtil(b) >= 85 ? 'text-error' : 'text-blue'">
                余 ¥{{ getBudgetAvailable(b).toLocaleString() }}
              </span>
            </div>
            <div class="f-progress-track mt-8">
              <div
                class="f-progress-fill"
                :class="{ caution: budgetUtil(b) >= 80 && budgetUtil(b) < 92, error: budgetUtil(b) >= 92 }"
                :style="{ width: budgetUtil(b) + '%' }"
              />
            </div>
            <div v-if="expandedBudgets.has(b.id)" class="flex justify-between text-xs text-muted mt-8 tabular-nums">
              <span>{{ b.code }} · {{ b.sourceLabel }}</span>
              <span>已发 ¥{{ b.usedAmount.toLocaleString() }} / 冻结 ¥{{ b.frozenAmount.toLocaleString() }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Recent Jobs Table -->
    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-20 pb-12">
        <h2 class="text-sub">近期岗位动态</h2>
        <router-link to="/admin/jobs" class="f-btn f-btn-sm">查看全部</router-link>
      </div>

      <div class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>岗位名称</th>
              <th>类型</th>
              <th>院系</th>
              <th>预计经费</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="job in visibleJobs.slice(0, 5)" :key="job.id">
              <td class="fw-600">{{ job.title }}</td>
              <td><span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span></td>
              <td class="text-muted">{{ store.getDeptShortName(job.deptId) }}</td>
              <td class="tabular-nums">¥{{ calcJobBudgetCost(job).toLocaleString() }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${JOB_STATUS_MAP[job.status]?.tone || 'neutral'}`">
                  {{ JOB_STATUS_MAP[job.status]?.label || job.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import {
  filterJobsByRole,
  filterByRoleDept,
  scanAnomalies,
  getBudgetAvailable,
  getBudgetUtilization,
  calcJobBudgetCost,
  JOB_STATUS_MAP
} from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const expandedWarns = ref(new Set())
const expandedBudgets = ref(new Set())

function toggleWarn(id) {
  const s = new Set(expandedWarns.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedWarns.value = s
}

function toggleBudget(id) {
  const s = new Set(expandedBudgets.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedBudgets.value = s
}

const canPublish = computed(() => ['MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN'].includes(user.value?.role))

const visibleJobs = computed(() => filterJobsByRole(store.db, user.value))
const visibleBudgets = computed(() => filterByRoleDept(store.db, store.db.budgets, user.value, 'BUDGET'))
const visibleContracts = computed(() => filterByRoleDept(store.db, store.db.contracts, user.value, 'CONTRACT'))
const visibleApps = computed(() => filterByRoleDept(store.db, store.db.applications, user.value, 'APPLICATION'))
const visibleTimesheets = computed(() => filterByRoleDept(store.db, store.db.timesheets, user.value, 'TIMESHEET'))

const activeContracts = computed(() => visibleContracts.value.filter((c) => c.status === 'ACTIVE'))
const pendingSignContracts = computed(() => visibleContracts.value.filter((c) => c.status === 'PENDING_SIGN'))

const anomalies = computed(() => scanAnomalies(store.db, user.value))
const intlWarningCount = computed(() => anomalies.value.filter((w) => w.isInternational).length)

const budgetStats = computed(() => {
  const total = visibleBudgets.value.reduce((s, b) => s + Number(b.totalAmount), 0)
  const committed = visibleBudgets.value.reduce((s, b) => s + Number(b.usedAmount) + Number(b.frozenAmount), 0)
  const rate = total > 0 ? Math.min(100, Math.round((committed / total) * 100)) : 0
  return { total, committed, rate }
})

const pendingJobs = computed(() => {
  const role = user.value?.role
  if (role === 'DEPT_ADMIN') return visibleJobs.value.filter((j) => j.status === 'PENDING_DEPT_REVIEW')
  if (role === 'INTL_OFFICER') return visibleJobs.value.filter((j) => j.status === 'PENDING_INTL_REVIEW')
  if (role === 'SCHOOL_ADMIN') return visibleJobs.value.filter((j) => ['PENDING_DEPT_REVIEW', 'PENDING_INTL_REVIEW', 'PENDING_SCHOOL_REVIEW'].includes(j.status))
  return visibleJobs.value.filter((j) => j.status.startsWith('PENDING_'))
})

const pendingApps = computed(() => {
  const role = user.value?.role
  if (role === 'INTL_OFFICER') return visibleApps.value.filter((a) => a.status === 'PENDING_SPECIAL_APPROVAL')
  return visibleApps.value.filter((a) => ['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(a.status))
})

const pendingTsCount = computed(() => visibleTimesheets.value.filter((t) => t.status === 'PENDING').length)
const pendingMyActions = computed(() => pendingJobs.value.length + pendingApps.value.length + pendingTsCount.value)

function budgetUtil(b) { return getBudgetUtilization(b) }
function categoryLabel(cat) {
  return { HOURS_LIMIT: '工时预警', MISSING_TIMESHEET: '未填报', EXPIRING_SOON: '临期' }[cat] || '预警'
}
</script>

<style scoped>
.dash-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.dash-greeting {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.25;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.kpi-card {
  padding: 20px;
}

.kpi-label {
  font-size: 12.5px;
  color: var(--color-muted);
  font-weight: 600;
}

.kpi-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.kpi-icon.blue { background: var(--color-blue-subtle); color: var(--color-fluent-blue); }
.kpi-icon.error { background: var(--color-error-bg); color: var(--color-error); }
.kpi-icon.success { background: var(--color-success-bg); color: var(--color-success); }
.kpi-icon.neutral { background: var(--surface-subtle); color: var(--color-muted); }

.kpi-num {
  font-size: 30px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.kpi-unit {
  font-size: 14px;
  font-weight: 400;
  color: var(--color-muted);
  margin-left: 2px;
}

.bench-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

/* Borderless-feel hairlines inside cards (eliminates double-boxing!) */
.clean-list {
  display: flex;
  flex-direction: column;
}

.clean-row {
  padding: 12px 10px;
  margin: 0 -10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background var(--motion-fast);
}

.clean-row + .clean-row {
  border-top: 1px solid var(--color-border-light);
  border-top-left-radius: 0;
  border-top-right-radius: 0;
}

.clean-row:hover {
  background: var(--surface-subtle);
}

.row-detail {
  padding-left: 17px;
  line-height: 1.55;
}

.table-card { overflow: hidden; }
.pb-12 { padding-bottom: 12px; }
.mt-4 { margin-top: 4px; }
.mt-10 { margin-top: 10px; }
.min-w-0 { min-width: 0; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 1120px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .bench-grid { grid-template-columns: 1fr; }
}

@media (max-width: 600px) {
  .kpi-grid { grid-template-columns: 1fr; }
}
</style>
