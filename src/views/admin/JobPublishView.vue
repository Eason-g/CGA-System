<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">发布新岗位</h1>
      <router-link to="/admin/jobs" class="f-btn">返回列表</router-link>
    </header>

    <div class="publish-layout">
      <!-- Left: Single-column Form (max-width 640px per Fluent spec 2.4.2) -->
      <form class="acrylic-card p-24 publish-form flex-col gap-16" @submit.prevent="handleSubmit">
        <div>
          <label class="f-label mb-8">1. 选择岗位类别（共用流程引擎，字段动态切换） <span class="req">*</span></label>
          <div class="type-selector-grid">
            <button
              v-for="item in jobTypes"
              :key="item.value"
              type="button"
              class="type-option"
              :class="{ active: form.type === item.value }"
              @click="switchJobType(item.value)"
            >
              <div class="flex items-center justify-between">
                <span class="fw-600">{{ item.title }}</span>
                <span class="f-type-tag" :class="item.value.toLowerCase()">{{ item.value }}</span>
              </div>
              <div class="text-xs text-muted mt-4">{{ item.desc }}</div>
            </button>
          </div>
        </div>

        <div class="f-field">
          <label class="f-label">岗位名称 <span class="req">*</span></label>
          <input
            v-model="form.title"
            type="text"
            class="f-input"
            required
            placeholder="例如：《计算机网络（双语）》实验助教 / 量子计算课题助研"
          />
        </div>

        <div class="grid-2">
          <div class="f-field">
            <label class="f-label">设岗院系 / 部门 <span class="req">*</span></label>
            <select v-model="form.deptId" class="f-select" :disabled="user?.role !== 'SCHOOL_ADMIN'">
              <option v-for="d in store.db.departments" :key="d.id" :value="d.id">
                {{ d.name }}
              </option>
            </select>
          </div>

          <div class="f-field">
            <label class="f-label">经费来源账号（实时联动余额） <span class="req">*</span></label>
            <select v-model="form.budgetId" class="f-select">
              <option v-for="b in selectableBudgets" :key="b.id" :value="b.id">
                {{ b.name }}（可用 ¥{{ getBudgetAvailable(b).toLocaleString() }}）
              </option>
            </select>
          </div>
        </div>

        <div class="grid-4">
          <div class="f-field">
            <label class="f-label">招聘人数 <span class="req">*</span></label>
            <input v-model.number="form.quota" type="number" min="1" max="20" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">时薪（元/h） <span class="req">*</span></label>
            <input v-model.number="form.hourlyRate" type="number" min="20" max="120" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">每周工时（h） <span class="req">*</span></label>
            <input v-model.number="form.weeklyHours" type="number" min="2" max="20" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">聘期周数 <span class="req">*</span></label>
            <input v-model.number="form.weeks" type="number" min="1" max="36" class="f-input tabular-nums" required />
          </div>
        </div>

        <!-- Dynamic Differential Fields Block -->
        <div class="dynamic-section">
          <div class="text-xs fw-600 text-blue mb-12">
            2. {{ form.type }} 岗位差异化专属字段
          </div>

          <!-- TA Dynamic Fields -->
          <template v-if="form.type === 'TA'">
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">绑定课程编号 <span class="req">*</span></label>
                <input v-model="extraTA.courseCode" type="text" class="f-input" placeholder="如 CS3090" required />
              </div>
              <div class="f-field">
                <label class="f-label">授课学期 <span class="req">*</span></label>
                <input v-model="extraTA.semester" type="text" class="f-input" required />
              </div>
            </div>

            <div class="grid-2 mt-12">
              <div class="f-field">
                <label class="f-label">课程名称 <span class="req">*</span></label>
                <input v-model="extraTA.courseName" type="text" class="f-input" placeholder="如 操作系统原理（双语）" required />
              </div>
              <div class="f-field">
                <label class="f-label">语言能力最低要求</label>
                <select v-model.number="extraTA.minLangScore" class="f-select">
                  <option :value="0">无特殊语言限制</option>
                  <option :value="6.0">IELTS 6.0 / HSK 5</option>
                  <option :value="6.5">IELTS 6.5 / CET-6 550+</option>
                  <option :value="7.0">IELTS 7.0 / TOEFL 95+</option>
                  <option :value="7.5">IELTS 7.5 / TOEFL 105+</option>
                </select>
              </div>
            </div>

            <div class="f-field mt-12">
              <label class="f-label">教学周排班安排</label>
              <input v-model="extraTA.scheduleText" type="text" class="f-input" placeholder="如 周二 14:00-16:00 上机实验；周四 10:00-12:00 答疑" />
            </div>

            <label class="intl-toggle mt-12">
              <input v-model="form.allowInternational" type="checkbox" class="f-checkbox" />
              <div>
                <div class="fw-600 text-sm">面向国际生开放（勾选后自动触发国际生办专项会签）</div>
                <div class="text-xs text-muted">国际生申请人将自动校验 X1 有效学习居留许可、语言证明与 20h 周工时上限</div>
              </div>
            </label>
          </template>

          <!-- RA Dynamic Fields -->
          <template v-else-if="form.type === 'RA'">
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">绑定科研项目编号 <span class="req">*</span></label>
                <input v-model="extraRA.projectCode" type="text" class="f-input" placeholder="如 NSFC-62601234" required />
              </div>
              <div class="f-field">
                <label class="f-label">研究方向 <span class="req">*</span></label>
                <input v-model="extraRA.researchDirection" type="text" class="f-input" placeholder="如 多模态推理与系统优化" required />
              </div>
            </div>

            <div class="f-field mt-12">
              <label class="f-label">课题名称 <span class="req">*</span></label>
              <input v-model="extraRA.projectName" type="text" class="f-input" required />
            </div>

            <div class="f-field mt-12">
              <label class="f-label">阶段性里程碑（Milestone，每行一条）</label>
              <textarea v-model="extraRA.milestonesText" class="f-textarea" rows="3" />
            </div>

            <label class="intl-toggle mt-12">
              <input v-model="form.allowInternational" type="checkbox" class="f-checkbox" />
              <div>
                <div class="fw-600 text-sm">允许国际研究生申请该科研助研岗位</div>
                <div class="text-xs text-muted">仍将强制执行国际生签证与 20h 累计工时校验</div>
              </div>
            </label>
          </template>

          <!-- GA Dynamic Fields -->
          <template v-else>
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">绑定行政科室 <span class="req">*</span></label>
                <input v-model="extraGA.officeDept" type="text" class="f-input" placeholder="如 研究生院培养办" required />
              </div>
              <div class="f-field">
                <label class="f-label">办公地点 <span class="req">*</span></label>
                <input v-model="extraGA.location" type="text" class="f-input" placeholder="如 行政楼 B306" required />
              </div>
            </div>
            <div class="grid-2 mt-12">
              <div class="f-field">
                <label class="f-label">值班时段 <span class="req">*</span></label>
                <input v-model="extraGA.dutyShift" type="text" class="f-input" placeholder="如 周二/周四 14:00-17:00" required />
              </div>
              <div class="f-field">
                <label class="f-label">专项经费说明</label>
                <input v-model="extraGA.fundNote" type="text" class="f-input" />
              </div>
            </div>
          </template>
        </div>

        <div class="grid-2">
          <div class="f-field">
            <label class="f-label">技能匹配标签（逗号分隔，用于学生端智能推荐）</label>
            <input v-model="skillsInput" type="text" class="f-input" placeholder="Python, 机器学习, 英语授课" />
          </div>
          <div class="f-field">
            <label class="f-label">申请截止日期</label>
            <input v-model="form.deadline" type="date" class="f-input" />
          </div>
        </div>

        <div class="f-field">
          <label class="f-label">岗位职责描述</label>
          <textarea v-model="form.description" class="f-textarea" rows="3" placeholder="简述核心工作内容、考核标准与能力要求..." />
        </div>

        <div class="flex items-center justify-between pt-12 border-t">
          <span class="text-xs text-muted">
            {{ validation.ok ? '预算与名额双管控校验已通过，可提交审批流' : '存在硬拦截项，请调整预算账号或招聘规模' }}
          </span>
          <button
            type="submit"
            class="f-btn f-btn-primary f-btn-lg"
            :disabled="!validation.ok || submitting"
          >
            <FluentIcon name="check" :size="16" />
            <span>提交岗位并启动审批流</span>
          </button>
        </div>
      </form>

      <!-- Right: Sticky Real-Time Budget & Quota Guard Card (Spec 2.4.2) -->
      <aside class="guard-aside">
        <div
          class="acrylic-card p-20 guard-card"
          :class="{ 'border-error': !validation.ok }"
        >
          <div class="flex items-center justify-between">
            <span class="fw-600 flex items-center gap-8">
              <FluentIcon name="wallet" :size="17" :class="validation.ok ? 'text-blue' : 'text-error'" />
              <span>经费与名额双管控测算</span>
            </span>
            <span class="f-pill" :class="validation.ok ? 'f-pill-success' : 'f-pill-error'">
              {{ validation.ok ? '测算通过' : '触发拦截' }}
            </span>
          </div>

          <div class="calc-formula mt-16">
            <div class="text-xs text-muted">预计总支出 = 人数 × 时薪 × 周工时 × 周数</div>
            <div class="text-xs text-muted tabular-nums mt-4">
              {{ form.quota }} 人 × ¥{{ form.hourlyRate }} × {{ form.weeklyHours }}h × {{ form.weeks }} 周
            </div>
            <div class="calc-total tabular-nums mt-6" :class="validation.budgetExceeded ? 'text-error' : 'text-blue'">
              ¥{{ validation.estimatedCost.toLocaleString() }}
            </div>
          </div>

          <!-- Budget Pool Comparison -->
          <div class="guard-block mt-16">
            <div class="flex justify-between text-xs">
              <span class="text-muted">所选经费池当前可用余额</span>
              <span class="fw-600 tabular-nums">¥{{ validation.availableBudget.toLocaleString() }}</span>
            </div>
            <div class="flex justify-between text-xs mt-6">
              <span class="text-muted">本次冻结后预计结余</span>
              <span
                class="fw-600 tabular-nums"
                :class="validation.remainingAfter < 0 ? 'text-error' : 'text-success'"
              >
                {{ validation.remainingAfter < 0 ? '-' : '' }}¥{{ Math.abs(validation.remainingAfter).toLocaleString() }}
              </span>
            </div>
            <div class="f-progress-track mt-8">
              <div
                class="f-progress-fill"
                :class="validation.budgetExceeded ? 'error' : projectedBudgetRate >= 85 ? 'caution' : ''"
                :style="{ width: Math.min(100, projectedBudgetRate) + '%' }"
              />
            </div>
          </div>

          <!-- Department Quota Check -->
          <div class="guard-block mt-12">
            <div class="flex justify-between text-xs">
              <span class="text-muted">{{ validation.quotaInfo.deptName }} 剩余名额</span>
              <span class="fw-600 tabular-nums" :class="validation.quotaExceeded ? 'text-error' : ''">
                {{ validation.quotaInfo.remaining }} / {{ validation.quotaInfo.limit }} 人
              </span>
            </div>
          </div>

          <!-- Hard-Block Error Alert -->
          <div v-if="!validation.ok" class="f-banner error mt-16">
            <FluentIcon name="x-circle" :size="17" />
            <div class="text-xs">
              <div v-for="(err, i) in validation.errors" :key="i" class="fw-600">{{ err }}</div>
            </div>
          </div>

          <!-- Dynamic Approval Workflow Preview -->
          <div class="wf-preview mt-16 pt-12 border-t">
            <div class="text-xs fw-600 mb-8">预计流转审批链（读取校级配置中心）</div>
            <div class="flex flex-col gap-6">
              <div v-for="(step, idx) in previewWorkflow" :key="step" class="wf-preview-item">
                <span class="step-num">{{ idx + 1 }}</span>
                <span class="text-xs">{{ stepName(step) }}</span>
              </div>
            </div>
          </div>

          <div class="mt-12 text-xs text-muted">
            提示：可将经费账号切换为「青年探索微额科研基金（余额紧张）」或调高人数，立即体验前端预算超支红框硬拦截！
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import {
  validateJobPublish,
  getBudgetAvailable,
  buildWorkflowStepsForJob
} from '@/services/ruleEngine'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
const router = useRouter()
const user = computed(() => store.currentUser)

