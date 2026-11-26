<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div class="flex items-center gap-12">
        <h1 class="text-title">周期性考核评价</h1>
        <button type="button" class="f-expand-trigger" @click="showRules = !showRules">
          <span>分档规则</span>
          <FluentIcon :name="showRules ? 'chevron-up' : 'chevron-down'" :size="14" />
        </button>
      </div>
      <button v-if="canEvaluate" type="button" class="f-btn f-btn-primary" @click="openNewEvalModal">
        <FluentIcon name="award" :size="15" />
        <span>发起考核</span>
      </button>
    </header>

    <div v-if="showRules" class="f-expand-panel" style="margin-top: -8px;">
      维度权重动态读取校级配置：≥90 分（优秀 · 可续聘 · 1.1x），60–89 分（合格 · 可续聘 · 1.0x），&lt;60 分（不合格 · 不予续聘 · 0.85x）。
    </div>

    <section class="acrylic-card table-card">
      <div v-if="visibleEvaluations.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>受聘学生</th>
              <th>岗位</th>
              <th>周期</th>
              <th>得分 / 分档</th>
              <th>续聘与系数</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="ev in visibleEvaluations" :key="ev.id">
              <tr>
                <td class="fw-600">{{ store.getUser(ev.studentId)?.name }}</td>
                <td>
                  <span class="f-type-tag mr-6" :class="ev.jobType.toLowerCase()">{{ ev.jobType }}</span>
                  <span>{{ store.getJob(ev.jobId)?.title }}</span>
                </td>
                <td class="text-muted text-xs">{{ ev.period }}</td>
                <td>
                  <div class="flex items-center gap-6">
                    <span class="fw-600 tabular-nums">{{ ev.totalScore }}</span>
                    <span class="f-pill" :class="ev.grade === '优秀' ? 'f-pill-success' : ev.grade === '合格' ? 'f-pill-blue' : 'f-pill-error'">
                      {{ ev.grade }}
                    </span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-6">
                    <span class="f-pill" :class="ev.renewalEligible ? 'f-pill-success' : 'f-pill-error'">
                      {{ ev.renewalEligible ? '可续聘' : '不续聘' }}
                    </span>
                    <span class="text-xs text-blue fw-600 tabular-nums">{{ ev.coefficient }}x</span>
                  </div>
                </td>
                <td>
                  <div class="flex items-center gap-8">
                    <button type="button" class="f-expand-trigger" @click="toggleEval(ev.id)">
                      <span>{{ expandedIds.has(ev.id) ? '收起' : '明细' }}</span>
                      <FluentIcon :name="expandedIds.has(ev.id) ? 'chevron-up' : 'chevron-down'" :size="13" />
                    </button>
                    <button type="button" class="f-btn-link text-xs" @click="store.openArchive(ev.contractId)">归档</button>
                  </div>
                </td>
              </tr>
              <tr v-if="expandedIds.has(ev.id)" class="expand-row">
                <td colspan="6">
                  <div class="expand-inner">
                    <div class="flex flex-wrap gap-16 text-xs">
                      <span v-for="d in ev.dimensions" :key="d.key">
                        {{ d.label }}（{{ d.weight }}%）：<strong class="tabular-nums">{{ d.score }} 分</strong>
                      </span>
                      <span v-if="ev.courseEvalSync" class="text-blue">
                        课程评教联动：{{ ev.courseEvalSync.responseCount }} 份 · 满意度 {{ ev.courseEvalSync.satisfactionRate }}%
                      </span>
                    </div>
                    <div v-if="ev.comment" class="text-xs text-muted mt-6">评语：{{ ev.comment }}</div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="award" description="暂无考核评价记录" />
    </section>

    <!-- Evaluation Modal -->
    <div v-if="showModal" class="f-overlay" @click.self="showModal = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="填写考核评分">
        <header class="f-modal-header">
          <h2 class="text-sub">发起周期考核评价</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showModal = false">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>

        <div class="f-modal-body flex-col gap-16">
          <div class="f-field">
            <label class="f-label">选择在岗协议 <span class="req">*</span></label>
            <select v-model="evalForm.contractId" class="f-select" @change="initScoresForContract">
              <option v-for="c in activeContracts" :key="c.id" :value="c.id">
                {{ store.getUser(c.studentId)?.name }} · [{{ store.getJob(c.jobId)?.type }}] {{ store.getJob(c.jobId)?.title }}
              </option>
            </select>
          </div>

          <div class="f-field">
            <label class="f-label">考核周期</label>
            <input v-model="evalForm.period" type="text" class="f-input" />
          </div>

          <div v-if="selectedContractJob?.type === 'TA'" class="f-banner info">
            <div class="text-xs flex-1">已同步课程评教：48 份问卷，满意度 94.2%（折算 93 分）</div>
            <button type="button" class="f-btn f-btn-sm" @click="applyCourseEvalScore">一键代入</button>
          </div>

          <div class="dim-box">
            <div v-for="dim in activeTemplate" :key="dim.key" class="dim-row">
              <div class="flex-1 text-xs">
                <span class="fw-600">{{ dim.label }}</span>
                <span class="text-muted">（{{ dim.weight }}%）</span>
              </div>
              <input v-model.number="evalForm.dimensionScores[dim.key]" type="number" min="0" max="100" class="f-input score-input tabular-nums" />
            </div>

            <div class="preview-result mt-12">
              <span class="fw-600 tabular-nums">加权总分：{{ liveScore }} 分</span>
              <div class="flex items-center gap-6">
                <span class="f-pill" :class="`f-pill-${liveGrade.tone}`">{{ liveGrade.grade }}</span>
                <span class="f-pill f-pill-blue tabular-nums">{{ liveGrade.coefficient }}x</span>
              </div>
            </div>
          </div>

          <div class="f-field">
            <label class="f-label">综合评语</label>
            <textarea v-model="evalForm.comment" class="f-textarea" rows="2" />
          </div>
        </div>

        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="showModal = false">取消</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="!evalForm.contractId || busy" @click="submitEval">提交考核</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { filterByRoleDept, gradeFromScore } from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const user = computed(() => store.currentUser)

