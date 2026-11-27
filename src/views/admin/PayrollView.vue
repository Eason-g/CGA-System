<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div class="flex items-center gap-12">
        <h1 class="text-title">{{ $t('admin.payroll.title') }}</h1>
        <button type="button" class="f-expand-trigger" @click="showRule = !showRule">
          <span>{{ $t('admin.payroll.formula') }}</span>
          <FluentIcon :name="showRule ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <button v-if="canFinance" type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleAutoCalc">
        <FluentIcon name="sparkles" :size="15" />
        <span>{{ $t('admin.payroll.calc', { month: currentMonth }) }}</span>
      </button>
    </header>

    <div v-if="showRule" class="f-expand-panel" style="margin-top: -8px;">
      {{ $t('admin.payroll.rule') }}<template v-if="isHttpMode">{{ $t('admin.payroll.ruleHttp') }}</template><template v-else>{{ $t('admin.payroll.ruleMock') }}</template>
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
        <div class="text-xs text-muted mt-6 tabular-nums">{{ $t('admin.payroll.usedFrozen', { u: s.used.toLocaleString(), f: s.frozen.toLocaleString() }) }}</div>
      </div>
    </section>

    <section class="acrylic-card table-card">
      <div class="flex items-center justify-between p-16 border-b flex-wrap gap-12">
        <div class="f-segmented">
          <button type="button" :class="{ active: sourceFilter === 'ALL' }" @click="sourceFilter = 'ALL'">{{ $t('admin.common.all') }}</button>
          <button type="button" :class="{ active: sourceFilter === 'TA_FUND' }" @click="sourceFilter = 'TA_FUND'">{{ $t('admin.payroll.filters.TA_FUND') }}</button>
          <button type="button" :class="{ active: sourceFilter === 'RA_PROJECT' }" @click="sourceFilter = 'RA_PROJECT'">{{ $t('admin.payroll.filters.RA_PROJECT') }}</button>
          <button type="button" :class="{ active: sourceFilter === 'GA_FUND' }" @click="sourceFilter = 'GA_FUND'">{{ $t('admin.payroll.filters.GA_FUND') }}</button>
        </div>
        <div class="text-xs text-muted tabular-nums">
          {{ $t('admin.payroll.total') }}<strong class="text-blue">¥{{ totalFilteredAmount.toLocaleString(locale, { minimumFractionDigits: 2 }) }}</strong>
        </div>
      </div>

      <div v-if="filteredPayrolls.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.common.student') }}</th>
              <th>{{ $t('admin.common.job') }}</th>
              <th>{{ $t('admin.common.month') }}</th>
              <th>{{ $t('admin.payroll.colDetail') }}</th>
              <th>{{ $t('admin.payroll.colAmount') }}</th>
              <th>{{ $t('admin.common.status') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
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
                  {{ statusLabel(FINANCE_STATUS_MAP, p.financeStatus) }}
                </span>
                <div v-if="p.financeStatus === 'SUBMITTED' && p.financeSerialNo" class="text-xs text-muted mt-4 tabular-nums">
                  {{ $t('admin.payroll.serial', { n: p.financeSerialNo }) }}
                </div>
                <div v-if="['PAY_FAILED', 'SUBMITTED'].includes(p.financeStatus) && p.failReason" class="text-xs text-error mt-4 fail-reason">
                  {{ p.failReason }}
                </div>
              </td>
              <td>
                <!-- http 模式：两步发放（提交财务 → 财务回写）；mock 模式保留一步打款 -->
                <template v-if="isHttpMode && canPay && ['PENDING_PAY', 'PAY_FAILED', 'SUBMITTED'].includes(p.financeStatus)">
                  <button
                    v-if="p.financeStatus !== 'SUBMITTED'"
                    type="button"
                    class="f-btn f-btn-primary f-btn-sm"
                    :disabled="busy"
                    @click="handleSubmitFinance(p)"
                  >
                    {{ p.financeStatus === 'PAY_FAILED' ? $t('admin.payroll.resubmit') : $t('admin.payroll.submitFinance') }}
                  </button>
                  <div v-else class="flex gap-6 flex-wrap">
                    <button type="button" class="f-btn f-btn-sm" :disabled="busy" :title="$t('admin.payroll.simOkTitle')" @click="handleSimulate(p, true)">
                      {{ $t('admin.payroll.simOk') }}
                    </button>
                    <button type="button" class="f-btn f-btn-danger-outline f-btn-sm" :disabled="busy" :title="$t('admin.payroll.simFailTitle')" @click="handleSimulate(p, false)">
                      {{ $t('admin.payroll.simFail') }}
                    </button>
                  </div>
                </template>
                <button
                  v-else-if="!isHttpMode && p.financeStatus === 'PENDING_PAY' && canFinance"
                  type="button"
                  class="f-btn f-btn-primary f-btn-sm"
                  :disabled="busy"
                  @click="handlePayout(p)"
                >
                  {{ $t('admin.payroll.confirmPay') }}
                </button>
                <button
                  v-else-if="p.financeStatus === 'PENDING_CALC' && canFinance"
                  type="button"
                  class="f-btn f-btn-sm"
                  :disabled="busy"
                  @click="handleAutoCalc"
                >
                  {{ $t('admin.payroll.calcBtn') }}
                </button>
                <button v-else type="button" class="f-btn-link text-xs" @click="store.openArchive(p.contractId)">{{ $t('admin.common.archive') }}</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="wallet" :description="$t('admin.payroll.empty')" />
    </section>
  </div>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, getBudgetAvailable, FINANCE_STATUS_MAP } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useI18n } from '@/i18n'
