<template>
  <div class="profile-page" :class="{ 'student-wrap': isStudent }">
    <!-- Top Identity Banner Card -->
    <section class="acrylic-card profile-hero">
      <div class="hero-left">
        <div class="big-avatar" :class="user?.portal">
          {{ user?.name?.slice(0, 1) || 'U' }}
        </div>
        <div class="min-w-0">
          <div class="flex items-center gap-8 flex-wrap">
            <h1 class="text-title">{{ user?.name }}</h1>
            <span class="f-pill f-pill-blue">{{ roleText }}</span>
            <span v-if="user?.isInternational" class="f-pill f-pill-success">
              {{ t('profile.intlValid', { visa: user.visaType?.slice(0, 2) || 'X1' }) }}
            </span>
          </div>
          <div class="f-meta-line mt-6">
            <span>{{ store.schoolName }}</span>
            <span class="f-meta-sep">·</span>
            <span>{{ store.getDeptName(user?.deptId) }}</span>
            <template v-if="user?.studentNo">
              <span class="f-meta-sep">·</span>
              <span class="tabular-nums">{{ t('profile.studentNo', { no: user.studentNo }) }}</span>
            </template>
            <template v-else-if="user?.title">
              <span class="f-meta-sep">·</span>
              <span>{{ user.title }}</span>
            </template>
          </div>
        </div>
      </div>

      <button type="button" class="f-btn" @click="toggleEdit">
        <FluentIcon name="pen" :size="14" />
        <span>{{ editing ? t('profile.cancelEdit') : t('profile.edit') }}</span>
      </button>
    </section>

    <!-- Role-Adaptive Summary Metrics -->
    <section class="stats-grid">
      <template v-if="isStudent">
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('jobs.weeklyHoursTitle') }}</div>
          <div class="stat-val tabular-nums mt-6">
            {{ allocatedHours }}<span class="stat-sub">/{{ maxHours }}h</span>
          </div>
          <div class="f-progress-track mt-8">
            <div
              class="f-progress-fill"
              :class="hoursRatio >= 100 ? 'error' : hoursRatio >= 80 ? 'caution' : ''"
              :style="{ width: Math.min(100, hoursRatio) + '%' }"
            />
          </div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.contracts') }}</div>
          <div class="stat-val tabular-nums mt-6">{{ myContracts.length }}<span class="stat-sub"> {{ t('profile.unitContracts') }}</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.totalPaid') }}</div>
          <div class="stat-val text-blue tabular-nums mt-6">¥{{ totalPaid.toFixed(0) }}</div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.latestEval') }}</div>
          <div class="stat-val mt-6">
            <span v-if="latestEval">{{ latestEval.grade }}</span>
            <span v-else class="text-muted text-lg">{{ t('common.none') }}</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.scope') }}</div>
          <div class="stat-val-sm mt-8">{{ scopeLabel }}</div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.visibleJobs') }}</div>
          <div class="stat-val tabular-nums mt-6">{{ visibleJobsCount }}<span class="stat-sub"> {{ t('profile.unitJobs') }}</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.myAudit') }}</div>
          <div class="stat-val tabular-nums mt-6">{{ myAuditLogs.length }}<span class="stat-sub"> {{ t('profile.unitRecords') }}</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">{{ t('profile.ssoStatus') }}</div>
          <div class="stat-val-sm text-success mt-8">{{ t('profile.ssoVerified') }}</div>
        </div>
      </template>
    </section>

    <!-- Main Two-Column Content -->
    <section class="profile-grid">
      <!-- Left Column: Credentials / Academic / RBAC Matrix -->
      <div class="flex-col gap-16">
        <!-- Edit Drawer Inline Card when editing -->
        <form v-if="editing" class="acrylic-card p-20 flex-col gap-14" @submit.prevent="saveProfile">
          <h2 class="text-sub">{{ t('profile.editTitle') }}</h2>
          <div class="grid-2">
            <div class="f-field">
              <label class="f-label">{{ t('profile.email') }}</label>
              <input v-model="editForm.email" type="email" class="f-input" />
            </div>
            <div class="f-field">
              <label class="f-label">{{ t('profile.phone') }}</label>
              <input v-model="editForm.phone" type="text" class="f-input" />
            </div>
          </div>
          <div v-if="isStudent" class="f-field">
            <label class="f-label">{{ t('profile.skillsLabel') }}</label>
            <input v-model="editForm.skillsText" type="text" class="f-input" :placeholder="t('profile.skillsPlaceholder')" />
          </div>
          <div class="f-field">
            <label class="f-label">{{ t('profile.bio') }}</label>
            <textarea v-model="editForm.bio" class="f-textarea" rows="2" :placeholder="t('profile.bioPlaceholder')" />
          </div>
          <div class="flex justify-end gap-8">
            <button type="button" class="f-btn f-btn-sm" @click="editing = false">{{ t('common.cancel') }}</button>
            <button type="submit" class="f-btn f-btn-primary f-btn-sm" :disabled="saving">{{ t('profile.saveChanges') }}</button>
          </div>
        </form>

        <!-- Student Academic & Compliance Profile -->
        <div v-if="isStudent" class="acrylic-card p-20 flex-col gap-14">
          <h2 class="text-sub">{{ t('profile.academicTitle') }}</h2>
          <div class="kv-list">
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.degreeMajor') }}</span>
              <span class="kv-v">{{ user.degree }} · {{ user.major }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.enrollment') }}</span>
              <span class="kv-v fw-600" :class="enrollmentStatusOf(user) === ENROLLMENT_ACTIVE ? 'text-success' : 'text-error'">
                {{ enrollmentText(enrollmentStatusOf(user)) }}{{ t('profile.enrollmentNote') }}
              </span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.gpa') }}</span>
              <span class="kv-v tabular-nums">{{ user.gpa || '-' }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.langCert') }}</span>
              <span v-if="user.languageCertStatus === 'PENDING'" class="kv-v text-caution fw-600">
                {{ t('profile.langPending') }}
              </span>
              <span v-else-if="user.languageCertStatus === 'REJECTED'" class="kv-v text-error fw-600">
                {{ user.languageCert || '-' }}{{ t('profile.langRejected') }}
              </span>
              <span v-else-if="user.languageCertStatus === 'VERIFIED'" class="kv-v text-success fw-600">
                {{ user.languageCert }}{{ t('profile.langVerified') }}
              </span>
              <span v-else class="kv-v text-success fw-600">{{ user.languageCert }}{{ t('profile.langSynced') }}</span>
            </div>
            <div v-if="user.isInternational" class="kv-item">
              <span class="kv-k">{{ t('profile.visa') }}</span>
              <span class="kv-v text-blue fw-600">{{ user.visaType }}{{ t('profile.validUntil', { date: user.visaExpiry }) }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.email') }}</span>
              <span class="kv-v">{{ user.email || '-' }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.phone') }}</span>
              <span class="kv-v tabular-nums">{{ user.phone || '-' }}</span>
            </div>
          </div>

          <div class="pt-12 border-t">
            <div class="text-xs text-muted mb-8">{{ t('profile.mySkills') }}</div>
            <div class="flex items-center gap-6 flex-wrap">
              <span v-for="sk in user.skills || []" :key="sk" class="skill-chip">{{ sk }}</span>
              <span v-if="!(user.skills && user.skills.length)" class="text-xs text-subtle">{{ t('profile.noSkills') }}</span>
            </div>
          </div>
        </div>

        <!-- P1 第三批：语言证明与签证材料上传（http 模式） -->
        <div v-if="isStudent && isHttpMode" class="acrylic-card p-20 flex-col gap-12">
          <div class="flex items-center justify-between flex-wrap gap-8">
            <h2 class="text-sub">{{ t('profile.materials') }}</h2>
            <div class="flex gap-8">
              <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
                <FluentIcon name="plus" :size="14" />
                <span>{{ t('profile.uploadLang') }}</span>
                <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'LANG_CERT')" />
              </label>
              <label v-if="user.isInternational" class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
                <FluentIcon name="plus" :size="14" />
                <span>{{ t('profile.uploadVisa') }}</span>
                <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'VISA')" />
              </label>
            </div>
          </div>
          <div class="text-xs text-muted">
            {{ t('profile.materialsHint') }}
          </div>
          <ul v-if="myMaterials.length" class="att-list">
            <li v-for="a in myMaterials" :key="a.id" class="att-row">
              <span class="f-pill f-pill-neutral">{{ kindLabel(a.kind) }}</span>
              <span class="text-sm truncate att-name">{{ a.originalName }}</span>
              <span v-if="a.verifyStatus" class="f-pill" :class="`f-pill-${LANG_CERT_STATUS_MAP[a.verifyStatus]?.tone}`">
                {{ localized(LANG_CERT_STATUS_MAP[a.verifyStatus], 'label') }}
              </span>
              <span class="text-xs text-muted tabular-nums">{{ a.uploadedAt }}</span>
              <button type="button" class="f-btn-link text-xs" @click="downloadAtt(a)">{{ t('common.download') }}</button>
            </li>
          </ul>
          <div v-else class="text-xs text-subtle">{{ t('profile.noMaterials') }}</div>
        </div>

        <!-- Admin Staff Profile & RBAC Permissions -->
        <div v-else class="acrylic-card p-20 flex-col gap-14">
          <h2 class="text-sub">{{ t('profile.staffTitle') }}</h2>
          <div class="kv-list">
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.school') }}</span>
              <span class="kv-v">{{ store.schoolName }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.dept') }}</span>
              <span class="kv-v">{{ store.getDeptName(user?.deptId) }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.username') }}</span>
              <span class="kv-v tabular-nums">{{ user?.username }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">{{ t('profile.email') }}</span>
              <span class="kv-v">{{ user?.email || '-' }}</span>
            </div>
          </div>

          <div class="pt-12 border-t">
            <div class="text-xs text-muted mb-8">{{ t('profile.permissions') }}</div>
            <div class="flex items-center gap-6 flex-wrap">
              <span v-for="perm in rolePermissions" :key="perm" class="f-pill f-pill-blue">{{ perm }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Student Active Roles OR Admin Personal Audit Trail -->
      <div class="acrylic-card p-20 flex-col gap-14">
        <template v-if="isStudent">
          <div class="flex items-center justify-between">
            <h2 class="text-sub">{{ t('profile.history') }}</h2>
            <router-link to="/student/contracts" class="f-btn-link text-xs">{{ t('profile.manageContracts') }}</router-link>
          </div>

          <div v-if="myContracts.length" class="clean-list">
            <div v-for="c in myContracts" :key="c.id" class="clean-row">
              <div class="flex items-center justify-between gap-8">
                <div class="flex items-center gap-8 min-w-0">
                  <span class="f-type-tag" :class="store.getJob(c.jobId)?.type?.toLowerCase()">
                    {{ store.getJob(c.jobId)?.type }}
                  </span>
                  <span class="fw-600 text-sm truncate">{{ store.getJob(c.jobId)?.title }}</span>
                </div>
                <span class="f-pill" :class="c.status === 'ACTIVE' ? 'f-pill-success' : 'f-pill-caution'">
                  {{ c.status === 'ACTIVE' ? t('contractStatus.ACTIVE') : t('contractStatus.PENDING_SIGN') }}
                </span>
              </div>
              <div class="f-meta-line mt-6 tabular-nums">
                <span>{{ c.startDate }} ~ {{ c.endDate }}</span>
                <span class="f-meta-sep">·</span>
                <span>{{ t('unit.hoursPerWeek', { n: c.weeklyHours }) }} · ¥{{ c.hourlyRate }}/h</span>
              </div>
            </div>
          </div>
          <EmptyState v-else icon="briefcase" :description="t('profile.noHistory')" />
        </template>

        <template v-else>
          <div class="flex items-center justify-between">
            <h2 class="text-sub">{{ t('profile.recentOps') }}</h2>
            <router-link to="/admin/audit" class="f-btn-link text-xs">{{ t('profile.allLogs') }}</router-link>
          </div>

          <div v-if="myAuditLogs.length" class="f-timeline">
            <div v-for="log in myAuditLogs.slice(0, 6)" :key="log.id" class="f-timeline-item">
              <span class="f-timeline-dot" />
              <div class="flex items-center justify-between gap-8">
                <span class="fw-600 text-xs">{{ log.action }}</span>
                <span class="text-xs text-subtle tabular-nums">{{ log.timestamp.slice(5, 16) }}</span>
              </div>
              <div class="text-xs text-muted mt-4">{{ log.opinion || log.targetId }}</div>
            </div>
          </div>
          <EmptyState v-else icon="history" :description="t('profile.noOps')" />
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { getStudentHoursSummary, filterJobsByRole, LANG_CERT_STATUS_MAP, enrollmentStatusOf, ENROLLMENT_ACTIVE, ENROLLMENT_STATUSES } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi, ACCEPT_TYPES, ATTACHMENT_KIND_MAP } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const { t, localized } = useI18n()
const user = computed(() => store.currentUser)
const isStudent = computed(() => user.value?.portal === 'student')

// 角色名按角色代码翻译（不显示后端的中文 roleLabel）
const roleText = computed(() => (user.value?.role ? t(`role.${user.value.role}`) : ''))

// 学籍状态：规则引擎的取值为中文（在读 / 休学 / 毕业），按其在 ENROLLMENT_STATUSES 中的位置翻译
const ENROLLMENT_CODES = ['ACTIVE', 'SUSPENDED', 'GRADUATED']
function enrollmentText(status) {
  const idx = ENROLLMENT_STATUSES.indexOf(status)
  return idx >= 0 ? t(`enrollment.${ENROLLMENT_CODES[idx]}`) : status
}

// 附件类型按代码翻译
function kindLabel(kind) {
  const key = `attachmentKind.${kind}`
  const text = t(key)
  return text === key ? ATTACHMENT_KIND_MAP[kind] || kind : text
}

const editing = ref(false)
const saving = ref(false)
const editForm = reactive({ email: '', phone: '', skillsText: '', bio: '' })

// 优先使用快照里的 hoursSummary（后端跨院累计），没有时按可见合同计算
const hoursSummary = computed(() => getStudentHoursSummary(store.db, user.value))
const allocatedHours = computed(() => hoursSummary.value.allocated)
const maxHours = computed(() => hoursSummary.value.max)
const hoursRatio = computed(() => Math.round((allocatedHours.value / maxHours.value) * 100))

const myContracts = computed(() =>
  store.db.contracts.filter((c) => c.studentId === user.value?.id)
)
const totalPaid = computed(() =>
  store.db.payrolls
    .filter((p) => p.studentId === user.value?.id && p.financeStatus === 'PAID')
    .reduce((s, p) => s + Number(p.amount), 0)
)
const latestEval = computed(() =>
  store.db.evaluations.find((e) => e.studentId === user.value?.id) || null
)

const visibleJobsCount = computed(() => filterJobsByRole(store.db, user.value).length)
const myAuditLogs = computed(() =>
  store.db.auditLogs.filter((l) => l.operatorName === user.value?.name)
)

const scopeLabel = computed(() => {
  const r = user.value?.role
  if (r === 'SCHOOL_ADMIN') return t('profile.scopeSchool')
  if (r === 'DEPT_ADMIN') return t('profile.scopeDept', { dept: store.getDeptShortName(user.value.deptId) })
  if (r === 'INTL_OFFICER') return t('profile.scopeIntl')
  return t('profile.scopeMentor', { dept: store.getDeptShortName(user.value?.deptId) })
})

const rolePermissions = computed(() => {
  const list = t(`profile.perms.${user.value?.role}`)
  // 词条为 '|' 分隔的能力列表；没有对应角色时为空
  return typeof list === 'string' && !list.startsWith('profile.perms.') ? list.split('|') : []
})

function toggleEdit() {
  if (!editing.value && user.value) {
    editForm.email = user.value.email || ''
    editForm.phone = user.value.phone || ''
    editForm.skillsText = (user.value.skills || []).join(', ')
    editForm.bio = user.value.bio || ''
  }
  editing.value = !editing.value
}

async function saveProfile() {
  saving.value = true
  try {
    const skills = isStudent.value
      ? editForm.skillsText.split(/[,，]/).map((s) => s.trim()).filter(Boolean)
      : undefined
    const res = await store.mutate(() =>
      api.updateUserProfile(
        {
          userId: user.value.id,
          email: editForm.email,
          phone: editForm.phone,
          skills,
          bio: editForm.bio
        },
        user.value
      )
    )
    if (res.code === 200) editing.value = false
  } finally {
    saving.value = false
  }
}

// ===== 合规材料（P1 第三批）=====
const myMaterials = ref([])
const uploading = ref(false)

async function loadMaterials() {
  if (!isHttpMode || !isStudent.value || !user.value) return
  const res = await materialFinanceApi.listAttachments({ ownerId: user.value.id })
  if (res.code === 200) {
    myMaterials.value = (res.data || []).filter((a) => a.kind === 'LANG_CERT' || a.kind === 'VISA')
  }
}

async function onPick(event, kind) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    // 上传语言证明会改变学籍合规状态，成功后刷新快照
    const res = await store.mutate(() => materialFinanceApi.uploadAttachment(file, kind))
    if (res.code === 200) await loadMaterials()
  } finally {
    uploading.value = false
  }
}

async function downloadAtt(a) {
  const res = await materialFinanceApi.downloadAttachment(a)
  if (res.code !== 200) store.notify(localized(res, 'message') || t('profile.downloadFailed'), 'error')
}

onMounted(loadMaterials)
</script>

<style scoped>
.upload-btn { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; }
.upload-btn.disabled { opacity: 0.5; pointer-events: none; }
.att-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.att-row { display: flex; align-items: center; gap: 8px; padding: 6px 10px; border: 1px solid var(--color-border-light); border-radius: 4px; background: #ffffff; min-width: 0; }
.att-name { flex: 1; min-width: 0; }
.text-caution { color: var(--color-caution); }

.profile-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.profile-page.student-wrap {
  max-width: 1180px;
  margin: 24px auto 0;
  padding: 0 24px;
}

.profile-hero {
  padding: 24px 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.hero-left {
  display: flex;
  align-items: center;
  gap: 18px;
  min-width: 0;
}

.big-avatar {
  width: 60px;
  height: 60px;
  border-radius: 16px;
  font-size: 24px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.big-avatar.student {
  background: var(--color-blue-subtle);
  color: var(--color-fluent-blue);
}

.big-avatar.admin {
  background: var(--color-fluent-blue);
  color: #ffffff;
  box-shadow: 0 6px 16px rgba(0, 120, 212, 0.25);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}

.stat-card { padding: 18px 20px; }
.stat-val { font-size: 26px; font-weight: 600; line-height: 1.15; }
.stat-val-sm { font-size: 16px; font-weight: 600; line-height: 1.3; }
.stat-sub { font-size: 13px; font-weight: 400; color: var(--color-muted); }

.profile-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  align-items: start;
}

.kv-list {
  display: flex;
  flex-direction: column;
}

.kv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-light);
  font-size: 13px;
}

.kv-item:last-child { border-bottom: none; }
.kv-k { color: var(--color-muted); flex-shrink: 0; }
.kv-v { text-align: right; font-weight: 500; }

.skill-chip {
  font-size: 12px;
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 600;
}

.clean-list { display: flex; flex-direction: column; }
.clean-row {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border-light);
}
.clean-row:last-child { border-bottom: none; }

.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.border-t { border-top: 1px solid var(--color-border-light); }
.pt-12 { padding-top: 12px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mb-8 { margin-bottom: 8px; }
.min-w-0 { min-width: 0; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.justify-end { justify-content: flex-end; }

@media (max-width: 960px) {
  .profile-page.student-wrap { padding: 0 16px; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .profile-grid { grid-template-columns: 1fr; }
}

@media (max-width: 560px) {
  .stats-grid, .grid-2 { grid-template-columns: 1fr; }
}

/* 手机（≤768px）：网格列允许收缩，按钮换行，避免页面被撑宽 */
@media (max-width: 768px) {
  .profile-page.student-wrap { margin-top: 16px; padding: 0 16px; }
  .profile-page { gap: 14px; }
  .profile-hero { padding: 18px 16px; }
  .hero-left { gap: 12px; }
  .big-avatar { width: 52px; height: 52px; font-size: 21px; }
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
  .stat-card { padding: 14px; min-width: 0; }
  .stat-val { font-size: 22px; }
  .profile-grid { grid-template-columns: minmax(0, 1fr); }
  .profile-grid > *, .stats-grid > * { min-width: 0; }
  .profile-grid .p-20 { padding: 16px; }
  .profile-grid .flex { flex-wrap: wrap; }
  .profile-grid .upload-btn { flex: 1 1 140px; }
  .kv-item { font-size: 14px; flex-wrap: wrap; }
  .kv-v { min-width: 0; overflow-wrap: anywhere; }
  .skill-chip { font-size: 13px; }
}
</style>