const canEvaluate = computed(() => ['MENTOR', 'DEPT_ADMIN', 'SCHOOL_ADMIN'].includes(user.value?.role))
const visibleEvaluations = computed(() => filterByRoleDept(store.db, store.db.evaluations, user.value, 'EVALUATION'))
const activeContracts = computed(() =>
  filterByRoleDept(store.db, store.db.contracts, user.value, 'CONTRACT').filter((c) => c.status === 'ACTIVE')
)

const showRules = ref(false)
const showModal = ref(false)
const busy = ref(false)
const courseEvalLinked = ref(false)
const expandedIds = ref(new Set())

const evalForm = reactive({
  contractId: '',
  period: '2026-11 月度考核',
  dimensionScores: {},
  comment: '履职尽责，按期完成任务。'
})

const selectedContract = computed(() => store.db.contracts.find((c) => c.id === evalForm.contractId) || null)
const selectedContractJob = computed(() => (selectedContract.value ? store.getJob(selectedContract.value.jobId) : null))
const activeTemplate = computed(() => store.db.configs.evaluationTemplates[selectedContractJob.value?.type || 'TA'] || [])

const liveScore = computed(() => {
  const total = activeTemplate.value.reduce((s, d) => s + (Number(evalForm.dimensionScores[d.key] || 0) * d.weight) / 100, 0)
  return Math.round(total * 10) / 10
})
const liveGrade = computed(() => gradeFromScore(liveScore.value))

function toggleEval(id) {
  const s = new Set(expandedIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedIds.value = s
}

function initScoresForContract() {
  courseEvalLinked.value = false
  const scores = {}
  activeTemplate.value.forEach((d) => { scores[d.key] = 90 })
  evalForm.dimensionScores = scores
}

function openNewEvalModal() {
  evalForm.contractId = activeContracts.value[0]?.id || ''
  initScoresForContract()
  showModal.value = true
}

function applyCourseEvalScore() {
  const serviceDim = activeTemplate.value.find((d) => d.key === 'service') || activeTemplate.value[1]
  if (serviceDim) {
    evalForm.dimensionScores[serviceDim.key] = 93
    courseEvalLinked.value = true
    store.notify('已代入课程评教分数（93 分）', 'info')
  }
}

async function submitEval() {
  busy.value = true
  try {
    const res = await store.mutate(() =>
      api.submitEvaluation(
        {
          contractId: evalForm.contractId,
          period: evalForm.period,
          dimensionScores: evalForm.dimensionScores,
          comment: evalForm.comment,
          courseEvalSync: courseEvalLinked.value ? { responseCount: 48, satisfactionRate: 94.2, avgRating: 4.71 } : null
        },
        user.value
      )
    )
    if (res.code === 200) showModal.value = false
  } finally { busy.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.expand-row { background: rgba(243, 242, 241, 0.55); }
.expand-inner { padding: 4px 2px; }
.dim-box { padding: 14px; border-radius: 4px; background: #faf9f8; border: 1px solid var(--color-border-light); }
.dim-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 6px 0; border-bottom: 1px solid var(--color-border-light); }
.dim-row:last-of-type { border-bottom: none; }
.score-input { width: 88px; }
.preview-result { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; background: #ffffff; border: 1px solid var(--color-blue-border); border-radius: 4px; }
.flex-1 { flex: 1; }
.mr-6 { margin-right: 6px; }
.mt-6 { margin-top: 6px; }
</style>
