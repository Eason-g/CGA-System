<template>
  <div>
    <!-- Airy Editorial Hero with Unified Elevated Search Capsule -->
    <section class="hero-section">
      <div class="hero-inner">
        <div class="hero-eyebrow">{{ store.currentUniversity.name }} · 2026-2027 学年三助招募</div>
        <h1 class="hero-title mt-8">探索适合你的助教、助研与助管岗位</h1>

        <div class="unified-search mt-20">
          <FluentIcon name="search" :size="17" class="u-search-ic" />
          <input
            v-model="keyword"
            type="text"
            class="u-search-input"
            placeholder="输入岗位名称、课程代码或技能关键词..."
          />
          <button type="button" class="f-btn f-btn-primary u-search-btn" @click="keyword = keyword.trim()">
            搜索岗位
          </button>
        </div>

        <div class="hero-pills mt-16">
          <button
            v-for="p in filterPills"
            :key="p.key"
            type="button"
            class="f-pill-filter"
            :class="{ active: activeFilter === p.key }"
            @click="activeFilter = p.key"
          >
            {{ p.label }}
          </button>
          <button
            type="button"
            class="f-pill-filter"
            :class="{ active: sortByMatch }"
            @click="sortByMatch = !sortByMatch"
          >
            <FluentIcon name="sparkles" :size="13" />
            <span>技能推荐</span>
          </button>
        </div>
      </div>
    </section>

    <!-- Mobile Compact Pinned Hours Strip -->
    <div class="mobile-hours-strip">
      <div class="flex items-center justify-between text-xs">
        <span class="fw-600">已占周工时 {{ allocatedHours }}/{{ maxHours }}h</span>
        <span :class="remainingHours <= 4 ? 'text-error fw-600' : 'text-blue fw-600'">可报余量 {{ remainingHours }}h</span>
      </div>
      <div class="f-progress-track mt-6">
        <div
          class="f-progress-fill"
          :class="hoursRatio >= 100 ? 'error' : hoursRatio >= 80 ? 'caution' : ''"
          :style="{ width: Math.min(100, hoursRatio) + '%' }"
        />
      </div>
    </div>

    <!-- Main 12-Column Storefront (8 + 4) -->
    <div class="storefront-container">
      <!-- Left 8 Columns: Clean Uniform Job Cards -->
      <section class="jobs-column">
        <div class="flex items-center justify-between mb-14 flex-wrap gap-8">
          <span class="text-xs text-muted">
            在招岗位 <strong class="text-text tabular-nums">{{ displayedJobs.length }}</strong> 个 · 点击岗位可打开右侧详情，或勾选最多 3 个横向对比
          </span>
          <span v-if="sortByMatch" class="f-pill f-pill-blue">已按技能匹配排序</span>
        </div>

        <div v-if="displayedJobs.length" class="job-stream">
          <article
            v-for="item in displayedJobs"
            :key="item.job.id"
            class="acrylic-card interactive job-card"
            :class="{ 'is-selected': compareIds.includes(item.job.id) }"
          >
            <div class="job-top" @click="detailJob = item.job">
              <div class="min-w-0">
                <div class="flex items-center gap-8 flex-wrap">
                  <span class="f-type-tag" :class="item.job.type.toLowerCase()">{{ item.job.type }}</span>
                  <h2 class="job-title">{{ item.job.title }}</h2>
                </div>
                <div class="f-meta-line mt-8">
                  <span>{{ store.getDeptShortName(item.job.deptId) }}</span>
                  <span class="f-meta-sep">·</span>
                  <span class="tabular-nums">{{ item.job.weeklyHours }}h/周 · {{ item.job.weeks }}周</span>
                  <span class="f-meta-sep">·</span>
                  <span class="tabular-nums">余 {{ Math.max(0, item.job.quota - item.job.hiredCount) }}/{{ item.job.quota }} 名额</span>
                  <template v-if="item.job.allowInternational">
                    <span class="f-meta-sep">·</span>
                    <span class="text-blue fw-600">国际生可报</span>
                  </template>
                  <template v-if="sortByMatch && item.match.matched.length">
                    <span class="f-meta-sep">·</span>
                    <span class="text-success fw-600">匹配 {{ item.match.score }}%</span>
                  </template>
                </div>
              </div>

              <div class="rate-block">
                <div class="rate-value tabular-nums">¥{{ item.job.hourlyRate }}<span class="rate-unit">/h</span></div>
              </div>
            </div>

            <div class="job-bottom mt-14">
              <div class="flex items-center gap-10">
                <span class="text-xs text-subtle">
                  {{ store.getUser(item.job.mentorId)?.name || '-' }} · {{ item.job.deadline }} 截止
                </span>
              </div>

              <div class="flex items-center gap-8">
                <span v-if="!item.check.alreadyApplied && !item.check.passed" class="f-pill f-pill-caution">需合规/特批</span>
                <button
                  type="button"
                  class="compare-toggle-btn"
                  :class="{ active: compareIds.includes(item.job.id) }"
                  @click.stop="toggleCompare(item.job.id)"
                >
                  <FluentIcon :name="compareIds.includes(item.job.id) ? 'check' : 'compare'" :size="13" />
                  <span>{{ compareIds.includes(item.job.id) ? '已加对比' : '对比' }}</span>
                </button>
                <button type="button" class="f-btn f-btn-sm" @click.stop="detailJob = item.job">
                  详情
                </button>
                <button
                  type="button"
                  class="f-btn f-btn-primary f-btn-sm"
                  :disabled="item.check.alreadyApplied || item.check.quotaFull"
                  @click.stop="applyModalJob = item.job"
                >
                  {{ item.check.alreadyApplied ? '已投递' : item.check.quotaFull ? '已招满' : '一键申请' }}
                </button>
              </div>
            </div>
          </article>
        </div>

        <EmptyState
          v-else
          icon="search"
          description="未找到符合条件的岗位"
          action-text="查看全部岗位"
          @action="resetFilters"
        />
      </section>

      <!-- Right 4 Columns: Calm Sticky Personal Panel -->
      <aside class="sidebar-column">
        <!-- Card 1: Weekly Hours Gauge -->
        <div class="acrylic-card p-20">
          <div class="flex items-center justify-between">
            <h3 class="text-sm fw-600">跨岗周工时</h3>
            <span class="f-pill" :class="hoursRatio >= 100 ? 'f-pill-error' : hoursRatio >= 80 ? 'f-pill-caution' : 'f-pill-blue'">
              {{ allocatedHours }}/{{ maxHours }}h
            </span>
          </div>

          <div class="gauge-row mt-14">
            <svg viewBox="0 0 100 100" class="ring-svg" aria-label="周工时占比">
              <circle cx="50" cy="50" r="40" fill="none" stroke="rgba(15,23,42,0.07)" stroke-width="9" />
              <circle
                cx="50"
                cy="50"
                r="40"
                fill="none"
                :stroke="hoursRatio >= 100 ? '#d92d20' : hoursRatio >= 80 ? '#ffb900' : '#0078d4'"
                stroke-width="9"
                stroke-linecap="round"
                :stroke-dasharray="`${ringDash} ${251.2 - ringDash}`"
                transform="rotate(-90 50 50)"
              />
              <text x="50" y="48" text-anchor="middle" font-size="18" font-weight="600" fill="#181c22">{{ allocatedHours }}h</text>
              <text x="50" y="63" text-anchor="middle" font-size="9.5" fill="#5c6573">余 {{ remainingHours }}h</text>
            </svg>

            <div class="flex-1 flex-col gap-8 min-w-0">
              <div v-for="c in myActiveContracts" :key="c.id" class="hours-seg-item">
                <span class="f-type-tag" :class="store.getJob(c.jobId)?.type?.toLowerCase()">
                  {{ store.getJob(c.jobId)?.type }}
                </span>
                <span class="text-xs truncate flex-1">{{ store.getJob(c.jobId)?.title }}</span>
                <strong class="text-xs tabular-nums">{{ c.weeklyHours }}h</strong>
              </div>
              <div v-if="!myActiveContracts.length" class="text-xs text-muted">当前无已占工时</div>
            </div>
          </div>
        </div>

        <!-- Card 2: Personal Compliance -->
        <div class="acrylic-card p-20">
          <div class="flex items-center justify-between">
            <h3 class="text-sm fw-600">合规资质</h3>
            <router-link to="/student/profile" class="f-btn-link text-xs">个人主页 →</router-link>
          </div>

          <div class="qual-summary mt-12">
            <div v-if="student?.isInternational" class="qual-item">
              <FluentIcon :name="student.visaValid ? 'check-circle' : 'x-circle'" :size="15" :class="student.visaValid ? 'ic-ok' : 'ic-err'" />
              <span>签证：{{ student.visaValid ? 'X1 有效' : '失效' }}</span>
            </div>
            <div class="qual-item">
              <FluentIcon name="check-circle" :size="15" class="ic-ok" />
              <span>语言：{{ student?.languageCert }}</span>
            </div>
            <div class="qual-item">
              <FluentIcon :name="pendingSignContracts.length ? 'alert' : 'check-circle'" :size="15" :class="pendingSignContracts.length ? 'ic-warn' : 'ic-ok'" />
              <span>电子协议：{{ pendingSignContracts.length ? '有待签署' : '已生效' }}</span>
            </div>
          </div>
        </div>

        <!-- Card 3: Contextual To-Dos -->
        <div v-if="pendingSignContracts.length || missingWeek12Contracts.length" class="acrylic-card p-20">
          <h3 class="text-sm fw-600 mb-10">待办提醒</h3>
          <div class="flex-col gap-8">
            <router-link v-for="c in pendingSignContracts" :key="c.id" to="/student/contracts" class="todo-item caution">
              <span>待签合同 · {{ store.getJob(c.jobId)?.title }}</span>
            </router-link>
            <router-link v-if="missingWeek12Contracts.length" to="/student/contracts" class="todo-item info">
              <span>第 {{ store.db.meta.currentWeekNo }} 周工时待填报 →</span>
            </router-link>
          </div>
        </div>
      </aside>
    </div>

    <!-- Floating Frosted-Glass Job Compare Dock -->
    <div v-if="comparedJobs.length > 0" class="compare-dock">
      <div class="dock-chips">
        <div v-for="j in comparedJobs" :key="j.id" class="dock-chip">
          <span class="f-type-tag" :class="j.type.toLowerCase()">{{ j.type }}</span>
          <span class="dock-chip-title truncate">{{ j.title }}</span>
          <button type="button" class="dock-remove" @click="toggleCompare(j.id)" aria-label="移除">
            <FluentIcon name="close" :size="12" />
          </button>
        </div>
      </div>
      <div class="dock-actions">
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="compareIds = []">清空</button>
        <button
          type="button"
          class="f-btn f-btn-primary f-btn-sm"
          :disabled="comparedJobs.length < 2"
          @click="showCompareModal = true"
        >
          <FluentIcon name="compare" :size="14" />
          <span>开始对比 ({{ comparedJobs.length }}/3)</span>
        </button>
      </div>
    </div>

    <!-- Right-Side Slide-Over Job Detail Drawer -->
    <JobDetailDrawer
      v-if="detailJob"
      :job="detailJob"
      :is-compared="compareIds.includes(detailJob.id)"
      @close="detailJob = null"
      @toggle-compare="toggleCompare"
      @apply="openApplyFromSub"
    />

    <!-- Side-by-Side Job Comparison Modal -->
    <JobCompareModal
      v-if="showCompareModal && comparedJobs.length"
      :jobs="comparedJobs"
      @close="showCompareModal = false"
      @remove="removeCompare"
      @clear="clearCompare"
      @apply="openApplyFromSub"
    />

    <!-- Apply & Hard Compliance Check Modal -->
    <ApplyJobModal
      v-if="applyModalJob"
      :job="applyModalJob"
      @close="applyModalJob = null"
    />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import {
  getStudentHoursSummary,
  getStudentActiveContracts,
  validateStudentApplication,
  calcSkillMatch
} from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import ApplyJobModal from '@/components/ApplyJobModal.vue'
import JobDetailDrawer from '@/components/JobDetailDrawer.vue'
import JobCompareModal from '@/components/JobCompareModal.vue'

