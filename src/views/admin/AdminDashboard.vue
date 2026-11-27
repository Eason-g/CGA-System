<template>
  <div class="flex-col gap-24">
    <!-- Greeting Header -->
    <header class="dash-header">
      <div>
        <h1 class="dash-greeting">{{ $t('admin.dashboard.greeting', { name: user?.name }) }}</h1>
        <p class="text-xs text-muted mt-4">
          {{ $t('admin.dashboard.subline', { role: roleLabel(user?.role, user?.roleLabel), month: store.db.meta.currentMonth, week: store.db.meta.currentWeekNo }) }}
        </p>
      </div>
      <div class="flex items-center gap-8">
        <router-link v-if="canPublish" to="/admin/jobs/publish" class="f-btn f-btn-primary">
          <FluentIcon name="plus" :size="15" />
          <span>{{ $t('admin.dashboard.publish') }}</span>
        </router-link>
        <router-link to="/admin/reports" class="f-btn">{{ $t('admin.dashboard.reports') }}</router-link>
      </div>
    </header>

    <!-- 4 Luminous Metric Cards -->
    <section class="kpi-grid">
      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">{{ $t('admin.dashboard.budgetRate') }}</span>
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
          <span class="kpi-label">{{ $t('admin.dashboard.anomalies') }}</span>
          <span class="kpi-icon" :class="anomalies.length ? 'error' : 'neutral'">
            <FluentIcon name="alert" :size="16" />
          </span>
        </div>
        <div class="kpi-num tabular-nums mt-12 flex items-center gap-8">
          <span>{{ anomalies.length }}</span>
          <span v-if="anomalies.length" class="f-dot-error" />
        </div>
        <div class="text-xs text-muted mt-10">{{ $t('admin.dashboard.intlCount', { n: intlWarningCount }) }}</div>
      </div>

      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">{{ $t('admin.dashboard.pending') }}</span>
          <span class="kpi-icon blue"><FluentIcon name="briefcase" :size="16" /></span>
        </div>
        <div class="kpi-num tabular-nums mt-12">{{ pendingMyActions }}</div>
        <div class="text-xs text-muted mt-10">
          {{ $t('admin.dashboard.pendingBreakdown', { jobs: pendingJobs.length, apps: pendingApps.length, ts: pendingTsCount }) }}
        </div>
      </div>

      <div class="acrylic-card kpi-card">
        <div class="flex items-center justify-between">
          <span class="kpi-label">{{ $t('admin.dashboard.activeHires') }}</span>
          <span class="kpi-icon success"><FluentIcon name="users" :size="16" /></span>
        </div>
        <div class="kpi-num tabular-nums mt-12">{{ activeContracts.length }}<span class="kpi-unit">{{ $t('admin.dashboard.peopleUnit') }}</span></div>
        <div class="text-xs text-muted mt-10">{{ $t('admin.dashboard.pendingSign', { n: pendingSignContracts.length }) }}</div>
      </div>
    </section>

    <!-- Two Balanced Panels -->
    <section class="bench-grid">
      <!-- Left: Anomalies (Clean list with click-to-expand) -->
      <div class="acrylic-card p-20 flex-col gap-12">
        <div class="flex items-center justify-between">
          <h2 class="text-sub">{{ $t('admin.dashboard.warnTitle') }}</h2>
          <router-link to="/admin/timesheets" class="f-btn-link text-xs">{{ $t('admin.dashboard.toTimesheets') }}</router-link>
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
                <span class="fw-600 text-sm truncate">{{ localized(w, 'title') }}</span>
              </div>
              <div class="flex items-center gap-6">
                <span class="f-pill" :class="w.severity === 'error' ? 'f-pill-error' : 'f-pill-caution'">
                  {{ categoryLabel(w.category) }}
                </span>
                <FluentIcon :name="expandedWarns.has(w.id) ? 'chevron-up' : 'chevron-down'" :size="14" class="text-subtle" />
              </div>
            </div>
            <div v-if="expandedWarns.has(w.id)" class="row-detail text-xs text-muted mt-8">
              {{ localized(w, 'detail') }}
            </div>
          </div>
        </div>
        <EmptyState v-else icon="check-circle" :description="$t('admin.dashboard.noWarn')" />
      </div>

      <!-- Right: Budget Pools -->
      <div class="acrylic-card p-20 flex-col gap-12">
        <div class="flex items-center justify-between">
          <h2 class="text-sub">{{ $t('admin.dashboard.budgetTitle') }}</h2>
          <router-link to="/admin/payroll" class="f-btn-link text-xs">{{ $t('admin.dashboard.toPayroll') }}</router-link>
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
                {{ $t('admin.dashboard.remaining', { n: getBudgetAvailable(b).toLocaleString() }) }}
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
              <span>{{ b.code }} · {{ isEn ? fundSourceLabel(b.sourceType) : b.sourceLabel || fundSourceLabel(b.sourceType) }}</span>
              <span>{{ $t('admin.dashboard.usedFrozen', { used: b.usedAmount.toLocaleString(), frozen: b.frozenAmount.toLocaleString() }) }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Recent Jobs Table -->
    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-20 pb-12">
        <h2 class="text-sub">{{ $t('admin.dashboard.recentJobs') }}</h2>
        <router-link to="/admin/jobs" class="f-btn f-btn-sm">{{ $t('admin.dashboard.viewAll') }}</router-link>
      </div>

      <div class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.dashboard.colTitle') }}</th>
              <th>{{ $t('admin.common.type') }}</th>
              <th>{{ $t('admin.common.dept') }}</th>
              <th>{{ $t('admin.dashboard.colCost') }}</th>
              <th>{{ $t('admin.common.status') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="job in visibleJobs.slice(0, 5)" :key="job.id">
              <td class="fw-600">{{ job.title }}</td>
              <td><span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span></td>
              <td class="text-muted">{{ deptShortName(store.db, job.deptId) }}</td>
              <td class="tabular-nums">¥{{ calcJobBudgetCost(job).toLocaleString() }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${JOB_STATUS_MAP[job.status]?.tone || 'neutral'}`">
                  {{ statusLabel(JOB_STATUS_MAP, job.status) }}
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
import { useI18n } from '@/i18n'
import { fundSourceLabel } from '@/services/jobSchema'
import { roleLabel, deptShortName, statusLabel } from './adminI18n'

const store = useAppStore()
const { t, isEn, localized } = useI18n()
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
  const key = `admin.dashboard.categories.${cat}`
  const v = t(key)
  return v === key ? t('admin.dashboard.categories.other') : v
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

/* 手机（≤768px）：网格列允许收缩，避免长标题（truncate）把页面撑宽 */
@media (max-width: 768px) {
  .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .bench-grid { grid-template-columns: minmax(0, 1fr); }
  .kpi-grid > *, .bench-grid > * { min-width: 0; }
  .bench-grid .p-20 { padding: 16px; }
  .clean-row { min-height: 44px; }
  .row-detail { font-size: 14px; }
}

@media (max-width: 420px) {
  .kpi-grid { grid-template-columns: minmax(0, 1fr); }
}
</style>
