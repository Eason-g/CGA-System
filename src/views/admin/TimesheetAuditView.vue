<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ $t('admin.timesheets.title') }}</h1>
      <div class="f-segmented">
        <button type="button" :class="{ active: statusTab === 'ALL' }" @click="statusTab = 'ALL'">{{ $t('admin.common.all') }}</button>
        <button type="button" :class="{ active: statusTab === 'PENDING' }" @click="statusTab = 'PENDING'">{{ $t('admin.timesheets.tabPending', { n: pendingCount }) }}</button>
        <button type="button" :class="{ active: statusTab === 'APPROVED' }" @click="statusTab = 'APPROVED'">{{ $t('admin.timesheets.tabApproved') }}</button>
      </div>
    </header>

    <!-- Collapsible Anomaly Bar -->
    <section v-if="anomalies.length" class="acrylic-card p-16">
      <div class="flex items-center justify-between cursor-pointer" @click="showAnomalies = !showAnomalies">
        <div class="flex items-center gap-8">
          <span class="f-dot-error" />
          <span class="fw-600 text-sm">{{ $t('admin.timesheets.anomalyCount', { n: anomalies.length }) }}</span>
        </div>
        <button type="button" class="f-expand-trigger">
          <span>{{ showAnomalies ? $t('admin.common.collapse') : $t('admin.timesheets.showAll') }}</span>
          <FluentIcon :name="showAnomalies ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <div v-if="showAnomalies" class="anomaly-grid mt-12">
        <div v-for="w in anomalies" :key="w.id" class="anomaly-pill" :class="w.severity">
          <div class="fw-600 text-xs">{{ localized(w, 'title') }}</div>
          <div class="text-xs text-muted mt-4">{{ localized(w, 'detail') }}</div>
        </div>
      </div>
    </section>

    <section class="acrylic-card table-card">
      <div v-if="filteredTimesheets.length" class="f-table-wrap">
        <table class="f-table f-table-sticky-action">
          <thead>
            <tr>
              <th>{{ $t('admin.common.student') }}</th>
              <th>{{ $t('admin.timesheets.colJob') }}</th>
              <th>{{ $t('admin.timesheets.colWeek') }}</th>
              <th>{{ $t('admin.timesheets.colLoad') }}</th>
              <th>{{ $t('admin.common.status') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ts in filteredTimesheets" :key="ts.id">
              <td>
                <div class="flex items-center gap-6">
                  <span class="fw-600">{{ store.getUser(ts.studentId)?.name }}</span>
                  <span v-if="store.getUser(ts.studentId)?.isInternational" class="f-pill f-pill-blue">{{ $t('admin.common.intl') }}</span>
                </div>
              </td>
              <td>{{ store.getJob(ts.jobId)?.title }}</td>
              <td class="tabular-nums fw-600">{{ $t('admin.timesheets.weekVal', { w: ts.weekNo, h: ts.hours }) }}</td>
              <td style="min-width: 140px;">
                <div class="text-xs text-muted tabular-nums">{{ weekTotal(ts.studentId, ts.weekNo) }}/{{ maxCap(ts.studentId) }}h</div>
                <div class="f-progress-track mt-4">
                  <div
                    class="f-progress-fill"
                    :class="weekRatio(ts.studentId, ts.weekNo) >= 100 ? 'error' : weekRatio(ts.studentId, ts.weekNo) >= 80 ? 'caution' : ''"
                    :style="{ width: Math.min(100, weekRatio(ts.studentId, ts.weekNo)) + '%' }"
                  />
                </div>
              </td>
              <td>
                <span
                  class="f-pill"
                  :class="ts.status === 'APPROVED' ? 'f-pill-success' : ts.status === 'REJECTED' ? 'f-pill-error' : 'f-pill-caution'"
                >
                  {{ $t(`admin.tsStatus.${['APPROVED', 'REJECTED'].includes(ts.status) ? ts.status : 'PENDING'}`) }}
                </span>
              </td>
              <td>
                <button
                  type="button"
                  class="f-btn f-btn-sm"
                  :class="ts.status === 'PENDING' && canAudit ? 'f-btn-primary' : ''"
                  @click="openAuditDrawer(ts)"
                >
                  {{ ts.status === 'PENDING' && canAudit ? $t('admin.timesheets.review') : $t('admin.common.details') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="clock" :description="$t('admin.timesheets.empty')" />
    </section>

    <AuditDrawer
      v-if="selectedTs"
      :title="$t('admin.timesheets.drawerTitle', { w: selectedTs.weekNo, h: selectedTs.hours })"
      :subtitle="`${selectedTs.id} · ${store.getUser(selectedTs.studentId)?.name}`"
      :audit-logs="selectedTsLogs"
      @close="selectedTsId = null"
    >
      <div class="drawer-card">
        <div class="fw-600 text-sm">{{ store.getJob(selectedTs.jobId)?.title }}</div>
        <div class="text-xs text-muted mt-6 tabular-nums">
          {{ $t('admin.timesheets.weekTotal', { t: weekTotal(selectedTs.studentId, selectedTs.weekNo), m: maxCap(selectedTs.studentId) }) }}
        </div>
        <div class="quote-box mt-10 text-xs">{{ selectedTs.workLog }}</div>
        <div v-if="selectedTs.milestoneRef" class="text-xs text-blue mt-8">{{ $t('admin.timesheets.milestone', { m: selectedTs.milestoneRef }) }}</div>
        <div v-if="selectedTs.mentorComment" class="text-xs text-muted mt-6">{{ $t('admin.timesheets.comment', { c: selectedTs.mentorComment }) }}</div>
      </div>

      <template v-if="selectedTs.status === 'PENDING' && canAudit" #footer>
        <input v-model="auditComment" type="text" class="f-input" :placeholder="$t('admin.timesheets.commentPh')" />
        <div class="flex gap-8 justify-end">
          <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleReview('REJECT')">{{ $t('admin.common.reject') }}</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleReview('APPROVE')">{{ $t('admin.timesheets.approve') }}</button>
        </div>
      </template>
    </AuditDrawer>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, scanAnomalies, getStudentWeekLoggedHours, getStudentHoursSummary } from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuditDrawer from '@/components/AuditDrawer.vue'
import { useI18n } from '@/i18n'

const store = useAppStore()
const { localized } = useI18n()
const user = computed(() => store.currentUser)

const statusTab = ref('ALL')
const showAnomalies = ref(false)
const selectedTsId = ref(null)
const auditComment = ref('')
const busy = ref(false)

const canAudit = computed(() => ['MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN'].includes(user.value?.role))
const anomalies = computed(() => scanAnomalies(store.db, user.value))
const visibleTimesheets = computed(() => filterByRoleDept(store.db, store.db.timesheets, user.value, 'TIMESHEET'))
const pendingCount = computed(() => visibleTimesheets.value.filter((t) => t.status === 'PENDING').length)

const filteredTimesheets = computed(() =>
  statusTab.value === 'ALL' ? visibleTimesheets.value : visibleTimesheets.value.filter((t) => t.status === statusTab.value)
)

const selectedTs = computed(() => store.db.timesheets.find((t) => t.id === selectedTsId.value) || null)
const selectedTsLogs = computed(() =>
  selectedTsId.value ? store.db.auditLogs.filter((l) => l.targetType === 'TIMESHEET' && l.targetId === selectedTsId.value) : []
)

function weekTotal(studentId, weekNo) { return getStudentWeekLoggedHours(store.db, studentId, weekNo) }
function maxCap(studentId) { return getStudentHoursSummary(store.db, store.getUser(studentId)).max }
function weekRatio(studentId, weekNo) { return Math.round((weekTotal(studentId, weekNo) / maxCap(studentId)) * 100) }

function openAuditDrawer(ts) {
  selectedTsId.value = ts.id
  auditComment.value = ''
}

async function handleReview(decision) {
  busy.value = true
  try {
    await store.mutate(() => api.reviewTimesheet({ timesheetId: selectedTs.value.id, decision, comment: auditComment.value.trim() }, user.value))
    auditComment.value = ''
  } finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.cursor-pointer { cursor: pointer; }
.anomaly-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 8px; }
.anomaly-pill { padding: 10px 12px; border-radius: 4px; background: rgba(255, 255, 255, 0.8); border: 1px solid var(--color-border-light); border-left: 3px solid var(--color-caution); }
.anomaly-pill.error { border-left-color: var(--color-error); }
.table-card { overflow: hidden; }
.drawer-card { padding: 14px 16px; background: rgba(255, 255, 255, 0.72); border: 1px solid var(--color-border-light); border-radius: 6px; }
.quote-box { padding: 8px 10px; background: #ffffff; border: 1px solid var(--color-border-light); border-radius: 4px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mt-8 { margin-top: 8px; }
.mt-10 { margin-top: 10px; }
.justify-end { justify-content: flex-end; }
</style>