const store = useAppStore()
const student = computed(() => store.currentUser)

const keyword = ref('')
const activeFilter = ref('ALL')
const sortByMatch = ref(false)
const applyModalJob = ref(null)
const detailJob = ref(null)
const compareIds = ref([])
const showCompareModal = ref(false)

const filterPills = [
  { key: 'ALL', label: '全部岗位' },
  { key: 'TA', label: '助教 TA' },
  { key: 'RA', label: '助研 RA' },
  { key: 'GA', label: '助管 GA' },
  { key: 'INTL', label: '国际生可报' }
]

// 优先使用快照里的 hoursSummary（后端跨院累计），没有时按可见合同计算
const hoursSummary = computed(() => getStudentHoursSummary(store.db, student.value))
const maxHours = computed(() => hoursSummary.value.max)
const allocatedHours = computed(() => hoursSummary.value.allocated)
const remainingHours = computed(() => Math.max(0, maxHours.value - allocatedHours.value))
const hoursRatio = computed(() => Math.round((allocatedHours.value / maxHours.value) * 100))
const ringDash = computed(() => Math.min(251.2, (allocatedHours.value / maxHours.value) * 251.2))

const myActiveContracts = computed(() => (student.value ? getStudentActiveContracts(store.db, student.value.id) : []))
const pendingSignContracts = computed(() =>
  store.db.contracts.filter((c) => c.studentId === student.value?.id && c.status === 'PENDING_SIGN')
)

