<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">工时审核与预警</h1>
      <div class="f-segmented">
        <button type="button" :class="{ active: statusTab === 'ALL' }" @click="statusTab = 'ALL'">全部</button>
        <button type="button" :class="{ active: statusTab === 'PENDING' }" @click="statusTab = 'PENDING'">待审（{{ pendingCount }}）</button>
        <button type="button" :class="{ active: statusTab === 'APPROVED' }" @click="statusTab = 'APPROVED'">已通过</button>
      </div>
    </header>

    <!-- Collapsible Anomaly Bar -->
    <section v-if="anomalies.length" class="acrylic-card p-16">
      <div class="flex items-center justify-between cursor-pointer" @click="showAnomalies = !showAnomalies">
        <div class="flex items-center gap-8">
          <span class="f-dot-error" />
          <span class="fw-600 text-sm">当前存在 {{ anomalies.length }} 条工时 / 聘期预警</span>
        </div>
        <button type="button" class="f-expand-trigger">
          <span>{{ showAnomalies ? '收起' : '展开查看' }}</span>
          <FluentIcon :name="showAnomalies ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <div v-if="showAnomalies" class="anomaly-grid mt-12">
        <div v-for="w in anomalies" :key="w.id" class="anomaly-pill" :class="w.severity">
          <div class="fw-600 text-xs">{{ w.title }}</div>
          <div class="text-xs text-muted mt-4">{{ w.detail }}</div>
        </div>
      </div>
    </section>

    <section class="acrylic-card table-card">
      <div v-if="filteredTimesheets.length" class="f-table-wrap">
        <table class="f-table f-table-sticky-action">
          <thead>
            <tr>
              <th>学生</th>
              <th>在岗岗位</th>
              <th>周次 / 工时</th>
              <th>当周跨岗负荷</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="ts in filteredTimesheets" :key="ts.id">
              <td>
                <div class="flex items-center gap-6">
                  <span class="fw-600">{{ store.getUser(ts.studentId)?.name }}</span>
                  <span v-if="store.getUser(ts.studentId)?.isInternational" class="f-pill f-pill-blue">国际生</span>
                </div>
              </td>
              <td>{{ store.getJob(ts.jobId)?.title }}</td>
              <td class="tabular-nums fw-600">第 {{ ts.weekNo }} 周 · {{ ts.hours }}h</td>
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
                  {{ ts.status === 'APPROVED' ? '已通过' : ts.status === 'REJECTED' ? '已驳回' : '待审核' }}
                </span>
              </td>
              <td>
                <button
                  type="button"
                  class="f-btn f-btn-sm"
                  :class="ts.status === 'PENDING' && canAudit ? 'f-btn-primary' : ''"
                  @click="openAuditDrawer(ts)"
                >
                  {{ ts.status === 'PENDING' && canAudit ? '审核' : '详情' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="clock" description="暂无工时记录" />
    </section>

    <AuditDrawer
      v-if="selectedTs"
      :title="`第 ${selectedTs.weekNo} 周工时 · ${selectedTs.hours}h`"
      :subtitle="`${selectedTs.id} · ${store.getUser(selectedTs.studentId)?.name}`"
      :audit-logs="selectedTsLogs"
      @close="selectedTsId = null"
    >
      <div class="drawer-card">
        <div class="fw-600 text-sm">{{ store.getJob(selectedTs.jobId)?.title }}</div>
        <div class="text-xs text-muted mt-6 tabular-nums">
          当周跨岗累计：{{ weekTotal(selectedTs.studentId, selectedTs.weekNo) }}/{{ maxCap(selectedTs.studentId) }}h
        </div>
        <div class="quote-box mt-10 text-xs">{{ selectedTs.workLog }}</div>
        <div v-if="selectedTs.milestoneRef" class="text-xs text-blue mt-8">关联：{{ selectedTs.milestoneRef }}</div>
        <div v-if="selectedTs.mentorComment" class="text-xs text-muted mt-6">评语：{{ selectedTs.mentorComment }}</div>
      </div>

      <template v-if="selectedTs.status === 'PENDING' && canAudit" #footer>
        <input v-model="auditComment" type="text" class="f-input" placeholder="审核意见（选填）..." />
        <div class="flex gap-8 justify-end">
          <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="handleReview('REJECT')">驳回</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="handleReview('APPROVE')">通过</button>
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

const store = useAppStore()
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