const jobTypes = [
  { value: 'TA', title: '助教（TA）', desc: '绑定课程号、语言要求与教学周排班' },
  { value: 'RA', title: '助研（RA）', desc: '绑定科研项目、课题经费与里程碑' },
  { value: 'GA', title: '助管（GA）', desc: '绑定行政科室、值班时段与专项经费' }
]

const defaultDeptId = user.value?.deptId && user.value.deptId !== 'DEPT_OFFICE' ? user.value.deptId : 'DEPT_CS'

const form = reactive({
  type: 'TA',
  title: '《计算机体系结构（双语）》课程助教',
  deptId: defaultDeptId,
  budgetId: 'B_CS_TA',
  quota: 2,
  hourlyRate: 45,
  weeklyHours: 8,
  weeks: 16,
  allowInternational: true,
  deadline: '2026-12-28',
  description: '协助主讲教师组织双语实验课、批改作业并维护课程评测环境。'
})

const extraTA = reactive({
  courseCode: 'CS3066',
  courseName: '计算机体系结构（双语）',
  semester: '2026-2027 春季学期',
  minLangScore: 6.5,
  scheduleText: '周二 14:00-16:00 实验辅导；周四 10:00-12:00 答疑'
})

const extraRA = reactive({
  projectCode: 'NSFC-62572018',
  projectName: '面向异构集群的大模型低延迟推理优化',
  researchDirection: 'AI 系统与高性能计算',
  milestonesText: 'M1：完成异构算子基准测试报告\nM2：实现多卡通信重叠优化原型\nM3：完成实验验证与论文图表整理'
})