const missingWeek12Contracts = computed(() => {
  const weekNo = store.db.meta.currentWeekNo
  return store.db.contracts.filter((c) => {
    if (c.studentId !== student.value?.id || c.status !== 'ACTIVE') return false
    return !store.db.timesheets.some((t) => t.contractId === c.id && t.weekNo === weekNo && t.status !== 'REJECTED')
  })
})

const displayedJobs = computed(() => {
  const published = store.db.jobs.filter((j) => j.status === 'PUBLISHED')
  const mapped = published
    .filter((job) => {
      if (activeFilter.value === 'TA' && job.type !== 'TA') return false
      if (activeFilter.value === 'RA' && job.type !== 'RA') return false
      if (activeFilter.value === 'GA' && job.type !== 'GA') return false
      if (activeFilter.value === 'INTL' && !job.allowInternational) return false
      if (keyword.value.trim()) {
        const q = keyword.value.trim().toLowerCase()
        const hay = `${job.title} ${job.description} ${(job.skills || []).join(' ')} ${JSON.stringify(job.extraFields || {})}`.toLowerCase()
        if (!hay.includes(q)) return false
      }
      return true
    })
    .map((job) => ({
      job,
      check: validateStudentApplication(store.db, student.value, job),
      match: calcSkillMatch(student.value, job)
    }))

  if (sortByMatch.value) mapped.sort((a, b) => b.match.score - a.match.score)
  return mapped
})

