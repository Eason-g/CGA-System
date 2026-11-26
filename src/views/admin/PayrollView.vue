<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div class="flex items-center gap-12">
        <h1 class="text-title">薪酬核算与预算联动</h1>
        <button type="button" class="f-expand-trigger" @click="showRule = !showRule">
          <span>核算公式</span>
          <FluentIcon :name="showRule ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <button v-if="canFinance" type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleAutoCalc">
        <FluentIcon name="sparkles" :size="15" />
        <span>核算 2026-11 薪酬</span>
      </button>
    </header>

    <div v-if="showRule" class="f-expand-panel" style="margin-top: -8px;">
      实发薪酬 = 导师审核通过工时 × 岗位时薪 × 周期考核系数（优秀 1.1x / 合格 1.0x / 不合格 0.85x）；确认打款后实时扣减对应经费池余额。
    </div>

    <!-- 3 Compact Budget Source Cards -->
    <section class="source-grid">
      <div v-for="s in sourceSummaries" :key="s.sourceType" class="acrylic-card p-16">
        <div class="flex items-center justify-between text-xs text-muted">
          <span class="fw-600 text-text">{{ s.label }}</span>
          <span class="tabular-nums">{{ s.rate }}%</span>
        </div>
        <div class="source-amount tabular-nums mt-6">¥{{ s.available.toLocaleString() }}</div>
        <div class="f-progress-track mt-8">
          <div class="f-progress-fill" :class="s.rate >= 92 ? 'error' : s.rate >= 80 ? 'caution' : ''" :style="{ width: s.rate + '%' }" />
        </div>
        <div class="text-xs text-muted mt-6 tabular-nums">已发 ¥{{ s.used.toLocaleString() }} · 冻结 ¥{{ s.frozen.toLocaleString() }}</div>
      </div>
    </section>

    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-16 border-b flex-wrap gap-12">
        <div class="f-segmented">
          <button type="button" :class="{ active: sourceFilter === 'ALL' }" @click="sourceFilter = 'ALL'">全部</button>
          <button type="button" :class="{ active: sourceFilter === 'TA_FUND' }" @click="sourceFilter = 'TA_FUND'">教务经费</button>
          <button type="button" :class="{ active: sourceFilter === 'RA_PROJECT' }" @click="sourceFilter = 'RA_PROJECT'">科研经费</button>
          <button type="button" :class="{ active: sourceFilter === 'GA_FUND' }" @click="sourceFilter = 'GA_FUND'">行政专项</button>
        </div>
        <div class="text-xs text-muted tabular-nums">
          合计：<strong class="text-blue">¥{{ totalFilteredAmount.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</strong>
        </div>
      </div>

      <div v-if="filteredPayrolls.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>学生</th>
              <th>岗位</th>
              <th>月份</th>
              <th>核算明细</th>
              <th>实发</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in filteredPayrolls" :key="p.id">
              <td class="fw-600">{{ store.getUser(p.studentId)?.name }}</td>
              <td>{{ store.getJob(p.jobId)?.title }}</td>
              <td class="tabular-nums text-muted">{{ p.month }}</td>
              <td class="text-xs tabular-nums text-muted">{{ p.totalHours }}h × ¥{{ p.hourlyRate }} × {{ p.coefficient }}x</td>
              <td class="tabular-nums fw-600 text-blue">¥{{ p.amount.toFixed(2) }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${FINANCE_STATUS_MAP[p.financeStatus]?.tone || 'neutral'}`">
                  {{ FINANCE_STATUS_MAP[p.financeStatus]?.label || p.financeStatus }}
                </span>
              </td>
              <td>
                <button
                  v-if="p.financeStatus === 'PENDING_PAY' && canFinance"
                  type="button"
                  class="f-btn f-btn-primary f-btn-sm"
                  :disabled="busy"
                  @click="handlePayout(p)"
                >
                  确认打款
                </button>
                <button
                  v-else-if="p.financeStatus === 'PENDING_CALC' && canFinance"
                  type="button"
                  class="f-btn f-btn-sm"
                  :disabled="busy"
                  @click="handleAutoCalc"
                >
                  核算
                </button>
                <button v-else type="button" class="f-btn-link text-xs" @click="store.openArchive(p.contractId)">归档</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="wallet" description="暂无薪酬记录" />
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, getBudgetAvailable, FINANCE_STATUS_MAP } from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const sourceFilter = ref('ALL')
const showRule = ref(false)
const busy = ref(false)

const canFinance = computed(() => ['SCHOOL_ADMIN', 'DEPT_ADMIN'].includes(user.value?.role))
const visibleBudgets = computed(() => filterByRoleDept(store.db, store.db.budgets, user.value, 'BUDGET'))
const visiblePayrolls = computed(() => filterByRoleDept(store.db, store.db.payrolls, user.value, 'PAYROLL'))

const sourceSummaries = computed(() => {
  const defs = [
    { sourceType: 'TA_FUND', label: '教务经费（TA）' },
    { sourceType: 'RA_PROJECT', label: '科研经费（RA）' },
    { sourceType: 'GA_FUND', label: '行政专项（GA）' }
  ]
  return defs.map((d) => {
    const list = visibleBudgets.value.filter((b) => b.sourceType === d.sourceType)
    const total = list.reduce((s, b) => s + Number(b.totalAmount), 0)
    const used = list.reduce((s, b) => s + Number(b.usedAmount), 0)
    const frozen = list.reduce((s, b) => s + Number(b.frozenAmount), 0)
    const available = list.reduce((s, b) => s + getBudgetAvailable(b), 0)
    const rate = total > 0 ? Math.min(100, Math.round(((used + frozen) / total) * 100)) : 0
    return { ...d, total, used, frozen, available, rate }
  })
})

const filteredPayrolls = computed(() =>
  sourceFilter.value === 'ALL' ? visiblePayrolls.value : visiblePayrolls.value.filter((p) => p.sourceType === sourceFilter.value)
)
const totalFilteredAmount = computed(() => filteredPayrolls.value.reduce((s, p) => s + Number(p.amount || 0), 0))

async function handleAutoCalc() {
  busy.value = true
  try { await store.mutate(() => api.generateMonthlyPayrolls({ month: '2026-11' }, user.value)) }
  finally { busy.value = false }
}

async function handlePayout(payroll) {
  busy.value = true
  try { await store.mutate(() => api.confirmPayrollPayout({ payrollId: payroll.id }, user.value)) }
  finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.source-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.source-amount { font-size: 24px; font-weight: 600; }
.text-text { color: var(--color-text); }
.table-card { overflow: hidden; }
.border-b { border-bottom: 1px solid var(--color-border-light); }
.mt-6 { margin-top: 6px; }

@media (max-width: 960px) {
  .source-grid { grid-template-columns: 1fr; }
}
</style>