const extraGA = reactive({
  officeDept: '研究生院综合服务大厅',
  location: '行政楼 A204',
  dutyShift: '周二、周四 14:00-17:00',
  fundNote: '校级勤工助学专项经费保障'
})

const skillsInput = ref('Python, 计算机系统, 英语授课')
const submitting = ref(false)

const selectableBudgets = computed(() => {
  if (user.value?.role === 'SCHOOL_ADMIN') return store.db.budgets
  return store.db.budgets.filter((b) => b.deptId === form.deptId || b.sourceType === 'GA_FUND')
})

const validation = computed(() => validateJobPublish(store.db, form))

const projectedBudgetRate = computed(() => {
  const b = validation.value.budget
  if (!b || !b.totalAmount) return 100
  return Math.round(((b.usedAmount + b.frozenAmount + validation.value.estimatedCost) / b.totalAmount) * 100)
})

const previewWorkflow = computed(() => buildWorkflowStepsForJob(store.db, form.type, form.allowInternational))

function switchJobType(type) {
  form.type = type
  if (type === 'TA') {
    form.budgetId = selectableBudgets.value.find((b) => b.sourceType === 'TA_FUND')?.id || selectableBudgets.value[0]?.id
    form.hourlyRate = 45
    form.weeklyHours = 8
    form.allowInternational = true
  } else if (type === 'RA') {
    form.budgetId = selectableBudgets.value.find((b) => b.sourceType === 'RA_PROJECT')?.id || selectableBudgets.value[0]?.id
    form.hourlyRate = 48
    form.weeklyHours = 10
    form.allowInternational = false
  } else {
    form.budgetId = selectableBudgets.value.find((b) => b.sourceType === 'GA_FUND')?.id || selectableBudgets.value[0]?.id
    form.hourlyRate = 35
    form.weeklyHours = 6
    form.allowInternational = false
  }
}

