<template>
  <div class="portal-page flex-col gap-20">
    <header class="flex items-center justify-between flex-wrap gap-12">
      <h1 class="text-title">{{ t('nav.contracts') }}</h1>
      <span class="f-pill f-pill-blue tabular-nums">
        {{ t('contracts.weekLogged', { week: currentWeek, used: currentWeekLogged, max: maxHours }) }}
      </span>
    </header>

    <!-- Section 1: Electronic Contracts (Clean cards with expandable Schedule/Milestones) -->
    <section class="flex-col gap-10">
      <h2 class="text-sm fw-600 text-muted">{{ t('contracts.title', { n: myContracts.length }) }}</h2>

      <div v-if="myContracts.length" class="flex-col gap-10">
        <div
          v-for="c in myContracts"
          :key="c.id"
          class="acrylic-card p-16"
          :class="{ 'border-caution': c.status === 'PENDING_SIGN' }"
        >
          <div class="flex items-center justify-between gap-12 flex-wrap">
            <div class="min-w-0">
              <div class="flex items-center gap-8 flex-wrap">
                <span class="f-type-tag" :class="store.getJob(c.jobId)?.type?.toLowerCase()">
                  {{ store.getJob(c.jobId)?.type }}
                </span>
                <span class="fw-600 text-lg">{{ store.getJob(c.jobId)?.title }}</span>
                <span class="f-pill" :class="c.status === 'ACTIVE' ? 'f-pill-success' : 'f-pill-caution'">
                  {{ c.status === 'ACTIVE' ? t('contractStatus.ACTIVE') : t('contractStatus.PENDING_SIGN') }}
                </span>
                <span v-if="daysLeft(c.endDate) <= 15 && c.status === 'ACTIVE'" class="f-pill f-pill-error">
                  {{ t('contracts.daysLeft', { n: Math.max(0, daysLeft(c.endDate)) }) }}
                </span>
              </div>
              <div class="f-meta-line mt-6 tabular-nums">
                <span>{{ c.startDate }} ~ {{ c.endDate }}</span>
                <span class="f-meta-sep">·</span>
                <span>{{ t('unit.hoursPerWeek', { n: c.weeklyHours }) }} · ¥{{ c.hourlyRate }}/h</span>
                <template v-if="feedbackSummaries[c.id]?.count">
                  <span class="f-meta-sep">·</span>
                  <span>{{ t('contracts.feedback', { avg: feedbackSummaries[c.id].avgRating, n: feedbackSummaries[c.id].count }) }}</span>
                </template>
              </div>
            </div>

            <div class="flex items-center gap-8">
              <button
                v-if="hasLinkage(c.jobId)"
                type="button"
                class="f-expand-trigger"
                @click="toggleContract(c.id)"
              >
                <span>{{ store.getJob(c.jobId)?.type === 'TA' ? t('contracts.schedule') : t('contracts.milestones') }}</span>
                <FluentIcon :name="expandedContracts.has(c.id) ? 'chevron-up' : 'chevron-down'" :size="14" />
              </button>
              <button v-if="c.status === 'PENDING_SIGN'" type="button" class="f-btn f-btn-primary f-btn-sm" @click="signingContract = c">
                <FluentIcon name="pen" :size="14" />
                <span>{{ t('contracts.sign') }}</span>
              </button>
              <button v-else type="button" class="f-btn f-btn-sm" @click="store.openArchive(c.id)">{{ t('contracts.archive') }}</button>
            </div>
          </div>

          <!-- Click-to-expand TA Schedule or RA Milestones -->
          <div v-if="expandedContracts.has(c.id)" class="f-expand-panel">
            <div v-if="store.getJob(c.jobId)?.type === 'TA'" class="schedule-grid">
              <div v-for="(slot, idx) in store.getJob(c.jobId)?.extraFields?.scheduleSlots || []" :key="idx" class="schedule-slot">
                <div class="fw-600">{{ slot.day }} {{ slot.time }}</div>
                <div class="text-muted">{{ slot.room }} · {{ slot.task }}</div>
              </div>
            </div>
            <div v-else-if="store.getJob(c.jobId)?.type === 'RA'" class="flex-col gap-6">
              <label v-for="ms in store.getJob(c.jobId)?.extraFields?.milestones || []" :key="ms.id" class="ms-row">
                <input type="checkbox" :checked="ms.done" class="f-checkbox" @change="handleToggleMilestone(c.jobId, ms.id)" />
                <span class="flex-1" :class="{ 'line-through text-muted': ms.done }">{{ ms.title }}</span>
                <span class="text-muted tabular-nums">{{ ms.dueDate }}</span>
              </label>
            </div>
          </div>
        </div>
      </div>
      <div v-else class="acrylic-card">
        <EmptyState icon="file" :description="t('contracts.empty')" />
      </div>
    </section>

    <!-- Section 2: Timesheet Submission + History -->
    <section class="ts-layout">
      <form class="acrylic-card p-20 flex-col gap-12" @submit.prevent="handleTimesheetSubmit">
        <h2 class="text-lg fw-600">{{ t('contracts.tsFormTitle') }}</h2>

        <div class="f-field">
          <label class="f-label">{{ t('contracts.tsPosition') }} <span class="req">*</span></label>
          <select v-model="tsForm.contractId" class="f-select" required>
            <option value="" disabled>{{ t('contracts.tsPickContract') }}</option>
            <option v-for="c in activeSignedContracts" :key="c.id" :value="c.id">
              [{{ store.getJob(c.jobId)?.type }}] {{ store.getJob(c.jobId)?.title }}
            </option>
          </select>
        </div>

        <div class="grid-2">
          <div class="f-field">
            <label class="f-label">{{ t('contracts.tsWeek') }}</label>
            <input v-model.number="tsForm.weekNo" type="number" min="1" :max="currentWeek" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">{{ t('contracts.tsHours') }}</label>
            <input v-model.number="tsForm.hours" type="number" min="0.1" :max="remainingWeekHours" step="0.1" class="f-input tabular-nums" required />
          </div>
        </div>

        <div class="f-banner" :class="tsCheck.ok ? 'info' : 'error'">
          <FluentIcon :name="tsCheck.ok ? 'check-circle' : 'x-circle'" :size="15" />
          <span class="text-xs fw-600">{{ tsCheckMessage }}</span>
        </div>

        <div class="f-field">
          <label class="f-label">{{ t('contracts.tsLog') }} <span class="req">*</span></label>
          <textarea v-model="tsForm.workLog" class="f-textarea" rows="3" required :placeholder="t('contracts.tsLogPlaceholder')" />
        </div>

        <button type="submit" class="f-btn f-btn-primary" :disabled="!tsForm.contractId || !tsCheck.ok || submitting">
          {{ t('contracts.tsSubmit') }}
        </button>
      </form>

      <div class="acrylic-card table-card">
        <div class="p-16 border-b">
          <h2 class="text-lg fw-600">{{ t('contracts.tsHistory', { n: myTimesheets.length }) }}</h2>
        </div>
        <div v-if="myTimesheets.length" class="f-table-wrap">
          <table class="f-table">
            <thead>
              <tr>
                <th>{{ t('contracts.tsWeek') }}</th>
                <th>{{ t('contracts.colJob') }}</th>
                <th>{{ t('contracts.colHours') }}</th>
                <th>{{ t('common.status') }}</th>
                <th>{{ t('contracts.colLog') }}</th>
              </tr>
            </thead>
            <tbody>
              <template v-for="ts in myTimesheets" :key="ts.id">
                <tr>
                  <td class="tabular-nums fw-600">{{ t('unit.weekNo', { n: ts.weekNo }) }}</td>
                  <td class="text-xs fw-600">{{ store.getJob(ts.jobId)?.title }}</td>
                  <td class="tabular-nums fw-600 text-blue">{{ ts.hours }}h</td>
                  <td>
                    <span class="f-pill" :class="ts.status === 'APPROVED' ? 'f-pill-success' : ts.status === 'REJECTED' ? 'f-pill-error' : 'f-pill-caution'">
                      {{ ts.status === 'APPROVED' ? t('tsStatus.APPROVED') : ts.status === 'REJECTED' ? t('tsStatus.REJECTED') : t('tsStatus.PENDING') }}
                    </span>
                  </td>
                  <td>
                    <button type="button" class="f-expand-trigger" @click="toggleTs(ts.id)">
                      <span>{{ expandedTs.has(ts.id) ? t('common.collapse') : t('common.view') }}</span>
                    </button>
                  </td>
                </tr>
                <tr v-if="expandedTs.has(ts.id)" class="expand-row">
                  <td colspan="5" class="text-xs">
                    <div>{{ ts.workLog }}</div>
                    <div v-if="ts.mentorComment" class="text-muted mt-4">{{ t('contracts.mentorFeedback') }}{{ ts.mentorComment }}</div>
                  </td>
                </tr>
              </template>
            </tbody>
          </table>
        </div>
        <EmptyState v-else icon="clock" :description="t('contracts.tsEmpty')" />
      </div>
    </section>

    <ContractSignModal v-if="signingContract" :contract="signingContract" @close="signingContract = null" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, watchEffect, watch } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { getStudentWeekLoggedHours, validateTimesheetSubmission, daysUntil, getMaxWeeklyHours } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import { useI18n } from '@/i18n'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import ContractSignModal from '@/components/ContractSignModal.vue'

