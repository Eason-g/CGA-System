<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ $t('admin.publish.title') }}</h1>
      <router-link to="/admin/jobs" class="f-btn">{{ $t('admin.publish.backToList') }}</router-link>
    </header>

    <div class="publish-layout">
      <!-- Left: Single-column Form (max-width 640px per Fluent spec 2.4.2) -->
      <form class="acrylic-card p-24 publish-form flex-col gap-16" @submit.prevent="handleSubmit">
        <!-- 岗位模板：从模板创建 / 存为模板 -->
        <div class="template-bar">
          <div class="flex items-center gap-8 flex-wrap">
            <FluentIcon name="layers" :size="16" class="text-blue" />
            <span class="text-sm fw-600">{{ $t('admin.publish.fromTemplate') }}</span>
            <select v-model="selectedTemplateId" class="f-select template-select">
              <option value="">{{ $t('admin.publish.chooseTemplate') }}</option>
              <option v-for="tpl in templates" :key="tpl.id" :value="tpl.id">
                [{{ tpl.type }}] {{ tpl.name }} ({{ tpl.deptId ? deptShortName(store.db, tpl.deptId) : $t('admin.publish.schoolWide') }})
              </option>
            </select>
            <button type="button" class="f-btn f-btn-sm" :disabled="!selectedTemplate" @click="applyTemplate(selectedTemplate)">{{ $t('admin.publish.apply') }}</button>
            <button
              v-if="selectedTemplate && canDeleteTemplate(selectedTemplate)"
              type="button"
              class="f-btn f-btn-danger-outline f-btn-sm"
              :disabled="templateBusy"
              @click="removeTemplate(selectedTemplate)"
            >
              {{ $t('admin.common.delete') }}
            </button>
            <button type="button" class="f-btn f-btn-sm ml-auto" @click="showSaveTemplate = !showSaveTemplate">
              <FluentIcon name="plus" :size="13" />
              <span>{{ $t('admin.publish.saveAsTemplate') }}</span>
            </button>
          </div>
          <div v-if="showSaveTemplate" class="save-template-row">
            <input v-model="templateForm.name" type="text" class="f-input flex-1" maxlength="64" :placeholder="$t('admin.publish.templateNamePh')" />
            <label v-if="user?.role === 'SCHOOL_ADMIN'" class="flex items-center gap-6 text-xs">
              <input v-model="templateForm.global" type="checkbox" class="f-checkbox" />
              <span>{{ $t('admin.publish.schoolWide') }}</span>
            </label>
            <span v-else class="text-xs text-muted">{{ $t('admin.publish.saveToDept') }}</span>
            <button type="button" class="f-btn f-btn-primary f-btn-sm" :disabled="!templateForm.name.trim() || templateBusy" @click="saveTemplate">{{ $t('admin.common.save') }}</button>
          </div>
        </div>

        <div>
          <label class="f-label mb-8">{{ $t('admin.publish.step1') }} <span class="req">*</span></label>
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
          <label class="f-label">{{ $t('admin.publish.jobTitle') }} <span class="req">*</span></label>
          <input
            v-model="form.title"
            type="text"
            class="f-input"
            required
            :placeholder="$t('admin.publish.jobTitlePh')"
          />
        </div>

        <div class="grid-2">
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.dept') }} <span class="req">*</span></label>
            <select v-model="form.deptId" class="f-select" :disabled="user?.role !== 'SCHOOL_ADMIN'">
              <option v-for="d in store.db.departments" :key="d.id" :value="d.id">
                {{ deptName(store.db, d.id) }}
              </option>
            </select>
          </div>

          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.budget') }} <span class="req">*</span></label>
            <select v-model="form.budgetId" class="f-select">
              <option v-for="b in selectableBudgets" :key="b.id" :value="b.id" :disabled="!isFundAllowed(schema, b)">
                {{ $t('admin.publish.budgetOption', { name: b.name, n: getBudgetAvailable(b).toLocaleString() }) }}{{ isFundAllowed(schema, b) ? '' : $t('admin.publish.notForType', { type: form.type }) }}
              </option>
            </select>
          </div>
        </div>

        <div class="grid-4">
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.quota') }} <span class="req">*</span></label>
            <input v-model.number="form.quota" type="number" min="1" max="20" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.hourlyRate') }} <span class="req">*</span></label>
            <input v-model.number="form.hourlyRate" type="number" min="20" max="120" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.weeklyHours') }} <span class="req">*</span></label>
            <input v-model.number="form.weeklyHours" type="number" min="2" max="20" class="f-input tabular-nums" required />
          </div>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.weeks') }} <span class="req">*</span></label>
            <input v-model.number="form.weeks" type="number" min="1" max="36" class="f-input tabular-nums" required />
          </div>
        </div>

        <!-- Dynamic Differential Fields Block：按 configs.jobTypeSchemas 渲染 -->
        <div class="dynamic-section">
          <div class="text-xs fw-600 text-blue mb-12">
            {{ $t('admin.publish.step2', { type: form.type }) }}
          </div>

          <div class="dyn-grid">
            <div
              v-for="(field, idx) in schema.fields"
              :key="`${form.type}_${field.key}`"
              class="f-field"
              :class="{ 'span-2': isWideField(field, idx) }"
            >
              <label class="f-label">
                {{ fieldLabel(field) }} <span v-if="field.required" class="req">*</span>
              </label>
              <select v-if="field.type === 'select'" v-model="extra[field.key]" class="f-select">
                <option v-for="opt in field.options || []" :key="String(opt.value)" :value="opt.value">{{ optionLabel(field, opt) }}</option>
              </select>
              <textarea
                v-else-if="field.type === 'textarea' || field.type === 'lines'"
                v-model="extra[field.key]"
                class="f-textarea"
                rows="3"
                :placeholder="fieldPlaceholder(field) || (field.type === 'lines' ? $t('admin.publish.linesPh') : '')"
              />
              <input
                v-else
                v-model="extra[field.key]"
                :type="field.type === 'number' ? 'number' : field.type === 'date' ? 'date' : 'text'"
                class="f-input"
                :placeholder="fieldPlaceholder(field)"
              />
            </div>
          </div>
          <div v-if="form.type === 'RA'" class="text-xs text-muted mt-8">{{ $t('admin.publish.milestoneHint') }}</div>
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
              <div class="fw-600 text-sm">{{ schemaText(form.type, schema, 'intlLabel') }}</div>
              <div class="text-xs text-muted">{{ schemaText(form.type, schema, 'intlHint') }}</div>
            </div>
          </label>
        </div>

        <div class="grid-2">
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.skills') }}</label>
            <input v-model="skillsInput" type="text" class="f-input" :placeholder="$t('admin.publish.skillsPh')" />
          </div>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.publish.deadline') }}</label>
            <input v-model="form.deadline" type="date" class="f-input" />
          </div>
        </div>

        <div class="f-field">
          <label class="f-label">{{ $t('admin.publish.description') }}</label>
          <textarea v-model="form.description" class="f-textarea" rows="3" :placeholder="$t('admin.publish.descriptionPh')" />
        </div>

        <div class="flex items-center justify-between pt-12 border-t">
          <span class="text-xs text-muted">
            {{ canSubmit ? $t('admin.publish.canSubmit') : $t('admin.publish.cannotSubmit') }}
          </span>
          <button
            type="submit"
            class="f-btn f-btn-primary f-btn-lg"
            :disabled="!canSubmit || submitting"
          >
            <FluentIcon name="check" :size="16" />
            <span>{{ $t('admin.publish.submit') }}</span>
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
              <span>{{ $t('admin.publish.guardTitle') }}</span>
            </span>
            <span class="f-pill" :class="validation.ok && fundAllowed ? 'f-pill-success' : 'f-pill-error'">
              {{ validation.ok && fundAllowed ? $t('admin.publish.guardPass') : $t('admin.publish.guardBlock') }}
            </span>
          </div>

          <div class="calc-formula mt-16">
            <div class="text-xs text-muted">{{ $t('admin.publish.formula') }}</div>
            <div class="text-xs text-muted tabular-nums mt-4">
              {{ $t('admin.publish.formulaVal', { q: form.quota, r: form.hourlyRate, h: form.weeklyHours, w: form.weeks }) }}
            </div>
            <div class="calc-total tabular-nums mt-6" :class="validation.budgetExceeded ? 'text-error' : 'text-blue'">
              ¥{{ validation.estimatedCost.toLocaleString() }}
            </div>
          </div>

          <!-- Budget Pool Comparison -->
          <div class="guard-block mt-16">
            <div class="flex justify-between text-xs">
              <span class="text-muted">{{ $t('admin.publish.available') }}</span>
              <span class="fw-600 tabular-nums">¥{{ validation.availableBudget.toLocaleString() }}</span>
            </div>
            <div class="flex justify-between text-xs mt-6">
              <span class="text-muted">{{ $t('admin.publish.remainingAfter') }}</span>
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
              <span class="text-muted">{{ $t('admin.publish.allowedFunds', { type: form.type }) }}</span>
              <span class="fw-600" :class="fundAllowed ? '' : 'text-error'">{{ allowedFundText }}</span>
            </div>
          </div>

          <!-- Department Quota Check -->
          <div class="guard-block mt-12">
            <div class="flex justify-between text-xs">
              <span class="text-muted">{{ $t('admin.publish.deptQuota', { dept: deptShortName(store.db, form.deptId) }) }}</span>
              <span class="fw-600 tabular-nums" :class="validation.quotaExceeded ? 'text-error' : ''">
                {{ $t('admin.publish.quotaVal', { r: validation.quotaInfo.remaining, l: validation.quotaInfo.limit }) }}
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
            <div class="text-xs fw-600 mb-8">{{ $t('admin.publish.wfPreview') }}</div>
            <div class="flex flex-col gap-6">
              <div v-for="(step, idx) in previewWorkflow" :key="step" class="wf-preview-item">
                <span class="step-num">{{ idx + 1 }}</span>
                <span class="text-xs">{{ stepName(step) }}</span>
              </div>
            </div>
          </div>

          <div class="mt-12 text-xs text-muted">
            {{ $t('admin.publish.tip') }}
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
  buildWorkflowStepsForJob,
  defaultJobDeadline
} from '@/services/ruleEngine'
import {
  JOB_TYPES,
  getJobTypeSchemas,
  initialExtraValues,
  buildExtraFields,
  validateExtraFields,
  isFundAllowed,
  fundSourceLabel,
  fieldLabel,
  fieldPlaceholder,
  optionLabel,
  schemaText
} from '@/services/jobSchema'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import ResearchProjectCheck from '@/components/ResearchProjectCheck.vue'
import { useI18n } from '@/i18n'
import { deptName, deptShortName, localizedList } from './adminI18n'