function stepName(key) {
  const map = {
    MENTOR_SUBMIT: '导师/科室提交申请',
    DEPT_REVIEW: '院系名额与经费初审',
    INTL_REVIEW: '国际生办专项会签（面向国际生触发）',
    SCHOOL_REVIEW: '校级专项经费终审',
    PUBLISHED: '正式发布至学生招聘大厅'
  }
  return map[key] || key
}

async function handleSubmit() {
  if (!validation.value.ok) return
  submitting.value = true
  try {
    let extraFields = {}
    if (form.type === 'TA') {
      const langText = extraTA.minLangScore > 0 ? `IELTS ${extraTA.minLangScore} 及以上` : '无特殊语言限制'
      extraFields = {
        ...extraTA,
        languageRequirement: langText,
        scheduleSlots: [{ day: '每周固定', time: extraTA.scheduleText, room: '教务排定教室', task: '课程辅导与答疑' }]
      }
    } else if (form.type === 'RA') {
      const lines = extraRA.milestonesText
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
      extraFields = {
        projectCode: extraRA.projectCode,
        projectName: extraRA.projectName,
        researchDirection: extraRA.researchDirection,
        milestones: lines.map((title, idx) => ({
          id: `MS_NEW_${idx + 1}`,
          title,
          dueDate: '2027-01-15',
          done: false
        }))
      }
    } else {
      extraFields = { ...extraGA }
    }

    const skills = skillsInput.value
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean)

    const res = await store.mutate(() =>
      api.createJob(
        {
          ...form,
          skills,
          extraFields
        },
        user.value
      )
    )
    if (res.code === 200) {
      router.push('/admin/jobs')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.publish-layout {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

/* Single-column form strictly max-width 640px */
.publish-form {
  width: 100%;
  max-width: 640px;
  flex-shrink: 0;
}

.guard-aside {
  flex: 1;
  min-width: 300px;
  position: sticky;
  top: 24px;
}

.guard-card {
  border-width: 1px;
}

.guard-card.border-error {
  border: 1px solid var(--color-error);
  background: rgba(255, 255, 255, 0.88);
}

.type-selector-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.type-option {
  text-align: left;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px solid var(--color-border-light);
  background: var(--surface-subtle);
  cursor: pointer;
  transition: all var(--motion-fast);
}

.type-option:hover {
  background: #ffffff;
  border-color: var(--color-blue-border);
}

.type-option.active {
  background: #ffffff;
  border-color: var(--color-fluent-blue);
  box-shadow: 0 0 0 1px var(--color-fluent-blue), var(--shadow-xs);
}

.grid-2 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.dynamic-section {
  padding: 18px;
  border-radius: 10px;
  background: var(--surface-subtle);
}

.intl-toggle {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  background: var(--color-blue-subtle);
  border-radius: 8px;
  cursor: pointer;
}

.f-checkbox {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  accent-color: var(--color-fluent-blue);
}

.calc-formula {
  padding: 16px;
  border-radius: 10px;
  background: var(--surface-subtle);
}

.calc-total {
  font-size: 30px;
  font-weight: 600;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.guard-block {
  padding: 12px 14px;
  background: var(--surface-subtle);
  border-radius: 8px;
}

.wf-preview-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.step-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-blue-subtle);
  color: var(--color-fluent-blue);
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.border-t { border-top: 1px solid var(--color-border-light); }
.pt-12 { padding-top: 12px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mb-8 { margin-bottom: 8px; }

@media (max-width: 1080px) {
  .publish-layout {
    flex-direction: column;
  }
  .publish-form {
    max-width: 100%;
  }
  .guard-aside {
    width: 100%;
    position: static;
  }
}

@media (max-width: 600px) {
  .type-selector-grid,
  .grid-2,
  .grid-4 {
    grid-template-columns: 1fr;
  }
}
</style>