const store = useAppStore()
const { t } = useI18n()
const student = computed(() => store.currentUser)
const currentWeek = computed(() => store.db.meta.currentWeekNo || 1)
const maxHours = computed(() => getMaxWeeklyHours(store.db, student.value))

const myContracts = computed(() => store.db.contracts.filter((c) => c.studentId === student.value?.id))
const activeSignedContracts = computed(() => myContracts.value.filter((c) => c.status === 'ACTIVE'))
const myTimesheets = computed(() => store.db.timesheets.filter((t) => t.studentId === student.value?.id))
const currentWeekLogged = computed(() => (student.value ? getStudentWeekLoggedHours(store.db, student.value.id, currentWeek.value) : 0))

// P1：服务对象评价汇总（只有平均分和条数，看不到单条评价；仅 http 模式）
const feedbackSummaries = ref({})
watch(
  () => activeSignedContracts.value.map((c) => c.id).join(','),
  async () => {
    if (!isHttpMode) return
    const result = {}
    for (const c of activeSignedContracts.value) {
      const res = await api.getFeedbackSummary({ contractId: c.id })
      if (res.code === 200 && res.data) result[c.id] = res.data
    }
    feedbackSummaries.value = result
  },
  { immediate: true }
)

const signingContract = ref(null)
const submitting = ref(false)
const expandedContracts = ref(new Set())
const expandedTs = ref(new Set())

