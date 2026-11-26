<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">报表与电子归档</h1>
      <span class="f-pill f-pill-blue">
        {{ user?.role === 'DEPT_ADMIN' ? store.getDeptShortName(user.deptId) : '全校范围' }}
      </span>
    </header>

    <!-- Multi-dimensional Clean Fluent SVG/CSS Charts -->
    <section class="charts-grid">
      <!-- Chart 1: By Job Type (TA / RA / GA) -->
      <div class="acrylic-card p-20">
        <div class="flex items-center justify-between mb-16">
          <h2 class="text-lg fw-600">按岗位类型分布（TA / RA / GA）</h2>
          <span class="text-xs text-muted">共 {{ visibleJobs.length }} 个岗位</span>
        </div>

        <div class="type-chart">
          <svg viewBox="0 0 120 120" class="donut-svg" aria-label="岗位类型占比图">
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
            <text x="60" y="73" text-anchor="middle" font-size="9" fill="#605e5c">总计划名额</text>
          </svg>

          <div class="legend-list">
            <div v-for="seg in typeSegments" :key="seg.type" class="legend-row">
              <span class="legend-dot" :style="{ background: seg.color }" />
              <span class="flex-1 text-sm fw-600">{{ seg.label }}</span>
              <span class="text-xs text-muted tabular-nums">{{ seg.jobs }} 岗 / {{ seg.quota }} 名额</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Chart 2: By Department Quota & Budget -->
      <div class="acrylic-card p-20">
        <div class="flex items-center justify-between mb-16">
          <h2 class="text-lg fw-600">按院系聘用与预算执行统计</h2>
          <span class="text-xs text-muted">单位：元</span>
        </div>

        <div class="flex-col gap-12">
          <div v-for="row in deptRows" :key="row.id" class="bar-row">
            <div class="flex justify-between text-xs mb-4">
              <span class="fw-600">{{ row.name }}</span>
              <span class="tabular-nums text-muted">
                在岗 {{ row.activeCount }} 人 · 预算已用 ¥{{ row.usedAmount.toLocaleString() }} / ¥{{ row.totalAmount.toLocaleString() }}
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

    <!-- Electronic Archive Table -->
    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-16 border-b">
        <h2 class="text-lg fw-600">电子归档目录</h2>
        <span class="text-xs text-muted">点击导出完整可打印档案</span>
      </div>

      <div v-if="visibleContracts.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>协议编号</th>
              <th>受聘学生</th>
              <th>三助岗位与院系</th>
              <th>聘用周期</th>
              <th>协议状态</th>
              <th>电子归档导出</th>
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
                <div class="text-xs text-muted">[{{ store.getJob(c.jobId)?.type }}] {{ store.getDeptShortName(c.deptId) }}</div>
              </td>
              <td class="text-xs tabular-nums">{{ c.startDate }} ~ {{ c.endDate }}</td>
              <td>
                <span class="f-pill" :class="c.status === 'ACTIVE' ? 'f-pill-success' : 'f-pill-caution'">
                  {{ c.status === 'ACTIVE' ? '在岗履约中' : '待签署协议' }}
                </span>
              </td>
              <td>
                <button type="button" class="f-btn f-btn-primary f-btn-sm" @click="store.openArchive(c.id)">
                  <FluentIcon name="print" :size="14" />
                  <span>导出电子归档视图</span>
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="file" description="当前范围暂无可归档聘用记录" />
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterJobsByRole, filterByRoleDept } from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const visibleJobs = computed(() => filterJobsByRole(store.db, user.value))
const visibleContracts = computed(() => filterByRoleDept(store.db, store.db.contracts, user.value, 'CONTRACT'))
const visibleBudgets = computed(() => filterByRoleDept(store.db, store.db.budgets, user.value, 'BUDGET'))

const totalQuota = computed(() => visibleJobs.value.reduce((s, j) => s + Number(j.quota || 0), 0))

const typeSegments = computed(() => {
  const defs = [
    { type: 'TA', label: '助教（TA）', color: '#0078d4' },
    { type: 'RA', label: '助研（RA）', color: '#106ebe' },
    { type: 'GA', label: '助管（GA）', color: '#605e5c' }
  ]
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

.table-card { overflow: hidden; }
.border-b { border-bottom: 1px solid var(--color-border-light); }
.flex-1 { flex: 1; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }
.mb-4 { margin-bottom: 4px; }

@media (max-width: 960px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
