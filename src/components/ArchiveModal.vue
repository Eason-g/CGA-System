<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal xl archive-printable" role="dialog" aria-modal="true" :aria-label="t('archive.dialogLabel')">
      <header class="f-modal-header no-print">
        <div>
          <div class="text-xs text-muted">{{ t('archive.headerHint') }}</div>
          <h2 class="text-sub mt-4">{{ t('archive.headerTitle', { id: contractId }) }}</h2>
        </div>
        <div class="flex items-center gap-8">
          <button type="button" class="f-btn f-btn-primary" @click="printArchive">
            <FluentIcon name="print" :size="16" />
            <span>{{ t('archive.print') }}</span>
          </button>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" :aria-label="t('common.close')" @click="$emit('close')">
            <FluentIcon name="close" :size="18" />
          </button>
        </div>
      </header>

      <div v-if="contract" class="f-modal-body archive-body">
        <div class="archive-banner">
          <div>
            <div class="text-xs text-muted">{{ store.schoolName }} · {{ t('archive.platform') }}</div>
            <h1 class="archive-h1">{{ t('archive.docTitle') }}</h1>
          </div>
          <div class="text-right text-xs text-muted tabular-nums">
            <div>{{ t('archive.archiveNo', { id: contract.id }) }}</div>
            <div>{{ t('archive.generatedOn', { date: todayStr }) }}</div>
          </div>
        </div>

        <!-- 1. Basic & Contract -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">{{ t('archive.sec1') }}</h3>
          <div class="archive-kv">
            <div><span class="k">{{ t('archive.student') }}</span><strong>{{ student?.name }}</strong> ({{ student?.studentNo }} · {{ student?.degree }})</div>
            <div><span class="k">{{ t('archive.intlStatus') }}</span><strong>{{ student?.isInternational ? t('archive.intlYes', { visa: student.visaType }) : t('archive.intlNo') }}</strong></div>
            <div><span class="k">{{ t('archive.dept') }}</span><strong>{{ store.getDeptName(contract.deptId) }}</strong></div>
            <div><span class="k">{{ t('archive.job') }}</span><strong>[{{ job?.type }}] {{ job?.title }}</strong></div>
            <div><span class="k">{{ t('archive.period') }}</span><strong class="tabular-nums">{{ contract.startDate }} ~ {{ contract.endDate }}</strong></div>
            <div><span class="k">{{ t('sign.hoursPay') }}</span><strong class="tabular-nums">{{ t('unit.hoursPerWeek', { n: contract.weeklyHours }) }} · ¥{{ contract.hourlyRate }}/h</strong></div>
            <div><span class="k">{{ t('archive.fund') }}</span><strong>{{ budget?.name }} ({{ budget?.code }})</strong></div>
            <div><span class="k">{{ t('archive.signedAt') }}</span><strong class="tabular-nums">{{ contract.signedAt || t('contractStatus.PENDING_SIGN') }}</strong></div>
          </div>

          <div class="signature-box mt-12">
            <div class="text-xs text-muted">{{ t('archive.signature') }}</div>
            <div class="flex items-center gap-16 mt-6">
              <img v-if="contract.signatureDataUrl" :src="contract.signatureDataUrl" :alt="t('archive.signatureAlt')" class="sig-img" />
              <div v-else class="sig-text-stamp">{{ contract.signatureText || t('archive.unsigned') }}</div>
              <div class="text-xs text-muted">
                <div>{{ t('archive.pledge') }}{{ contract.complianceAgreed ? t('archive.pledgeYes') : t('archive.pledgeNo') }}</div>
                <div v-if="student?.isInternational">{{ t('archive.intlDecl') }}{{ contract.complianceAgreed ? t('archive.intlDeclYes') : t('contractStatus.PENDING_SIGN') }}</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 2. Application & Compliance -->
        <section v-if="application" class="archive-sec">
          <h3 class="archive-sec-title">{{ t('archive.sec2') }}</h3>
          <div class="text-xs mb-8">
            {{ t('archive.appNo') }}<span class="fw-600">{{ application.id }}</span> · {{ t('archive.interviewScore') }}<span class="fw-600">{{ application.score ?? '-' }}</span> · {{ t('archive.specialFlag') }}<span class="fw-600">{{ application.isSpecialApproval ? t('archive.specialYes') : t('archive.specialNo') }}</span>
          </div>
          <div class="archive-checks">
            <div v-for="c in application.complianceCheckResult?.checks || []" :key="c.key" class="text-xs">
              [{{ c.passed ? '✓' : '✕' }}] <strong>{{ localized(c, 'label') }}</strong>{{ t('common.colon') }}{{ localized(c, 'detail') }}
            </div>
          </div>
        </section>

        <!-- 3. Timesheets -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">{{ t('archive.sec3', { n: timesheets.length, h: totalApprovedHours }) }}</h3>
          <table class="archive-table">
            <thead>
              <tr>
                <th>{{ t('contracts.tsWeek') }}</th>
                <th>{{ t('archive.colMonth') }}</th>
                <th>{{ t('archive.colHours') }}</th>
                <th>{{ t('archive.colLog') }}</th>
                <th>{{ t('common.status') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="ts in timesheets" :key="ts.id">
                <td class="tabular-nums">{{ t('unit.weekNo', { n: ts.weekNo }) }}</td>
                <td class="tabular-nums">{{ ts.month }}</td>
                <td class="tabular-nums fw-600">{{ ts.hours }}</td>
                <td>{{ ts.workLog }}</td>
                <td>{{ ts.status === 'APPROVED' ? t('tsStatus.APPROVED') : ts.status === 'REJECTED' ? t('tsStatus.REJECTED') : t('tsStatus.PENDING') }}</td>
              </tr>
              <tr v-if="!timesheets.length">
                <td colspan="5" class="text-muted">{{ t('contracts.tsEmpty') }}</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- 4. Evaluations & Payrolls -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">{{ t('archive.sec4') }}</h3>
          <div class="archive-two-col">
            <div>
              <div class="text-xs fw-600 mb-6">{{ t('archive.evaluations', { n: evaluations.length }) }}</div>
              <div v-for="ev in evaluations" :key="ev.id" class="archive-mini-card">
                <div class="flex justify-between text-xs fw-600">
                  <span>{{ ev.period }}</span>
                  <span>{{ t('archive.evalScore', { score: ev.totalScore, grade: ev.grade, k: ev.coefficient }) }}</span>
                </div>
                <div class="text-xs text-muted mt-4">{{ t('archive.renewal') }}{{ ev.renewalEligible ? t('archive.renewYes') : t('archive.renewNo') }} · {{ ev.comment }}</div>
              </div>
              <div v-if="!evaluations.length" class="text-xs text-muted">{{ t('archive.noEval') }}</div>
            </div>

            <div>
              <div class="text-xs fw-600 mb-6">{{ t('archive.payrolls', { n: payrolls.length }) }}</div>
              <div v-for="p in payrolls" :key="p.id" class="archive-mini-card">
                <div class="flex justify-between text-xs fw-600 tabular-nums">
                  <span>{{ p.month }} ({{ p.totalHours }}h × ¥{{ p.hourlyRate }} × {{ p.coefficient }}x)</span>
                  <span class="text-blue">¥{{ p.amount.toFixed(2) }}</span>
                </div>
                <div class="text-xs text-muted mt-4">
                  {{ t('archive.payStatus') }}{{ p.financeStatus === 'PAID' ? t('archive.paidWithVoucher', { no: p.voucherNo }) : localized(FINANCE_STATUS_MAP[p.financeStatus], 'label') || localized(FINANCE_STATUS_MAP.PENDING_CALC, 'label') }}
                </div>
              </div>
              <div v-if="!payrolls.length" class="text-xs text-muted">{{ t('archive.noPayroll') }}</div>
            </div>
          </div>
        </section>

        <!-- 5. Audit Chain -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">{{ t('archive.sec5', { n: relatedLogs.length }) }}</h3>
          <table class="archive-table">
            <thead>
              <tr>
                <th>{{ t('archive.colTime') }}</th>
                <th>{{ t('archive.colOperator') }}</th>
                <th>{{ t('archive.colAction') }}</th>
                <th>{{ t('archive.colOpinion') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in relatedLogs" :key="log.id">
                <td class="tabular-nums">{{ log.timestamp }}</td>
                <td>{{ log.operatorName }} ({{ log.operatorRole }})</td>
                <td class="fw-600">{{ log.action }}</td>
                <td>{{ log.opinion }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { FINANCE_STATUS_MAP, getTodayStr } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  contractId: { type: String, required: true }
})

defineEmits(['close'])
const store = useAppStore()
const { t, localized } = useI18n()
const todayStr = getTodayStr()

const contract = computed(() => store.db.contracts.find((c) => c.id === props.contractId))
const student = computed(() => (contract.value ? store.getUser(contract.value.studentId) : null))
const job = computed(() => (contract.value ? store.getJob(contract.value.jobId) : null))
const budget = computed(() => (job.value ? store.getBudget(job.value.budgetId) : null))
const application = computed(() =>
  contract.value?.applicationId ? store.db.applications.find((a) => a.id === contract.value.applicationId) : null
)
const timesheets = computed(() => store.db.timesheets.filter((t) => t.contractId === props.contractId))
const totalApprovedHours = computed(() =>
  timesheets.value.filter((t) => t.status === 'APPROVED').reduce((s, t) => s + Number(t.hours), 0)
)
const evaluations = computed(() => store.db.evaluations.filter((e) => e.contractId === props.contractId))
const payrolls = computed(() => store.db.payrolls.filter((p) => p.contractId === props.contractId))

const relatedLogs = computed(() => {
  const ids = new Set([
    props.contractId,
    contract.value?.jobId,
    contract.value?.applicationId,
    ...timesheets.value.map((t) => t.id),
    ...evaluations.value.map((e) => e.id),
    ...payrolls.value.map((p) => p.id)
  ].filter(Boolean))
  return store.db.auditLogs.filter((l) => ids.has(l.targetId))
})

function printArchive() {
  window.print()
}
</script>

<style scoped>
.archive-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.archive-banner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 2px solid var(--color-fluent-blue);
}

.archive-h1 {
  font-size: 22px;
  margin-top: 4px;
}

.archive-sec-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-fluent-blue);
  margin-bottom: 10px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-border-light);
}