const tsForm = reactive({ contractId: '', weekNo: currentWeek.value, hours: null, milestoneRef: '', workLog: '' })

watch(currentWeek, (week, previous) => {
  if (tsForm.weekNo === previous || tsForm.weekNo > week) tsForm.weekNo = week
})
const remainingWeekHours = computed(() => Math.max(0, Number((maxHours.value -
  getStudentWeekLoggedHours(store.db, student.value?.id, tsForm.weekNo)).toFixed(1))))

watchEffect(() => {
  if (!tsForm.contractId && activeSignedContracts.value.length) tsForm.contractId = activeSignedContracts.value[0].id
})

const tsCheck = computed(() =>
  validateTimesheetSubmission(store.db, student.value || { id: '', maxWeeklyHours: 20 }, tsForm.weekNo, tsForm.hours)
)

// 工时校验提示：用校验结果里的数字按当前语言拼文案（规则判断仍由 validateTimesheetSubmission 完成）
const tsCheckMessage = computed(() => {
  const c = tsCheck.value
  const hours = Number(tsForm.hours || 0)
  if (hours <= 0) return t('contracts.tsCheckZero')
  const params = { week: tsForm.weekNo, total: c.projectedHours, existing: c.existingHours, n: hours, max: c.maxHours }
  return c.ok ? t('contracts.tsCheckOk', params) : t('contracts.tsCheckOver', params)
})

function hasLinkage(jobId) {
  const j = store.getJob(jobId)
  return (j?.type === 'TA' && j.extraFields?.scheduleSlots?.length) || (j?.type === 'RA' && j.extraFields?.milestones?.length)
}

function toggleContract(id) {
  const s = new Set(expandedContracts.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedContracts.value = s
}

function toggleTs(id) {
  const s = new Set(expandedTs.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedTs.value = s
}

function daysLeft(dateStr) { return daysUntil(dateStr) }

async function handleToggleMilestone(jobId, milestoneId) {
  await store.mutate(() => api.toggleMilestone({ jobId, milestoneId }, student.value))
}

async function handleTimesheetSubmit() {
  if (!tsCheck.value.ok) return
  submitting.value = true
  try {
    const res = await store.mutate(() => api.submitTimesheet({ ...tsForm }, student.value))
    if (res.code === 200) tsForm.workLog = ''
  } finally { submitting.value = false }
}
</script>

<style scoped>
.portal-page { max-width: 1200px; margin: 24px auto 0; padding: 0 24px; }
.schedule-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 8px; }
.schedule-slot { padding: 8px 10px; background: #ffffff; border: 1px solid var(--color-blue-border); border-left: 3px solid var(--color-fluent-blue); border-radius: 4px; }
.ms-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: #ffffff; border: 1px solid var(--color-border-light); border-radius: 4px; cursor: pointer; }
.f-checkbox { width: 15px; height: 15px; accent-color: var(--color-fluent-blue); }
.line-through { text-decoration: line-through; }
.ts-layout { display: grid; grid-template-columns: 380px 1fr; gap: 20px; align-items: start; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.table-card { overflow: hidden; }
.expand-row { background: rgba(243, 242, 241, 0.55); }
.border-b { border-bottom: 1px solid var(--color-border-light); }
.flex-1 { flex: 1; }
.min-w-0 { min-width: 0; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }

@media (max-width: 960px) {
  .portal-page { padding: 0 16px; }
  .ts-layout { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 768px) {
  .portal-page { margin-top: 16px; }
  .ts-layout > * { min-width: 0; }
  .grid-2 { grid-template-columns: 1fr 1fr; }
  /* 合同卡片：操作按钮换行并铺满 */
  .acrylic-card.p-16 > .flex > .flex.items-center.gap-8 { flex-wrap: wrap; width: 100%; }
  .acrylic-card.p-16 > .flex > .flex.items-center.gap-8 > .f-btn { flex: 1 1 140px; }
  .schedule-grid { grid-template-columns: minmax(0, 1fr); }
  .ms-row { min-height: 44px; font-size: 14px; }
  .f-checkbox { width: 20px; height: 20px; }
  form .f-btn-primary { width: 100%; }
}
</style>
