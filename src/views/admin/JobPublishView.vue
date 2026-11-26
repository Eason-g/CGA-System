<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">发布新岗位</h1>
      <router-link to="/admin/jobs" class="f-btn">返回列表</router-link>
    </header>

    <div class="publish-layout">
      <!-- Left: Single-column Form (max-width 640px per Fluent spec 2.4.2) -->
      <form class="acrylic-card p-24 publish-form flex-col gap-16" @submit.prevent="handleSubmit">
        <!-- 岗位模板：从模板创建 / 存为模板 -->
        <div class="template-bar">
          <div class="flex items-center gap-8 flex-wrap">
            <FluentIcon name="layers" :size="16" class="text-blue" />
            <span class="text-sm fw-600">从模板创建</span>
            <select v-model="selectedTemplateId" class="f-select template-select">
              <option value="">选择岗位模板…</option>
              <option v-for="t in templates" :key="t.id" :value="t.id">
                [{{ t.type }}] {{ t.name }}（{{ t.deptId ? store.getDeptShortName(t.deptId) : '全校通用' }}）
              </option>
            </select>
            <button type="button" class="f-btn f-btn-sm" :disabled="!selectedTemplate" @click="applyTemplate(selectedTemplate)">应用</button>
            <button
              v-if="selectedTemplate && canDeleteTemplate(selectedTemplate)"
              type="button"
              class="f-btn f-btn-danger-outline f-btn-sm"
              :disabled="templateBusy"
              @click="removeTemplate(selectedTemplate)"
            >
              删除
            </button>
            <button type="button" class="f-btn f-btn-sm ml-auto" @click="showSaveTemplate = !showSaveTemplate">
              <FluentIcon name="plus" :size="13" />
              <span>存为模板</span>
            </button>
          </div>
          <div v-if="showSaveTemplate" class="save-template-row">
            <input v-model="templateForm.name" type="text" class="f-input flex-1" maxlength="64" placeholder="模板名称，如 计算机学院双语课程助教" />
            <label v-if="user?.role === 'SCHOOL_ADMIN'" class="flex items-center gap-6 text-xs">
              <input v-model="templateForm.global" type="checkbox" class="f-checkbox" />
              <span>全校通用</span>
            </label>
            <span v-else class="text-xs text-muted">保存到本院系</span>
            <button type="button" class="f-btn f-btn-primary f-btn-sm" :disabled="!templateForm.name.trim() || templateBusy" @click="saveTemplate">保存</button>
          </div>
        </div>

        <div>
          <label class="f-label mb-8">1. 选择岗位类别（共用流程引擎，字段按配置中心动态渲染） <span class="req">*</span></label>
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
              <option v-for="b in selectableBudgets" :key="b.id" :value="b.id" :disabled="!isFundAllowed(schema, b)">
                {{ b.name }}（可用 ¥{{ getBudgetAvailable(b).toLocaleString() }}）{{ isFundAllowed(schema, b) ? '' : `· 不适用于 ${form.type} 岗位` }}
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

        <!-- Dynamic Differential Fields Block：按 configs.jobTypeSchemas 渲染 -->
        <div class="dynamic-section">
          <div class="text-xs fw-600 text-blue mb-12">
            2. {{ form.type }} 岗位差异化专属字段
          </div>

          <div class="dyn-grid">
            <div
              v-for="(field, idx) in schema.fields"
              :key="`${form.type}_${field.key}`"
              class="f-field"
              :class="{ 'span-2': isWideField(field, idx) }"
            >
              <label class="f-label">
                {{ field.label }} <span v-if="field.required" class="req">*</span>
              </label>
              <select v-if="field.type === 'select'" v-model="extra[field.key]" class="f-select">
                <option v-for="opt in field.options || []" :key="String(opt.value)" :value="opt.value">{{ opt.label }}</option>
              </select>
              <textarea
                v-else-if="field.type === 'textarea' || field.type === 'lines'"
                v-model="extra[field.key]"
                class="f-textarea"
                rows="3"
                :placeholder="field.placeholder || (field.type === 'lines' ? '每行一条' : '')"
              />
              <input
                v-else
                v-model="extra[field.key]"
                :type="field.type === 'number' ? 'number' : field.type === 'date' ? 'date' : 'text'"
                class="f-input"
                :placeholder="field.placeholder || ''"
              />
            </div>
          </div>
          <div v-if="form.type === 'RA'" class="text-xs text-muted mt-8">里程碑截止日期由系统按聘期自动均分生成。</div>
          <!-- P2 B.3：到科研系统（模拟）校验绑定的项目 -->
          <ResearchProjectCheck
            v-if="form.type === 'RA'"
            :code="extra.projectCode"
            :dept-id="form.deptId"
            :deadline="form.deadline"
            :weeks="form.weeks"
          />

          <label v-if="schema.intlToggle" class="intl-toggle mt-12">
            <input v-model="form.allowInternational" type="checkbox" class="f-checkbox" />
            <div>
              <div class="fw-600 text-sm">{{ schema.intlToggle.label }}</div>
              <div class="text-xs text-muted">{{ schema.intlToggle.hint }}</div>
            </div>
          </label>
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
            {{ canSubmit ? '预算与名额双管控校验已通过，可提交审批流' : '存在硬拦截项或必填字段未填写，请检查右侧提示' }}
          </span>
          <button
            type="submit"
            class="f-btn f-btn-primary f-btn-lg"
            :disabled="!canSubmit || submitting"
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
          :class="{ 'border-error': !validation.ok || !fundAllowed }"
        >
          <div class="flex items-center justify-between">
            <span class="fw-600 flex items-center gap-8">
              <FluentIcon name="wallet" :size="17" :class="validation.ok ? 'text-blue' : 'text-error'" />
              <span>经费与名额双管控测算</span>
            </span>
            <span class="f-pill" :class="validation.ok && fundAllowed ? 'f-pill-success' : 'f-pill-error'">
              {{ validation.ok && fundAllowed ? '测算通过' : '触发拦截' }}
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
            <div class="flex justify-between text-xs mt-6">
              <span class="text-muted">{{ form.type }} 岗位允许的经费来源</span>
              <span class="fw-600" :class="fundAllowed ? '' : 'text-error'">{{ allowedFundText }}</span>
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
          <div v-if="blockingErrors.length" class="f-banner error mt-16">
            <FluentIcon name="x-circle" :size="17" />
            <div class="text-xs">
              <div v-for="(err, i) in blockingErrors" :key="i" class="fw-600">{{ err }}</div>
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
import {
  JOB_TYPES,
  getJobTypeSchemas,
  initialExtraValues,
  buildExtraFields,
  validateExtraFields,
  isFundAllowed,
  fundSourceLabel
} from '@/services/jobSchema'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import ResearchProjectCheck from '@/components/ResearchProjectCheck.vue'