const comparedJobs = computed(() =>
  compareIds.value.map((id) => store.getJob(id)).filter(Boolean)
)

function toggleCompare(jobId) {
  const idx = compareIds.value.indexOf(jobId)
  if (idx >= 0) {
    compareIds.value.splice(idx, 1)
  } else {
    if (compareIds.value.length >= 3) {
      store.notify('最多同时对比 3 个岗位，请先移除一个再添加', 'caution')
      return
    }
    compareIds.value.push(jobId)
  }
}

function removeCompare(jobId) {
  compareIds.value = compareIds.value.filter((id) => id !== jobId)
  if (compareIds.value.length === 0) showCompareModal.value = false
}

function clearCompare() {
  compareIds.value = []
  showCompareModal.value = false
}

function openApplyFromSub(job) {
  detailJob.value = null
  showCompareModal.value = false
  applyModalJob.value = job
}

function resetFilters() {
  keyword.value = ''
  activeFilter.value = 'ALL'
}
</script>

<style scoped>
.hero-section {
  width: 100%;
  padding: 40px 24px 32px;
  background:
    radial-gradient(620px circle at 24% 10%, rgba(0, 120, 212, 0.11), transparent 62%),
    radial-gradient(520px circle at 78% 80%, rgba(56, 189, 248, 0.09), transparent 60%),
    rgba(255, 255, 255, 0.62);
  border-bottom: 1px solid var(--color-border-light);
}