.archive-kv {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  font-size: 13px;
}

.archive-kv .k {
  color: var(--color-muted);
}

.signature-box {
  padding: 12px 14px;
  background: #faf9f8;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
}

.sig-img {
  height: 54px;
  border: 1px solid var(--color-border-light);
  background: #ffffff;
  padding: 4px 8px;
}

.sig-text-stamp {
  font-family: Georgia, serif;
  font-style: italic;
  font-size: 20px;
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  background: #ffffff;
}

.archive-checks {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: #faf9f8;
  border-radius: 4px;
}

.archive-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.archive-table th,
.archive-table td {
  border: 1px solid var(--color-border-light);
  padding: 6px 10px;
  text-align: left;
}

.archive-table th {
  background: #f3f2f1;
  color: var(--color-muted);
}

.archive-two-col {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.archive-mini-card {
  padding: 10px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
  margin-bottom: 8px;
}

.text-right { text-align: right; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mb-6 { margin-bottom: 6px; }
.mb-8 { margin-bottom: 8px; }

@media (max-width: 768px) {
  .f-modal-header { flex-wrap: wrap; }
  .archive-banner { flex-direction: column; align-items: flex-start; gap: 8px; }
  .archive-banner .text-right { text-align: left; }
  .archive-h1 { font-size: 18px; }
  .archive-kv { font-size: 14px; }
  .archive-kv > div { overflow-wrap: anywhere; }
  .signature-box > .flex { flex-wrap: wrap; }
  .sig-img { max-width: 100%; height: auto; max-height: 64px; }
  /* 档案表格只在自身范围内横向滚动 */
  .archive-table { display: block; overflow-x: auto; -webkit-overflow-scrolling: touch; font-size: 13px; }
  .archive-table th, .archive-table td { white-space: nowrap; }
  .archive-table td:nth-child(4) { white-space: normal; min-width: 180px; }
}

@media (max-width: 720px) {
  .archive-kv,
  .archive-two-col {
    grid-template-columns: 1fr;
  }
}
</style>
