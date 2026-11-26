<template>
  <div class="portal-page flex-col gap-20">
    <header class="flex items-center justify-between flex-wrap gap-12">
      <h1 class="text-title">薪酬记录</h1>
    </header>

    <!-- Summary Row -->
    <section class="summary-grid">
      <div class="acrylic-card p-20">
        <div class="text-xs text-muted">累计已发放实得</div>
        <div class="sum-val text-blue tabular-nums mt-6">¥{{ paidTotal.toFixed(2) }}</div>
      </div>
      <div class="acrylic-card p-20">
        <div class="text-xs text-muted">待财务打款 / 核算中</div>
        <div class="sum-val tabular-nums mt-6">¥{{ pendingTotal.toFixed(2) }}</div>
      </div>
      <div class="acrylic-card p-20">
        <div class="text-xs text-muted">已结算工时总计</div>
        <div class="sum-val tabular-nums mt-6">{{ totalHours }} 小时</div>
      </div>
    </section>

    <section class="acrylic-card table-card">
      <div v-if="myPayrolls.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>结算月份</th>
              <th>在岗岗位与经费来源</th>
              <th>自动核算明细</th>
              <th>实发金额</th>
              <th>发放状态与财务凭证</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="p in myPayrolls" :key="p.id">
              <td class="tabular-nums fw-600">{{ p.month }}</td>
              <td>
                <div class="fw-600 text-sm">{{ store.getJob(p.jobId)?.title }}</div>
                <div class="text-xs text-muted mt-2">{{ store.getBudget(p.budgetId)?.sourceLabel }} · {{ store.getBudget(p.budgetId)?.code }}</div>
              </td>
              <td class="text-xs tabular-nums">
                {{ p.totalHours }}h × ¥{{ p.hourlyRate }}/h × <strong class="text-blue">{{ p.coefficient }}x</strong>
              </td>
              <td class="tabular-nums fw-600 text-blue">¥{{ p.amount.toFixed(2) }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${FINANCE_STATUS_MAP[p.financeStatus]?.tone || 'neutral'}`">
                  {{ FINANCE_STATUS_MAP[p.financeStatus]?.label || p.financeStatus }}
                </span>
                <div v-if="p.voucherNo" class="text-xs text-muted mt-2 tabular-nums">{{ p.voucherNo }}</div>
              </td>
              <td>
                <button type="button" class="f-btn-link text-xs" @click="store.openArchive(p.contractId)">
                  查看电子归档
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="wallet" description="暂无薪酬结算记录，工时通过导师审核后将自动进入月度核算" />
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { FINANCE_STATUS_MAP } from '@/services/ruleEngine'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const myPayrolls = computed(() =>
  store.db.payrolls.filter((p) => p.studentId === store.currentUser?.id)
)

const paidTotal = computed(() =>
  myPayrolls.value.filter((p) => p.financeStatus === 'PAID').reduce((s, p) => s + Number(p.amount), 0)
)
const pendingTotal = computed(() =>
  myPayrolls.value.filter((p) => p.financeStatus !== 'PAID').reduce((s, p) => s + Number(p.amount), 0)
)
const totalHours = computed(() =>
  myPayrolls.value.reduce((s, p) => s + Number(p.totalHours), 0)
)
</script>

<style scoped>
.portal-page {
  max-width: 1200px;
  margin: 24px auto 0;
  padding: 0 24px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.sum-val {
  font-size: 28px;
  font-weight: 600;
}

.table-card { overflow: hidden; }
.items-end { align-items: flex-end; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }

@media (max-width: 960px) {
  .portal-page {
    padding: 0 16px;
  }
  .summary-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .portal-page { margin-top: 16px; }
  .summary-grid { gap: 10px; }
  .summary-grid .p-20 { padding: 14px 16px; display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
  .summary-grid .mt-6 { margin-top: 0; }
  .sum-val { font-size: 22px; }
  /* 表格只在卡片内横向滚动 */
  .f-table { min-width: 640px; }
}
</style>