.hero-inner {
  max-width: 1180px;
  margin: 0 auto;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
  padding: 4px 12px;
  border-radius: 999px;
}

.hero-title {
  font-size: var(--text-heading-lg);
  font-weight: 600;
  line-height: 1.24;
  letter-spacing: -0.02em;
}

.unified-search {
  width: 600px;
  max-width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 5px 5px 16px;
  background: #ffffff;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-card);
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}

.unified-search:focus-within {
  border-color: var(--color-fluent-blue);
  box-shadow: 0 0 0 3px rgba(0, 120, 212, 0.14), var(--shadow-card);
}

.u-search-ic { color: var(--color-subtle); flex-shrink: 0; }
.u-search-input { flex: 1; border: none; background: transparent; height: 36px; font-size: 14px; outline: none; box-shadow: none !important; }
.u-search-btn { height: 36px; border-radius: 8px; padding: 0 18px; }

.hero-pills { display: flex; align-items: center; justify-content: center; gap: 8px; flex-wrap: wrap; }
.hero-pills .f-pill-filter { display: inline-flex; align-items: center; gap: 5px; }

.mobile-hours-strip { display: none; }

.storefront-container {
  max-width: 1180px;
  margin: 26px auto 0;
  padding: 0 24px 64px;
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: 24px;
  align-items: start;
}

.jobs-column { grid-column: span 8; }

.sidebar-column {
  grid-column: span 4;
  position: sticky;
  top: 80px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.job-stream { display: flex; flex-direction: column; gap: 14px; }

.job-card { padding: 20px 22px; }
.job-card.is-selected {
  border-color: rgba(0, 120, 212, 0.42);
  background: rgba(255, 255, 255, 0.96);
}

.job-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
}

.job-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.35;
  transition: color var(--motion-fast);
}

.job-top:hover .job-title { color: var(--color-fluent-blue); }

.rate-block { flex-shrink: 0; text-align: right; }
.rate-value { font-size: 20px; font-weight: 600; color: var(--color-fluent-blue); line-height: 1.1; }
.rate-unit { font-size: 12px; font-weight: 400; color: var(--color-muted); }

.job-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-light);
}

.compare-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px;
  border-radius: 6px;
  border: 1px solid var(--color-border-light);
  background: transparent;
  color: var(--color-muted);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all var(--motion-fast);
}

.compare-toggle-btn:hover {
  border-color: var(--color-blue-border);
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
}

.compare-toggle-btn.active {
  border-color: var(--color-fluent-blue);
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
}

/* Floating Frosted Compare Dock */
.compare-dock {
  position: fixed;
  left: 50%;
  bottom: 22px;
  transform: translateX(-50%);
  z-index: 750;
  width: min(820px, calc(100vw - 32px));
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(28px) saturate(160%);
  -webkit-backdrop-filter: blur(28px) saturate(160%);
  border: 1px solid var(--color-blue-border);
  box-shadow: 0 14px 36px -6px rgba(15, 23, 42, 0.16), 0 2px 6px rgba(15, 23, 42, 0.06);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  animation: modalPop 180ms cubic-bezier(0.2, 0, 0, 1);
}

.dock-chips {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  flex-wrap: wrap;
}

.dock-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 210px;
  padding: 4px 6px 4px 6px;
  border-radius: 8px;
  background: var(--surface-subtle);
  font-size: 12px;
}

.dock-chip-title {
  font-weight: 600;
  color: var(--color-text);
}