import { statusLabel } from './adminI18n'

const store = useAppStore()
const { t, locale } = useI18n()
const user = computed(() => store.currentUser)

const sourceFilter = ref('ALL')
const showRule = ref(false)
const busy = ref(false)

const canFinance = computed(() => ['SCHOOL_ADMIN', 'DEPT_ADMIN'].includes(user.value?.role))
const visibleBudgets = computed(() => filterByRoleDept(store.db, store.db.budgets, user.value, 'BUDGET'))
const visiblePayrolls = computed(() => filterByRoleDept(store.db, store.db.payrolls, user.value, 'PAYROLL'))

const sourceSummaries = computed(() => {
  const defs = [
    { sourceType: 'TA_FUND' },
    { sourceType: 'RA_PROJECT' },
    { sourceType: 'GA_FUND' }
  ].map((d) => ({ ...d, label: t(`admin.payroll.sources.${d.sourceType}`) }))
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

const currentMonth = computed(() => store.db.meta?.currentMonth)
// 财务发放只由校级管理员（兼财务）操作
const canPay = computed(() => user.value?.role === 'SCHOOL_ADMIN')

async function handleAutoCalc() {
  busy.value = true
  try { await store.mutate(() => api.generateMonthlyPayrolls({ month: currentMonth.value }, user.value)) }
  finally { busy.value = false }
}

async function handleSubmitFinance(payroll) {
  busy.value = true
  try { await store.mutate(() => materialFinanceApi.submitPayrollToFinance(payroll.id)) }
  finally { busy.value = false }
}

async function handleSimulate(payroll, success) {
  busy.value = true
  try {
    await store.mutate(() =>
      materialFinanceApi.simulateFinanceCallback(payroll.id, success, success ? '' : t('admin.payroll.simFailReason'))
    )
  } finally { busy.value = false }
}

// 有“已提交财务”的单据时定时刷新，等待模拟财务系统自动回写（默认几秒后）
let pollTimer = null
const hasSubmitted = computed(() => isHttpMode && visiblePayrolls.value.some((p) => p.financeStatus === 'SUBMITTED'))
watch(
  hasSubmitted,
  (v) => {
    if (v && !pollTimer) {
      pollTimer = setInterval(() => store.refreshSnapshot(), 4000)
    } else if (!v && pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

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
.mt-4 { margin-top: 4px; }
.gap-6 { gap: 6px; }
.fail-reason { max-width: 220px; white-space: normal; }

@media (max-width: 960px) {
  .source-grid { grid-template-columns: 1fr; }
}
</style>