const store = useAppStore()
const router = useRouter()
const user = computed(() => store.currentUser)

// 岗位类型差异化字段配置（配置中心可编辑）
const schemas = computed(() => getJobTypeSchemas(store.db))
const jobTypes = computed(() =>
  JOB_TYPES.map((t) => ({ value: t, title: schemas.value[t]?.label || t, desc: schemas.value[t]?.desc || '' }))
)

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

const schema = computed(() => schemas.value[form.type] || { fields: [] })
// 当前类型的差异化字段取值（键为配置里的 key）
const extra = ref(initialExtraValues(schema.value))

const skillsInput = ref('Python, 计算机系统, 英语授课')
const submitting = ref(false)

const selectableBudgets = computed(() => {
  if (user.value?.role === 'SCHOOL_ADMIN') return store.db.budgets
  return store.db.budgets.filter((b) => b.deptId === form.deptId || b.sourceType === 'GA_FUND')
})

const validation = computed(() => validateJobPublish(store.db, form))
const selectedBudget = computed(() => store.getBudget(form.budgetId))
const fundAllowed = computed(() => isFundAllowed(schema.value, selectedBudget.value))
const allowedFundText = computed(() => (schema.value.allowedFundSources || []).map(fundSourceLabel).join('、') || '不限')
const extraErrors = computed(() => validateExtraFields(schema.value, extra.value))
const blockingErrors = computed(() => [
  ...(validation.value.ok ? [] : validation.value.errors),
  ...(fundAllowed.value ? [] : [`${form.type} 岗位只能使用${allowedFundText.value}`]),
  ...extraErrors.value
])
const canSubmit = computed(() => validation.value.ok && fundAllowed.value && extraErrors.value.length === 0)

const projectedBudgetRate = computed(() => {
  const b = validation.value.budget
  if (!b || !b.totalAmount) return 100
  return Math.round(((b.usedAmount + b.frozenAmount + validation.value.estimatedCost) / b.totalAmount) * 100)
})

const previewWorkflow = computed(() => buildWorkflowStepsForJob(store.db, form.type, form.allowInternational))

function pickBudgetFor(type) {
  const s = schemas.value[type]
  return selectableBudgets.value.find((b) => isFundAllowed(s, b))?.id || selectableBudgets.value[0]?.id
}

function switchJobType(type) {
  form.type = type
  const s = schemas.value[type] || {}
  const d = s.defaults || {}
  form.budgetId = pickBudgetFor(type)
  if (d.hourlyRate) form.hourlyRate = d.hourlyRate
  if (d.weeklyHours) form.weeklyHours = d.weeklyHours
  if (d.weeks) form.weeks = d.weeks
  form.allowInternational = s.intlToggle ? Boolean(d.allowInternational) : false
  extra.value = initialExtraValues(s)
}

