<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ $t('admin.reports.title') }}</h1>
      <span class="f-pill f-pill-blue">
        {{ user?.role === 'DEPT_ADMIN' ? deptShortName(store.db, user.deptId) : $t('admin.reports.schoolScope') }}
      </span>
    </header>

    <!-- Multi-dimensional Clean Fluent SVG/CSS Charts -->
    <section class="charts-grid">
      <!-- Chart 1: By Job Type (TA / RA / GA) -->
      <div class="acrylic-card p-20">
        <div class="flex items-center justify-between mb-16 gap-8 flex-wrap">
          <h2 class="text-lg fw-600">{{ $t('admin.reports.byType') }}</h2>
          <div class="flex items-center gap-8">
            <span class="text-xs text-muted">{{ $t('admin.reports.jobsCount', { n: visibleJobs.length }) }}</span>
            <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('jobs', 'xlsx')">{{ $t('admin.reports.exportExcel') }}</button>
            <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('jobs', 'csv')">{{ $t('admin.reports.exportCsv') }}</button>
          </div>
        </div>

        <div class="type-chart">
          <svg viewBox="0 0 120 120" class="donut-svg" :aria-label="$t('admin.reports.donutLabel')">
            <circle cx="60" cy="60" r="46" fill="none" stroke="rgba(96,94,92,0.12)" stroke-width="14" />
            <circle
              v-for="seg in typeSegments"
              :key="seg.type"
              cx="60"
              cy="60"
              r="46"
              fill="none"
              :stroke="seg.color"
              stroke-width="14"
              :stroke-dasharray="`${seg.dash} ${289 - seg.dash}`"
              :stroke-dashoffset="seg.offset"
              transform="rotate(-90 60 60)"
            />
            <text x="60" y="57" text-anchor="middle" font-size="18" font-weight="600" fill="#201f1e">{{ totalQuota }}</text>
            <text x="60" y="73" text-anchor="middle" font-size="9" fill="#605e5c">{{ $t('admin.reports.totalQuota') }}</text>
          </svg>

          <div class="legend-list">
            <div v-for="seg in typeSegments" :key="seg.type" class="legend-row">
              <span class="legend-dot" :style="{ background: seg.color }" />
              <span class="flex-1 text-sm fw-600">{{ seg.label }}</span>
              <span class="text-xs text-muted tabular-nums">{{ $t('admin.reports.segVal', { jobs: seg.jobs, quota: seg.quota }) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Chart 2: By Department Quota & Budget -->
      <div class="acrylic-card p-20">
        <div class="flex items-center justify-between mb-16 gap-8 flex-wrap">
          <h2 class="text-lg fw-600">{{ $t('admin.reports.byDept') }}</h2>
          <div class="flex items-center gap-8">
            <span class="text-xs text-muted">{{ $t('admin.reports.unit') }}</span>
            <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('budgets', 'xlsx')">{{ $t('admin.reports.exportExcel') }}</button>
            <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('budgets', 'csv')">{{ $t('admin.reports.exportCsv') }}</button>
          </div>
        </div>

        <div class="flex-col gap-12">
          <div v-for="row in deptRows" :key="row.id" class="bar-row">
            <div class="flex justify-between text-xs mb-4">
              <span class="fw-600">{{ deptName(store.db, row.id) }}</span>
              <span class="tabular-nums text-muted">
                {{ $t('admin.reports.deptRow', { n: row.activeCount, u: row.usedAmount.toLocaleString(), t: row.totalAmount.toLocaleString() }) }}
              </span>
            </div>
            <div class="f-progress-track" style="height: 6px;">
              <div
                class="f-progress-fill"
                :class="row.rate >= 90 ? 'error' : row.rate >= 80 ? 'caution' : ''"
                :style="{ width: row.rate + '%' }"
              />
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 报表导出（P1 第二批 2.1）：内容由后端按本人数据范围生成 -->
    <section class="acrylic-card p-20">
      <div class="flex items-center justify-between mb-16 gap-12 flex-wrap">
        <div>
          <h2 class="text-lg fw-600">{{ $t('admin.reports.exportTitle') }}</h2>
          <div class="text-xs text-muted mt-4">{{ $t('admin.reports.exportDesc') }}</div>
        </div>
        <div class="flex items-center gap-8 flex-wrap">
          <select v-model="exportMonth" class="f-select" style="width: 130px;" :aria-label="$t('admin.reports.monthAria')">
            <option value="">{{ $t('admin.reports.allMonths') }}</option>
            <option v-for="m in monthOptions" :key="m" :value="m">{{ m }}</option>
          </select>
          <select v-if="canPickDept" v-model="exportDept" class="f-select" style="width: 170px;" :aria-label="$t('admin.reports.deptAria')">
            <option value="">{{ $t('admin.reports.allDepts') }}</option>
            <option v-for="d in store.db.departments" :key="d.id" :value="d.id">{{ deptName(store.db, d.id) }}</option>
          </select>
        </div>
      </div>

      <div class="export-list">
        <div v-for="r in reportDefs" :key="r.type" class="export-row">
          <FluentIcon :name="r.icon" :size="18" class="text-blue" />
          <div class="flex-1">
            <div class="text-sm fw-600">{{ $t(`admin.reports.defs.${r.type}.title`) }}</div>
            <div class="text-xs text-muted">{{ $t(`admin.reports.defs.${r.type}.desc`) }}</div>
          </div>
          <button type="button" class="f-btn f-btn-primary f-btn-sm" :disabled="!!exporting" @click="exportReport(r.type, 'xlsx')">
            {{ exporting === `${r.type}.xlsx` ? $t('admin.reports.exporting') : $t('admin.reports.exportExcel') }}
          </button>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport(r.type, 'csv')">
            {{ exporting === `${r.type}.csv` ? $t('admin.reports.exporting') : $t('admin.reports.exportCsv') }}
          </button>
        </div>
      </div>
    </section>

    <!-- Electronic Archive Table -->
    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-16 border-b gap-8 flex-wrap">
        <h2 class="text-lg fw-600">{{ $t('admin.reports.archiveTitle') }}</h2>
        <div class="flex items-center gap-8 flex-wrap">
          <span class="text-xs text-muted">{{ $t('admin.reports.archiveHint') }}</span>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('hires', 'xlsx')">{{ $t('admin.reports.hiresExcel') }}</button>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" :disabled="!!exporting" @click="exportReport('hires', 'csv')">{{ $t('admin.reports.hiresCsv') }}</button>
        </div>
      </div>

      <div v-if="visibleContracts.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.reports.colContract') }}</th>
              <th>{{ $t('admin.reports.colStudent') }}</th>
              <th>{{ $t('admin.reports.colJobDept') }}</th>
              <th>{{ $t('admin.reports.colTerm') }}</th>
              <th>{{ $t('admin.reports.colContractStatus') }}</th>
              <th>{{ $t('admin.reports.colExport') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="c in visibleContracts" :key="c.id">
              <td class="tabular-nums fw-600">{{ c.id }}</td>
              <td>
                <div class="fw-600">{{ store.getUser(c.studentId)?.name }}</div>
                <div class="text-xs text-muted">{{ store.getUser(c.studentId)?.studentNo }}</div>
              </td>
              <td>
                <div class="text-sm fw-600">{{ store.getJob(c.jobId)?.title }}</div>
                <div class="text-xs text-muted">[{{ store.getJob(c.jobId)?.type }}] {{ deptShortName(store.db, c.deptId) }}</div>
              </td>
              <td class="text-xs tabular-nums">{{ c.startDate }} ~ {{ c.endDate }}</td>
              <td>
                <span class="f-pill" :class="c.status === 'ACTIVE' ? 'f-pill-success' : 'f-pill-caution'">
                  {{ c.status === 'ACTIVE' ? $t('admin.reports.active') : $t('admin.reports.pendingSign') }}
                </span>
              </td>
              <td>
                <button type="button" class="f-btn f-btn-primary f-btn-sm" @click="store.openArchive(c.id)">
                  <FluentIcon name="print" :size="14" />
                  <span>{{ $t('admin.reports.exportArchive') }}</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="file" :description="$t('admin.reports.empty')" />
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import { filterJobsByRole, filterByRoleDept } from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useI18n } from '@/i18n'
import { deptName, deptShortName } from './adminI18n'

const store = useAppStore()
const { t, localized } = useI18n()
const user = computed(() => store.currentUser)

const visibleJobs = computed(() => filterJobsByRole(store.db, user.value))
const visibleContracts = computed(() => filterByRoleDept(store.db, store.db.contracts, user.value, 'CONTRACT'))
const visibleBudgets = computed(() => filterByRoleDept(store.db, store.db.budgets, user.value, 'BUDGET'))

// ===== 报表导出（名称、说明见词条 admin.reports.defs.<type>） =====
const reportDefs = [
  { type: 'jobs', icon: 'briefcase' },
  { type: 'hires', icon: 'users' },
  { type: 'timesheets', icon: 'clock' },
  { type: 'payrolls', icon: 'wallet' },
  { type: 'budgets', icon: 'chart' }
]

const exportMonth = ref('')
const exportDept = ref('')
const exporting = ref('')

// 院系管理员只能导出本院系，不显示院系筛选
const canPickDept = computed(() => user.value?.role !== 'DEPT_ADMIN')

// 可选月份：当前月份 + 工时、薪酬里出现过的月份（倒序）
const monthOptions = computed(() => {
  const set = new Set()
  if (store.db.meta?.currentMonth) set.add(store.db.meta.currentMonth)
  ;(store.db.payrolls || []).forEach((p) => p.month && set.add(p.month))
  ;(store.db.timesheets || []).forEach((t) => t.month && set.add(t.month))
  return [...set].sort().reverse()
})

async function exportReport(type, format) {
  if (exporting.value) return
  exporting.value = `${type}.${format}`
  try {
    const res = await api.exportReport({
      type,
      format,
      month: exportMonth.value || undefined,
      deptId: canPickDept.value ? exportDept.value || undefined : undefined
    })
    store.notify(
      localized(res, 'message') || (res.code === 200 ? t('admin.reports.exportOk') : t('admin.reports.exportFailed')),
      res.code === 200 ? 'success' : 'error'
    )
  } finally {
    exporting.value = ''
  }
}

const totalQuota = computed(() => visibleJobs.value.reduce((s, j) => s + Number(j.quota || 0), 0))

const typeSegments = computed(() => {
  const defs = [
    { type: 'TA', color: '#0078d4' },
    { type: 'RA', color: '#106ebe' },
    { type: 'GA', color: '#605e5c' }
  ].map((d) => ({ ...d, label: t(`admin.jobTypes.${d.type}`) }))
  const circ = 2 * Math.PI * 46 // ~289
  let acc = 0
  return defs.map((d) => {
    const list = visibleJobs.value.filter((j) => j.type === d.type)
    const quota = list.reduce((s, j) => s + Number(j.quota || 0), 0)
    const ratio = totalQuota.value > 0 ? quota / totalQuota.value : 0
    const dash = ratio * circ
    const seg = { ...d, jobs: list.length, quota, dash, offset: -acc }
    acc += dash
    return seg
  })
})

const deptRows = computed(() => {
  const depts = user.value?.role === 'DEPT_ADMIN'
    ? store.db.departments.filter((d) => d.id === user.value.deptId)
    : store.db.departments

  return depts.map((d) => {
    const bList = visibleBudgets.value.filter((b) => b.deptId === d.id)
    const totalAmount = bList.reduce((s, b) => s + Number(b.totalAmount), 0)
    const usedAmount = bList.reduce((s, b) => s + Number(b.usedAmount), 0)
    const activeCount = visibleContracts.value.filter((c) => c.deptId === d.id && c.status === 'ACTIVE').length
    const rate = totalAmount > 0 ? Math.min(100, Math.round((usedAmount / totalAmount) * 100)) : 0
    return { id: d.id, name: d.name, totalAmount, usedAmount, activeCount, rate }
  })
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.charts-grid {
  display: grid;
  grid-template-columns: 1fr 1.25fr;
  gap: 16px;
}

.type-chart {
  display: flex;
  align-items: center;
  gap: 24px;
}

.donut-svg {
  width: 132px;
  height: 132px;
  flex-shrink: 0;
}

.legend-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.legend-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}

.export-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.export-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: 8px;
  flex-wrap: wrap;
}

.table-card { overflow: hidden; }
.border-b { border-bottom: 1px solid var(--color-border-light); }
.flex-1 { flex: 1; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }
.mb-4 { margin-bottom: 4px; }

.charts-grid > * { min-width: 0; }

@media (max-width: 960px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}

/* 手机：英文图例较长，环形图与图例上下排列，图例行可换行 */
@media (max-width: 480px) {
  .type-chart { flex-wrap: wrap; gap: 16px; }
  .legend-row { flex-wrap: wrap; row-gap: 2px; }
}
</style>