const store = useAppStore()
const { t } = useI18n()
const router = useRouter()
const user = computed(() => store.currentUser)

// 岗位类型差异化字段配置（配置中心可编辑）
const schemas = computed(() => getJobTypeSchemas(store.db))
const jobTypes = computed(() =>
  JOB_TYPES.map((type) => ({ value: type, title: schemaText(type, schemas.value[type], 'label'), desc: schemaText(type, schemas.value[type], 'desc') }))
)

const defaultDeptId = user.value?.deptId && user.value.deptId !== 'DEPT_OFFICE' ? user.value.deptId : 'DEPT_CS'

const form = reactive({
  type: 'TA',
  // 演示默认值（属于用户可改的数据）：按进入页面时的语言填入
  title: t('admin.publish.defaults.title'),
  deptId: defaultDeptId,
  budgetId: 'B_CS_TA',
  quota: 2,
  hourlyRate: 45,
  weeklyHours: 8,
  weeks: 16,
  allowInternational: true,
  deadline: defaultJobDeadline(),
  description: t('admin.publish.defaults.description')
})

const schema = computed(() => schemas.value[form.type] || { fields: [] })
// 当前类型的差异化字段取值（键为配置里的 key）
const extra = ref(initialExtraValues(schema.value))

const skillsInput = ref(t('admin.publish.defaults.skills'))
const submitting = ref(false)

