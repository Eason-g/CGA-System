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
            <span class="f-pill f-pill-blue">{{ user?.roleLabel }}</span>
            <span v-if="user?.isInternational" class="f-pill f-pill-success">
              国际生 · {{ user.visaType?.slice(0, 2) || 'X1' }}有效
            </span>
          </div>
          <div class="f-meta-line mt-6">
            <span>{{ store.schoolName }}</span>
            <span class="f-meta-sep">·</span>
            <span>{{ store.getDeptName(user?.deptId) }}</span>
            <template v-if="user?.studentNo">
              <span class="f-meta-sep">·</span>
              <span class="tabular-nums">学号 {{ user.studentNo }}</span>
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
        <span>{{ editing ? '取消编辑' : '编辑资料' }}</span>
      </button>
    </section>

    <!-- Role-Adaptive Summary Metrics -->
    <section class="stats-grid">
      <template v-if="isStudent">
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">跨岗周工时</div>
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
          <div class="text-xs text-muted">在岗 / 待签协议</div>
          <div class="stat-val tabular-nums mt-6">{{ myContracts.length }}<span class="stat-sub"> 份</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">累计已发薪酬</div>
          <div class="stat-val text-blue tabular-nums mt-6">¥{{ totalPaid.toFixed(0) }}</div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">最新考核结果</div>
          <div class="stat-val mt-6">
            <span v-if="latestEval">{{ latestEval.grade }}</span>
            <span v-else class="text-muted text-lg">暂无</span>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">数据隔离视野</div>
          <div class="stat-val-sm mt-8">{{ scopeLabel }}</div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">可见三助岗位</div>
          <div class="stat-val tabular-nums mt-6">{{ visibleJobsCount }}<span class="stat-sub"> 个</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">个人审计操作记录</div>
          <div class="stat-val tabular-nums mt-6">{{ myAuditLogs.length }}<span class="stat-sub"> 条</span></div>
        </div>
        <div class="acrylic-card stat-card">
          <div class="text-xs text-muted">统一认证状态</div>
          <div class="stat-val-sm text-success mt-8">CAS 已认证 ✓</div>
        </div>
      </template>
    </section>

    <!-- Main Two-Column Content -->
    <section class="profile-grid">
      <!-- Left Column: Credentials / Academic / RBAC Matrix -->
      <div class="flex-col gap-16">
        <!-- Edit Drawer Inline Card when editing -->
        <form v-if="editing" class="acrylic-card p-20 flex-col gap-14" @submit.prevent="saveProfile">
          <h2 class="text-sub">编辑个人资料</h2>
          <div class="grid-2">
            <div class="f-field">
              <label class="f-label">校园邮箱</label>
              <input v-model="editForm.email" type="email" class="f-input" />
            </div>
            <div class="f-field">
              <label class="f-label">联系电话</label>
              <input v-model="editForm.phone" type="text" class="f-input" />
            </div>
          </div>
          <div v-if="isStudent" class="f-field">
            <label class="f-label">个人技能标签（逗号分隔，实时影响岗位大厅推荐排序）</label>
            <input v-model="editForm.skillsText" type="text" class="f-input" placeholder="Python, 机器学习, 英语授课" />
          </div>
          <div class="f-field">
            <label class="f-label">个人简介</label>
            <textarea v-model="editForm.bio" class="f-textarea" rows="2" placeholder="简述研究兴趣、教学专长或负责业务..." />
          </div>
          <div class="flex justify-end gap-8">
            <button type="button" class="f-btn f-btn-sm" @click="editing = false">取消</button>
            <button type="submit" class="f-btn f-btn-primary f-btn-sm" :disabled="saving">保存修改</button>
          </div>
        </form>

        <!-- Student Academic & Compliance Profile -->
        <div v-if="isStudent" class="acrylic-card p-20 flex-col gap-14">
          <h2 class="text-sub">学籍档案与合规资质</h2>
          <div class="kv-list">
            <div class="kv-item">
              <span class="kv-k">培养层次 / 专业</span>
              <span class="kv-v">{{ user.degree }} · {{ user.major }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">学籍状态</span>
              <span class="kv-v fw-600" :class="enrollmentStatusOf(user) === '在读' ? 'text-success' : 'text-error'">
                {{ enrollmentStatusOf(user) }}（以学籍系统同步为准，非在读不可申请三助岗位）
              </span>
            </div>
            <div class="kv-item">
              <span class="kv-k">学业 GPA</span>
              <span class="kv-v tabular-nums">{{ user.gpa || '-' }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">语言能力认证</span>
              <span v-if="user.languageCertStatus === 'PENDING'" class="kv-v text-caution fw-600">
                新语言证明待国际生办核验（核验前申请校验视为未通过）
              </span>
              <span v-else-if="user.languageCertStatus === 'REJECTED'" class="kv-v text-error fw-600">
                {{ user.languageCert || '-' }}（最新语言证明核验未通过）
              </span>
              <span v-else-if="user.languageCertStatus === 'VERIFIED'" class="kv-v text-success fw-600">
                {{ user.languageCert }}（国际生办已核验）
              </span>
              <span v-else class="kv-v text-success fw-600">{{ user.languageCert }}（已同步教务）</span>
            </div>
            <div v-if="user.isInternational" class="kv-item">
              <span class="kv-k">签证与居留许可</span>
              <span class="kv-v text-blue fw-600">{{ user.visaType }}（有效至 {{ user.visaExpiry }}）</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">校园邮箱</span>
              <span class="kv-v">{{ user.email || '-' }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">联系电话</span>
              <span class="kv-v tabular-nums">{{ user.phone || '-' }}</span>
            </div>
          </div>

          <div class="pt-12 border-t">
            <div class="text-xs text-muted mb-8">我的技能标签（用于岗位匹配推荐）</div>
            <div class="flex items-center gap-6 flex-wrap">
              <span v-for="sk in user.skills || []" :key="sk" class="skill-chip">{{ sk }}</span>
              <span v-if="!(user.skills && user.skills.length)" class="text-xs text-subtle">暂未设置技能标签</span>
            </div>
          </div>
        </div>

        <!-- P1 第三批：语言证明与签证材料上传（http 模式） -->
        <div v-if="isStudent && isHttpMode" class="acrylic-card p-20 flex-col gap-12">
          <div class="flex items-center justify-between flex-wrap gap-8">
            <h2 class="text-sub">合规材料</h2>
            <div class="flex gap-8">
              <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
                <FluentIcon name="plus" :size="14" />
                <span>上传语言证明</span>
                <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'LANG_CERT')" />
              </label>
              <label v-if="user.isInternational" class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
                <FluentIcon name="plus" :size="14" />
                <span>上传签证/居留许可</span>
                <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'VISA')" />
              </label>
            </div>
          </div>
          <div class="text-xs text-muted">
            支持 PDF / JPG / PNG，单个不超过 10MB。新上传的语言证明需国际生办核验后才生效，核验前岗位申请的语言项视为未通过（可申请特批）。
          </div>
          <ul v-if="myMaterials.length" class="att-list">
            <li v-for="a in myMaterials" :key="a.id" class="att-row">
              <span class="f-pill f-pill-neutral">{{ ATTACHMENT_KIND_MAP[a.kind] }}</span>
              <span class="text-sm truncate att-name">{{ a.originalName }}</span>
              <span v-if="a.verifyStatus" class="f-pill" :class="`f-pill-${LANG_CERT_STATUS_MAP[a.verifyStatus]?.tone}`">
                {{ LANG_CERT_STATUS_MAP[a.verifyStatus]?.label }}
              </span>
              <span class="text-xs text-muted tabular-nums">{{ a.uploadedAt }}</span>
              <button type="button" class="f-btn-link text-xs" @click="downloadAtt(a)">下载</button>
            </li>
          </ul>
          <div v-else class="text-xs text-subtle">暂未上传材料</div>
        </div>

        <!-- Admin Staff Profile & RBAC Permissions -->
        <div v-else class="acrylic-card p-20 flex-col gap-14">
          <h2 class="text-sub">教职工信息与权限边界</h2>
          <div class="kv-list">
            <div class="kv-item">
              <span class="kv-k">所属院校</span>
              <span class="kv-v">{{ store.schoolName }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">所属院系 / 部门</span>
              <span class="kv-v">{{ store.getDeptName(user?.deptId) }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">系统账号</span>
              <span class="kv-v tabular-nums">{{ user?.username }}</span>
            </div>
            <div class="kv-item">
              <span class="kv-k">校园邮箱</span>
              <span class="kv-v">{{ user?.email || '-' }}</span>
            </div>
          </div>

          <div class="pt-12 border-t">
            <div class="text-xs text-muted mb-8">当前角色已授权能力</div>
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
            <h2 class="text-sub">在岗履历与考核</h2>
            <router-link to="/student/contracts" class="f-btn-link text-xs">管理合同 →</router-link>
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
                  {{ c.status === 'ACTIVE' ? '履约中' : '待签署' }}
                </span>
              </div>
              <div class="f-meta-line mt-6 tabular-nums">
                <span>{{ c.startDate }} ~ {{ c.endDate }}</span>
                <span class="f-meta-sep">·</span>
                <span>{{ c.weeklyHours }}h/周 · ¥{{ c.hourlyRate }}/h</span>
              </div>
            </div>
          </div>
          <EmptyState v-else icon="briefcase" description="暂无在岗聘用履历" />
        </template>

        <template v-else>
          <div class="flex items-center justify-between">
            <h2 class="text-sub">我最近的操作留痕</h2>
            <router-link to="/admin/audit" class="f-btn-link text-xs">全部日志 →</router-link>
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
          <EmptyState v-else icon="history" description="暂无个人操作记录" />
        </template>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { getStudentHoursSummary, filterJobsByRole, LANG_CERT_STATUS_MAP, enrollmentStatusOf } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi, ACCEPT_TYPES, ATTACHMENT_KIND_MAP } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)
const isStudent = computed(() => user.value?.portal === 'student')

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
  if (r === 'SCHOOL_ADMIN') return '全校跨院系'
  if (r === 'DEPT_ADMIN') return `${store.getDeptShortName(user.value.deptId)}隔离`
  if (r === 'INTL_OFFICER') return '国际生专项'
  return `${store.getDeptShortName(user.value?.deptId)} · 导师`
})

const rolePermissions = computed(() => {
  const map = {
    MENTOR: ['发布 TA/RA 岗位', '筛选简历与面试', '评分录用', '在岗工时审核', '周期考核评价'],
    DEPT_ADMIN: ['本院系数据隔离视图', '岗位名额与经费初审', '院系聘用报表', '异常预警监控'],
    INTL_OFFICER: ['国际生 TA 岗位会签', '签证/语言/工时特批留痕', '国际生合规监控'],
    SCHOOL_ADMIN: ['全校跨院系视图', '审批流与考核模板配置', '预算余额监控', '财务发放回写', '全流程审计追溯']
  }
  return map[user.value?.role] || []
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
  if (res.code !== 200) store.notify(res.message || '下载失败', 'error')
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