// 多行字段、以及落单在最后一行的字段占满整行
function isWideField(field, idx) {
  if (field.type === 'textarea' || field.type === 'lines') return true
  const fields = schema.value.fields || []
  const narrowBefore = fields.slice(0, idx).filter((f) => f.type !== 'textarea' && f.type !== 'lines').length
  const isLastNarrow = !fields.slice(idx + 1).some((f) => f.type !== 'textarea' && f.type !== 'lines')
  return isLastNarrow && narrowBefore % 2 === 0
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

function parseSkills() {
  return skillsInput.value
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

// 提交给后端的 extraFields：字段名与配置 key 一致（原有字段名不变）。
// TA 的 languageRequirement、RA 的 milestones 由后端生成；TA 排班卡片沿用原来的 scheduleSlots 结构
function currentExtraFields() {
  const extraFields = buildExtraFields(schema.value, extra.value)
  if (form.type === 'TA' && extraFields.scheduleText) {
    extraFields.scheduleSlots = [{ day: '每周固定', time: extraFields.scheduleText, room: '教务排定教室', task: '课程辅导与答疑' }]
  }
  return extraFields
}

async function handleSubmit() {
  if (!canSubmit.value) return
  submitting.value = true
  try {
    const res = await store.mutate(() =>
      api.createJob(
        {
          ...form,
          skills: parseSkills(),
          extraFields: currentExtraFields()
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

// ---------------------------------------------------------------- 岗位模板

const templates = computed(() => store.db.jobTemplates || [])
const selectedTemplateId = ref('')
const selectedTemplate = computed(() => templates.value.find((t) => t.id === selectedTemplateId.value) || null)
const showSaveTemplate = ref(false)
const templateBusy = ref(false)
const templateForm = reactive({ name: '', global: false })

function canDeleteTemplate(t) {
  return user.value?.role === 'SCHOOL_ADMIN' || t.createdBy === user.value?.id
}

function applyTemplate(t) {
  if (!t) return
  const p = t.payload || {}
  switchJobType(t.type)
  for (const key of ['quota', 'hourlyRate', 'weeklyHours', 'weeks']) {
    if (p[key] !== undefined && p[key] !== null && p[key] !== '') form[key] = Number(p[key])
  }
  if (schema.value.intlToggle && p.allowInternational !== undefined) form.allowInternational = Boolean(p.allowInternational)
  if (typeof p.description === 'string') form.description = p.description
  if (Array.isArray(p.skills)) skillsInput.value = p.skills.join(', ')
  // 经费账号只在当前可选且符合岗位类型时沿用
  const budget = selectableBudgets.value.find((b) => b.id === p.budgetId)
  if (budget && isFundAllowed(schema.value, budget)) form.budgetId = budget.id
  const values = { ...extra.value }
  for (const f of schema.value.fields || []) {
    const v = p.extraFields?.[f.key]
    if (v === undefined || v === null) continue
    values[f.key] = f.type === 'lines' && Array.isArray(v) ? v.join('\n') : v
  }
  extra.value = values
  store.notify(`已套用模板「${t.name}」，请补充岗位名称等信息`, 'info')
}

async function saveTemplate() {
  templateBusy.value = true
  try {
    const { title, ...rest } = form
    void title
    const payload = { ...rest, skills: parseSkills(), extraFields: buildExtraFields(schema.value, extra.value) }
    delete payload.deadline
    const res = await store.mutate(() =>
      api.saveJobTemplate({ name: templateForm.name.trim(), type: form.type, global: templateForm.global, payload })
    )
    if (res.code === 200) {
      selectedTemplateId.value = res.data?.id || ''
      templateForm.name = ''
      templateForm.global = false
      showSaveTemplate.value = false
    }
  } finally {
    templateBusy.value = false
  }
}

async function removeTemplate(t) {
  if (!window.confirm(`确定删除模板「${t.name}」吗？`)) return
  templateBusy.value = true
  try {
    const res = await store.mutate(() => api.deleteJobTemplate({ templateId: t.id }))
    if (res.code === 200) selectedTemplateId.value = ''
  } finally {
    templateBusy.value = false
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

.template-bar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 10px;
  border: 1px dashed var(--color-blue-border);
  background: var(--color-blue-subtle);
}

.template-select {
  flex: 1;
  min-width: 180px;
  max-width: 300px;
}

.save-template-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.ml-auto { margin-left: auto; }
.flex-1 { flex: 1; min-width: 160px; }

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

.dyn-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.dyn-grid .span-2 {
  grid-column: span 2;
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
.mt-8 { margin-top: 8px; }
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
  .grid-4,
  .dyn-grid {
    grid-template-columns: 1fr;
  }
  .dyn-grid .span-2 {
    grid-column: auto;
  }
}
</style>
