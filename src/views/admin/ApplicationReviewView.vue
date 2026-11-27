<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ user?.role === 'INTL_OFFICER' ? $t('admin.apps.titleIntl') : $t('admin.apps.title') }}</h1>
      <div class="f-segmented">
        <button type="button" :class="{ active: filterTab === 'ALL' }" @click="filterTab = 'ALL'">{{ $t('admin.common.all') }}</button>
        <button type="button" :class="{ active: filterTab === 'SPECIAL' }" @click="filterTab = 'SPECIAL'">{{ $t('admin.apps.tabSpecial', { n: specialCount }) }}</button>
        <button type="button" :class="{ active: filterTab === 'PENDING' }" @click="filterTab = 'PENDING'">{{ $t('admin.apps.tabPending') }}</button>
      </div>
    </header>

    <section class="acrylic-card table-card">
      <div v-if="filteredApps.length" class="f-table-wrap">
        <table class="f-table f-table-sticky-action">
          <thead>
            <tr>
              <th>{{ $t('admin.apps.colApplicant') }}</th>
              <th>{{ $t('admin.apps.colJob') }}</th>
              <th>{{ $t('admin.apps.colCompliance') }}</th>
              <th>{{ $t('admin.apps.colScore') }}</th>
              <th>{{ $t('admin.common.status') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="app in filteredApps" :key="app.id">
              <td>
                <div class="flex items-center gap-6">
                  <span class="fw-600">{{ store.getUser(app.studentId)?.name }}</span>
                  <span v-if="store.getUser(app.studentId)?.isInternational" class="f-pill f-pill-blue">{{ $t('admin.common.intl') }}</span>
                </div>
              </td>
              <td>{{ store.getJob(app.jobId)?.title }}</td>
              <td>
                <span v-if="app.isSpecialApproval" class="f-pill f-pill-caution">{{ $t('admin.apps.specialTrace') }}</span>
                <span
                  v-else
                  class="f-pill"
                  :class="app.complianceCheckResult?.passed ? 'f-pill-success' : 'f-pill-error'"
                >
                  {{ app.complianceCheckResult?.passed ? $t('admin.apps.passed') : $t('admin.apps.blocked') }}
                </span>
              </td>
              <td class="tabular-nums text-muted">{{ app.score !== null && app.score !== undefined ? $t('admin.apps.scoreVal', { n: app.score }) : '-' }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${APP_STATUS_MAP[app.status]?.tone || 'neutral'}`">
                  {{ statusLabel(APP_STATUS_MAP, app.status) }}
                </span>
              </td>
              <td>
                <button
                  type="button"
                  class="f-btn f-btn-sm"
                  :class="needsAction(app) ? 'f-btn-primary' : ''"
                  @click="openDrawer(app)"
                >
                  {{ needsAction(app) ? $t('admin.apps.process') : $t('admin.common.details') }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="users" :description="$t('admin.apps.empty')" />
    </section>

    <AuditDrawer
      v-if="selectedApp"
      :title="$t('admin.apps.drawerTitle', { name: selectedStudent?.name })"
      :subtitle="`${selectedApp.id} · ${selectedJob?.title}`"
      :audit-logs="selectedAppLogs"
      @close="selectedAppId = null"
    >
      <div class="drawer-card">
        <div class="kv-grid">
          <div><span class="text-muted">{{ $t('admin.apps.studentNo') }}</span><strong>{{ selectedStudent?.studentNo }}</strong></div>
          <div><span class="text-muted">{{ $t('admin.apps.major') }}</span><strong>{{ selectedStudent?.major }}</strong></div>
          <div><span class="text-muted">{{ $t('admin.apps.language') }}</span><strong>{{ selectedStudent?.languageCert || '-' }}</strong></div>
          <div><span class="text-muted">{{ $t('admin.apps.visa') }}</span><strong>{{ selectedStudent?.isInternational ? selectedStudent.visaType : $t('admin.apps.chinese') }}</strong></div>
        </div>
        <div class="quote-box mt-10 text-xs">{{ selectedApp.statement }}</div>
        <div v-if="!isHttpMode" class="mt-8 text-xs text-blue">{{ $t('admin.apps.attachment', { name: selectedApp.resumeFileName }) }}</div>
        <!-- P1 第三批：申请附件 + 学生的语言证明 / 签证材料，可下载 -->
        <div v-else class="mt-10">
          <div class="fw-600 text-xs mb-6">{{ $t('admin.apps.materials') }}</div>
          <div v-if="attLoading" class="text-xs text-muted">{{ $t('admin.apps.loadingAtt') }}</div>
          <div v-else-if="!drawerAttachments.length" class="text-xs text-muted">{{ $t('admin.apps.noAtt', { name: selectedApp.resumeFileName }) }}</div>
          <ul v-else class="att-list">
            <li v-for="a in drawerAttachments" :key="a.id" class="att-row">
              <span class="f-pill f-pill-neutral">{{ ATTACHMENT_KIND_MAP[a.kind] }}</span>
              <span class="text-xs truncate att-name">{{ a.originalName }}</span>
              <span v-if="a.verifyStatus" class="f-pill" :class="`f-pill-${LANG_CERT_STATUS_MAP[a.verifyStatus]?.tone}`">
                {{ statusLabel(LANG_CERT_STATUS_MAP, a.verifyStatus) }}
              </span>
              <span class="text-xs text-muted">{{ formatFileSize(a.sizeBytes) }}</span>
              <button type="button" class="f-btn-link text-xs" @click="downloadAtt(a)">{{ $t('admin.common.download') }}</button>
            </li>
          </ul>
        </div>
      </div>

      <div class="drawer-card">
        <div class="fw-600 text-xs mb-8">{{ $t('admin.apps.checks') }}</div>
        <div class="flex-col gap-6">
          <div v-for="c in selectedApp.complianceCheckResult?.checks || []" :key="c.key" class="check-mini" :class="c.passed ? 'ok' : 'fail'">
            <FluentIcon :name="c.passed ? 'check-circle' : 'x-circle'" :size="14" />
            <div class="text-xs"><strong>{{ localized(c, 'label') }}</strong>{{ $t('admin.common.colon') }}{{ localized(c, 'detail') }}</div>
          </div>
        </div>
        <div
          v-for="h in (selectedApp.complianceCheckResult?.hints || []).filter((x) => !x.passed)"
          :key="h.key"
          class="f-banner caution mt-10 text-xs"
        >
          <span><strong>{{ localized(h, 'label') }}{{ $t('admin.common.colon') }}</strong>{{ localized(h, 'detail') }}</span>
        </div>
        <div v-if="selectedApp.isSpecialApproval" class="f-banner caution mt-10 text-xs">
          <span><strong>{{ $t('admin.apps.specialReason') }}</strong>{{ selectedApp.specialReason }}</span>
        </div>
      </div>

      <template #footer>
        <div v-if="canReviewSpecial" class="flex-col gap-10">
          <input v-model="specialOpinion" type="text" class="f-input" :placeholder="$t('admin.apps.specialOpinionPh')" />
          <div class="flex gap-8 justify-end">
            <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleSpecialDecision('REJECT')">{{ $t('admin.common.reject') }}</button>
            <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleSpecialDecision('APPROVE')">{{ $t('admin.apps.approveSpecial') }}</button>
          </div>
        </div>

        <div v-else-if="canMentorProcess" class="flex-col gap-10">
          <div class="f-segmented">
            <button type="button" :class="{ active: actionMode === 'INTERVIEW' }" @click="actionMode = 'INTERVIEW'">{{ $t('admin.apps.scheduleInterview') }}</button>
            <button type="button" :class="{ active: actionMode === 'DECIDE' }" @click="actionMode = 'DECIDE'">{{ $t('admin.apps.decide') }}</button>
          </div>

          <div v-if="actionMode === 'INTERVIEW'" class="flex-col gap-8">
            <div class="grid-2">
              <input v-model="interviewForm.time" type="text" class="f-input" :placeholder="$t('admin.apps.interviewTime')" />
              <input v-model="interviewForm.location" type="text" class="f-input" :placeholder="$t('admin.apps.interviewLocation')" />
            </div>
            <div class="flex justify-end">
              <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleScheduleInterview">{{ $t('admin.apps.sendInterview') }}</button>
            </div>
          </div>

          <div v-else class="flex-col gap-8">
            <div class="grid-2">
              <input v-model.number="decideForm.score" type="number" min="0" max="100" class="f-input tabular-nums" :placeholder="$t('admin.apps.scorePh')" />
              <input v-model="decideForm.comment" type="text" class="f-input" :placeholder="$t('admin.apps.commentPh')" />
            </div>
            <div class="flex gap-8 justify-end">
              <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleDecide('REJECT')">{{ $t('admin.apps.notHire') }}</button>
              <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleDecide('HIRE')">{{ $t('admin.apps.hire') }}</button>
            </div>
          </div>
        </div>
      </template>
    </AuditDrawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, APP_STATUS_MAP, LANG_CERT_STATUS_MAP, contractStartFrom } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi, ATTACHMENT_KIND_MAP, formatFileSize } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import AuditDrawer from '@/components/AuditDrawer.vue'
import { useI18n } from '@/i18n'
import { statusLabel } from './adminI18n'

const store = useAppStore()
const { t, localized } = useI18n()
const user = computed(() => store.currentUser)

const filterTab = ref('ALL')
const selectedAppId = ref(null)
const actionMode = ref('INTERVIEW')
const specialOpinion = ref('')
const busy = ref(false)

// 表单默认值（演示用，提交后成为用户数据）：按打开抽屉时的语言填入
const interviewForm = reactive({ time: `${contractStartFrom()} 14:30`, location: '', note: '' })
const decideForm = reactive({ score: 90, comment: '' })
function resetFormDefaults() {
  interviewForm.time = `${contractStartFrom()} 14:30`
  interviewForm.location = t('admin.apps.defaults.location')
  interviewForm.note = t('admin.apps.defaults.note')
  decideForm.score = 90
  decideForm.comment = t('admin.apps.defaults.comment')
}

const visibleApps = computed(() => filterByRoleDept(store.db, store.db.applications, user.value, 'APPLICATION'))
const specialCount = computed(() => visibleApps.value.filter((a) => a.isSpecialApproval).length)

const filteredApps = computed(() => {
  return visibleApps.value.filter((a) => {
    if (filterTab.value === 'SPECIAL') return a.isSpecialApproval
    if (filterTab.value === 'PENDING') return ['SUBMITTED', 'PENDING_SPECIAL_APPROVAL', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(a.status)
    return true
  })
})

const selectedApp = computed(() => store.db.applications.find((a) => a.id === selectedAppId.value) || null)
const selectedStudent = computed(() => (selectedApp.value ? store.getUser(selectedApp.value.studentId) : null))
const selectedJob = computed(() => (selectedApp.value ? store.getJob(selectedApp.value.jobId) : null))
const selectedAppLogs = computed(() =>
  selectedAppId.value ? store.db.auditLogs.filter((l) => l.targetType === 'APPLICATION' && l.targetId === selectedAppId.value) : []
)

const canReviewSpecial = computed(() =>
  selectedApp.value?.status === 'PENDING_SPECIAL_APPROVAL' && ['INTL_OFFICER', 'SCHOOL_ADMIN'].includes(user.value?.role)
)
const canMentorProcess = computed(() =>
  ['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(selectedApp.value?.status) && ['MENTOR', 'SCHOOL_ADMIN'].includes(user.value?.role)
)

function needsAction(app) {
  if (app.status === 'PENDING_SPECIAL_APPROVAL' && ['INTL_OFFICER', 'SCHOOL_ADMIN'].includes(user.value?.role)) return true
  if (['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(app.status) && ['MENTOR', 'SCHOOL_ADMIN'].includes(user.value?.role)) return true
  return false
}

function openDrawer(app) {
  selectedAppId.value = app.id
  specialOpinion.value = ''
  resetFormDefaults()
  actionMode.value = app.status === 'INTERVIEW_SCHEDULED' ? 'DECIDE' : 'INTERVIEW'
  loadAttachments(app)
}

// 筛选抽屉的附件：本申请关联的材料 + 申请人的语言证明、签证材料
const drawerAttachments = ref([])
const attLoading = ref(false)
async function loadAttachments(app) {
  drawerAttachments.value = []
  if (!isHttpMode) return
  attLoading.value = true
  try {
    const res = await materialFinanceApi.listAttachments({ ownerId: app.studentId })
    if (selectedAppId.value !== app.id) return
    const ids = new Set(app.attachmentIds || [])
    drawerAttachments.value = (res.code === 200 ? res.data || [] : []).filter(
      (a) => ids.has(a.id) || a.kind === 'LANG_CERT' || a.kind === 'VISA'
    )
  } finally {
    attLoading.value = false
  }
}

async function downloadAtt(a) {
  const res = await materialFinanceApi.downloadAttachment(a)
  if (res.code !== 200) store.notify(localized(res, 'message') || t('admin.common.downloadFailed'), 'error')
}

async function handleSpecialDecision(decision) {
  busy.value = true
  try {
    await store.mutate(() => api.reviewSpecialApproval({ applicationId: selectedApp.value.id, decision, opinion: specialOpinion.value.trim() }, user.value))
  } finally { busy.value = false }
}

async function handleScheduleInterview() {
  busy.value = true
  try {
    await store.mutate(() => api.scheduleInterview({ applicationId: selectedApp.value.id, ...interviewForm }, user.value))
    actionMode.value = 'DECIDE'
  } finally { busy.value = false }
}

async function handleDecide(decision) {
  busy.value = true
  try {
    await store.mutate(() => api.decideApplication({ applicationId: selectedApp.value.id, score: decideForm.score, decision, comment: decideForm.comment }, user.value))
  } finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.drawer-card { padding: 14px 16px; background: rgba(255, 255, 255, 0.72); border: 1px solid var(--color-border-light); border-radius: 6px; }
.kv-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; font-size: var(--text-label); }
.quote-box { padding: 8px 10px; background: #ffffff; border: 1px solid var(--color-border-light); border-radius: 4px; }
.check-mini { display: flex; align-items: flex-start; gap: 8px; padding: 6px 8px; background: #ffffff; border-radius: 4px; border: 1px solid var(--color-border-light); }
.check-mini.ok svg { color: var(--color-success); margin-top: 2px; flex-shrink: 0; }
.check-mini.fail svg { color: var(--color-error); margin-top: 2px; flex-shrink: 0; }
.grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; }
.att-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.att-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: #ffffff; border: 1px solid var(--color-border-light); border-radius: 4px; min-width: 0; }
.att-name { flex: 1; min-width: 0; }
.mb-6 { margin-bottom: 6px; }
.mt-8 { margin-top: 8px; }
.mt-10 { margin-top: 10px; }
.mb-8 { margin-bottom: 8px; }
.justify-end { justify-content: flex-end; }
</style>