.dock-remove {
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 5px;
  background: transparent;
  color: var(--color-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  flex-shrink: 0;
}

.dock-remove:hover { background: var(--color-error-bg); color: var(--color-error); }

.dock-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.gauge-row { display: flex; align-items: center; gap: 14px; }
.ring-svg { width: 84px; height: 84px; flex-shrink: 0; }
.hours-seg-item { display: flex; align-items: center; gap: 6px; }

.qual-summary { display: flex; flex-direction: column; gap: 9px; }
.qual-item { display: flex; align-items: center; gap: 8px; font-size: 12.5px; }

.ic-ok { color: var(--color-success); flex-shrink: 0; }
.ic-warn { color: var(--color-caution-text); flex-shrink: 0; }
.ic-err { color: var(--color-error); flex-shrink: 0; }

.todo-item {
  display: block;
  padding: 9px 12px;
  border-radius: 8px;
  background: var(--surface-subtle);
  color: var(--color-text);
  font-size: 12.5px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: background var(--motion-fast);
}

.todo-item:hover { background: var(--color-blue-subtle); color: var(--color-fluent-blue); }
.todo-item.caution { border-left: 3px solid var(--color-caution-bright); }
.todo-item.info { border-left: 3px solid var(--color-fluent-blue); }

.text-text { color: var(--color-text); }
.min-w-0 { min-width: 0; }
.flex-1 { flex: 1; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mt-6 { margin-top: 6px; }
.mt-14 { margin-top: 14px; }
.mb-10 { margin-bottom: 10px; }
.mb-14 { margin-bottom: 14px; }

@media (max-width: 960px) {
  .hero-title { font-size: 24px; }
  .mobile-hours-strip {
    display: block;
    position: sticky;
    top: 101px;
    z-index: 400;
    padding: 10px 16px;
    background: rgba(255, 255, 255, 0.94);
    backdrop-filter: blur(20px);
    border-bottom: 1px solid var(--color-border-light);
  }
  .storefront-container { grid-template-columns: minmax(0, 1fr); padding: 0 16px 80px; }
  .jobs-column, .sidebar-column { grid-column: span 1; position: static; min-width: 0; }
}

/* 手机（≤768px）：卡片满宽、操作按钮换行，对比栏让出底部标签栏 */
@media (max-width: 768px) {
  .hero-section { padding: 20px 16px 18px; }
  .hero-eyebrow { font-size: 13px; max-width: 100%; line-height: 1.4; }
  .hero-title { font-size: 21px; }
  .unified-search { padding: 4px 4px 4px 12px; }
  .u-search-input { min-width: 0; font-size: 16px; height: 40px; }
  .u-search-btn { height: 40px; padding: 0 14px; flex-shrink: 0; }
  .hero-pills {
    flex-wrap: nowrap;
    justify-content: flex-start;
    max-width: 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    padding-bottom: 2px;
  }
  .hero-pills { scrollbar-width: none; }
  .hero-pills::-webkit-scrollbar { display: none; }
  .hero-pills .f-pill-filter { flex-shrink: 0; }

  .mobile-hours-strip { top: 56px; padding: 10px 16px; }
  .storefront-container { margin-top: 16px; gap: 16px; padding-bottom: 24px; }
  .job-card { padding: 16px; }
  .job-top { gap: 12px; }
  .job-top > .min-w-0 { flex: 1; }
  .job-title { font-size: 16px; }
  .rate-value { font-size: 18px; }
  .rate-unit { font-size: 13px; }

  .job-bottom { flex-direction: column; align-items: stretch; }
  .job-bottom .text-xs { font-size: 13px; }
  .job-bottom > div:last-child { flex-wrap: wrap; }
  .job-bottom > div:last-child > button { flex: 1 1 0; min-width: 0; }
  .job-bottom > div:last-child > .f-pill { flex-basis: 100%; justify-content: center; }
  .compare-toggle-btn { height: 40px; justify-content: center; font-size: 14px; }

  .hours-seg-item .text-xs, .qual-item, .todo-item { font-size: 14px; }
  .todo-item { padding: 11px 12px; }

  .compare-dock {
    bottom: calc(72px + env(safe-area-inset-bottom));
    width: calc(100vw - 16px);
    padding: 8px 10px;
    gap: 8px;
  }
  .dock-chips { flex-basis: 100%; }
  .dock-chip { max-width: 100%; min-width: 0; font-size: 13px; }
  .dock-remove { width: 32px; height: 32px; }
  .dock-actions { flex: 1; justify-content: flex-end; }
  .dock-actions .f-btn-primary { flex: 1; }
}
</style>