const selectableBudgets = computed(() => {
  if (user.value?.role === 'SCHOOL_ADMIN') return store.db.budgets
  return store.db.budgets.filter((b) => b.deptId === form.deptId || b.sourceType === 'GA_FUND')
})

const validation = computed(() => validateJobPublish(store.db, form))
const selectedBudget = computed(() => store.getBudget(form.budgetId))
const fundAllowed = computed(() => isFundAllowed(schema.value, selectedBudget.value))
const allowedFundText = computed(
  () => (schema.value.allowedFundSources || []).map(fundSourceLabel).join(t('admin.common.listSep')) || t('admin.common.unlimited')
)
const extraErrors = computed(() => validateExtraFields(schema.value, extra.value))
const blockingErrors = computed(() => [
  ...(validation.value.ok ? [] : localizedList(validation.value, 'errors')),
  ...(fundAllowed.value ? [] : [t('admin.publish.fundOnly', { type: form.type, funds: allowedFundText.value })]),
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
  const k = `admin.publish.stepsLong.${key}`
  const v = t(k)
  return v === k ? key : v
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
    extraFields.scheduleSlots = [{
      day: t('admin.publish.defaults.slotDay'),
      time: extraFields.scheduleText,
      room: t('admin.publish.defaults.slotRoom'),
      task: t('admin.publish.defaults.slotTask')
    }]
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
const selectedTemplate = computed(() => templates.value.find((tpl) => tpl.id === selectedTemplateId.value) || null)
const showSaveTemplate = ref(false)
const templateBusy = ref(false)
const templateForm = reactive({ name: '', global: false })

function canDeleteTemplate(tpl) {
  return user.value?.role === 'SCHOOL_ADMIN' || tpl.createdBy === user.value?.id
}

function applyTemplate(tpl) {
  if (!tpl) return
  const p = tpl.payload || {}
  switchJobType(tpl.type)
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
  store.notify(t('admin.publish.templateApplied', { name: tpl.name }), 'info')
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

async function removeTemplate(tpl) {
  if (!window.confirm(t('admin.publish.confirmDeleteTemplate', { name: tpl.name }))) return
  templateBusy.value = true
  try {
    const res = await store.mutate(() => api.deleteJobTemplate({ templateId: tpl.id }))
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
