<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ $t('admin.config.title') }}</h1>
      <div class="flex gap-8">
        <button type="button" class="f-btn" @click="reloadLocal">{{ $t('admin.config.restore') }}</button>
        <button type="button" class="f-btn f-btn-primary" :disabled="saving" @click="handleSave">
          <FluentIcon name="check" :size="15" />
          <span>{{ $t('admin.config.saveApply') }}</span>
        </button>
      </div>
    </header>

    <!-- Section 1: Approval Workflows -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <h2 class="text-lg fw-600">{{ $t('admin.config.workflowTitle') }}</h2>
        <div class="f-segmented">
          <button v-for="t in ['TA', 'RA', 'GA']" :key="t" type="button" :class="{ active: wfType === t }" @click="wfType = t">
            {{ t }}
          </button>
        </div>
      </div>

      <div class="wf-editor-list">
        <div v-for="(node, idx) in draftWorkflows[wfType]" :key="`${wfType}_${node.key}_${idx}`" class="wf-node-card">
          <div class="flex items-center gap-12 flex-1">
            <span class="node-idx">{{ idx + 1 }}</span>
            <div class="flex items-center gap-8 flex-wrap">
              <span class="fw-600 text-sm">{{ stepLabel(node.key, node.label) }}</span>
              <span class="text-xs text-muted">{{ isEn ? roleLabel(node.role) : node.role }}</span>
              <span v-if="node.conditional === 'allowInternational'" class="f-pill f-pill-caution">{{ $t('admin.config.intlOnly') }}</span>
            </div>
          </div>

          <div class="flex items-center gap-6">
            <button v-if="idx > 1 && idx < draftWorkflows[wfType].length - 1" type="button" class="f-btn f-btn-sm" @click="moveNode(idx, -1)">{{ $t('admin.config.moveUp') }}</button>
            <button v-if="idx > 0 && idx < draftWorkflows[wfType].length - 2" type="button" class="f-btn f-btn-sm" @click="moveNode(idx, 1)">{{ $t('admin.config.moveDown') }}</button>
            <button v-if="node.key !== 'MENTOR_SUBMIT' && node.key !== 'PUBLISHED'" type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="removeNode(idx)">{{ $t('admin.config.remove') }}</button>
          </div>
        </div>
      </div>

      <div class="add-node-bar">
        <select v-model="newNodeKey" class="f-select" style="max-width: 240px;">
          <option value="DEPT_REVIEW">{{ $t('admin.config.nodeOptions.DEPT_REVIEW') }}</option>
          <option value="INTL_REVIEW">{{ $t('admin.config.nodeOptions.INTL_REVIEW') }}</option>
          <option value="SCHOOL_REVIEW">{{ $t('admin.config.nodeOptions.SCHOOL_REVIEW') }}</option>
        </select>
        <button type="button" class="f-btn" @click="addNode">
          <FluentIcon name="plus" :size="14" />
          <span>{{ $t('admin.config.insertNode') }}</span>
        </button>
      </div>
    </section>

    <!-- Section 2: Evaluation Weights -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <h2 class="text-lg fw-600">{{ $t('admin.config.evalTitle') }}</h2>
        <div class="f-segmented">
          <button v-for="t in ['TA', 'RA', 'GA']" :key="t" type="button" :class="{ active: evalType === t }" @click="evalType = t">
            {{ t }}
          </button>
        </div>
      </div>

      <div class="flex-col gap-8">
        <div v-for="(dim, idx) in draftEvals[evalType]" :key="`${evalType}_${dim.key}_${idx}`" class="dim-edit-row">
          <input v-model="dim.label" type="text" class="f-input flex-1" />
          <div class="flex items-center gap-6">
            <input v-model.number="dim.weight" type="number" min="0" max="100" class="f-input tabular-nums" style="width: 84px;" />
            <span class="text-sm fw-600">%</span>
          </div>
          <button v-if="draftEvals[evalType].length > 1" type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="draftEvals[evalType].splice(idx, 1)">{{ $t('admin.common.delete') }}</button>
        </div>
      </div>

      <div class="flex items-center justify-between">
        <button type="button" class="f-btn f-btn-sm" @click="addDimension">
          <FluentIcon name="plus" :size="14" />
          <span>{{ $t('admin.config.addDim') }}</span>
        </button>
        <span class="text-xs fw-600 tabular-nums" :class="currentWeightSum === 100 ? 'text-success' : 'text-error'">
          {{ $t('admin.config.weightSum', { n: currentWeightSum }) }} {{ currentWeightSum === 100 ? '✓' : $t('admin.config.mustBe100') }}
        </span>
      </div>
    </section>

    <!-- Section 3: Job Type Differential Fields（P1：发布页按此渲染，后端建岗按此校验） -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <div>
          <h2 class="text-lg fw-600">{{ $t('admin.config.fieldsTitle') }}</h2>
          <div class="text-xs text-muted mt-4">{{ $t('admin.config.fieldsDesc') }}</div>
        </div>
        <div class="f-segmented">
          <button v-for="t in ['TA', 'RA', 'GA']" :key="t" type="button" :class="{ active: fieldType === t }" @click="fieldType = t">
            {{ t }}
          </button>
        </div>
      </div>

      <div class="flex-col gap-8">
        <div v-for="(field, idx) in currentSchema.fields" :key="`${fieldType}_${idx}`" class="field-edit-card">
          <div class="field-edit-row">
            <input v-model="field.label" type="text" class="f-input flex-1" maxlength="32" :placeholder="$t('admin.config.fieldNamePh')" />
            <input
              v-model="field.key"
              type="text"
              class="f-input key-input"
              :disabled="!field._new"
              :placeholder="$t('admin.config.fieldKeyPh')"
              :title="$t('admin.config.fieldKeyTitle')"
            />
            <select v-model="field.type" class="f-select type-input">
              <option v-for="opt in FIELD_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <label class="flex items-center gap-6 text-xs nowrap">
              <input v-model="field.required" type="checkbox" class="f-checkbox" />
              <span>{{ $t('admin.config.required') }}</span>
            </label>
            <div class="flex items-center gap-4">
              <button type="button" class="f-btn f-btn-sm" :disabled="idx === 0" @click="moveField(idx, -1)">{{ $t('admin.config.moveUp') }}</button>
              <button type="button" class="f-btn f-btn-sm" :disabled="idx === currentSchema.fields.length - 1" @click="moveField(idx, 1)">{{ $t('admin.config.moveDown') }}</button>
              <button type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="currentSchema.fields.splice(idx, 1)">{{ $t('admin.common.delete') }}</button>
            </div>
          </div>
          <div class="field-edit-row">
            <input v-model="field.placeholder" type="text" class="f-input flex-1" :placeholder="$t('admin.config.placeholderPh')" />
            <textarea
              v-if="field.type === 'select'"
              :value="optionsText(field)"
              class="f-textarea flex-1"
              rows="2"
              :placeholder="$t('admin.config.optionsPh')"
              @change="setOptions(field, $event.target.value)"
            />
          </div>
        </div>
      </div>

      <div class="flex items-center justify-between flex-wrap gap-12">
        <button type="button" class="f-btn f-btn-sm" @click="addField">
          <FluentIcon name="plus" :size="14" />
          <span>{{ $t('admin.config.addField') }}</span>
        </button>
        <div class="flex items-center gap-12 flex-wrap text-xs">
          <span class="fw-600">{{ $t('admin.config.allowedFunds') }}</span>
          <label v-for="opt in FUND_SOURCE_OPTIONS" :key="opt.value" class="flex items-center gap-4">
            <input
              type="checkbox"
              class="f-checkbox"
              :checked="currentSchema.allowedFundSources.includes(opt.value)"
              @change="toggleFund(opt.value)"
            />
            <span>{{ opt.label }}</span>
          </label>
        </div>
      </div>

      <div class="rules-grid">
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.defaultRate') }}</label>
          <input v-model.number="currentSchema.defaults.hourlyRate" type="number" min="1" max="1000" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.defaultHours') }}</label>
          <input v-model.number="currentSchema.defaults.weeklyHours" type="number" min="1" max="40" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.defaultWeeks') }}</label>
          <input v-model.number="currentSchema.defaults.weeks" type="number" min="1" max="52" class="f-input tabular-nums" />
        </div>
      </div>
    </section>

    <!-- Section 4: Global Compliance Rules -->
    <section class="acrylic-card p-20">
      <h2 class="text-lg fw-600 mb-12">{{ $t('admin.config.rulesTitle') }}</h2>
      <div class="rules-grid">
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.maxHours') }}</label>
          <input v-model.number="draftRules.maxWeeklyHoursDefault" type="number" min="8" max="30" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.maxHoursIntl') }}</label>
          <input v-model.number="draftRules.maxWeeklyHoursIntl" type="number" min="8" max="20" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">{{ $t('admin.config.expiryDays') }}</label>
          <input v-model.number="draftRules.expiryWarningDays" type="number" min="5" max="60" class="f-input tabular-nums" />
        </div>
      </div>
    </section>

    <!-- Section 5: 智能推荐权重（P2 C.2，学生端岗位大厅“为你推荐”按此打分） -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div>
        <h2 class="text-lg fw-600">{{ $t('admin.config.recTitle') }}</h2>
        <div class="text-xs text-muted mt-4">
          {{ $t('admin.config.recDesc') }}
        </div>
      </div>
      <div class="weights-grid">
        <div v-for="f in RECOMMEND_FACTORS" :key="f.key" class="f-field">
          <label class="f-label">{{ factorLabel(f) }}</label>
          <input v-model.number="draftRules.recommendWeights[f.key]" type="number" min="0" step="1" class="f-input tabular-nums" />
          <span class="text-xs text-muted">{{ $t(`admin.config.factors.${f.key}.hint`) }} · {{ $t('admin.config.share', { n: weightShare(f.key) }) }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import { getJobTypeSchemas, FIELD_TYPE_OPTIONS, FUND_SOURCE_OPTIONS, DERIVED_FIELD_KEYS } from '@/services/jobSchema'
import FluentIcon from '@/components/FluentIcon.vue'
import { useI18n } from '@/i18n'
import { roleLabel, stepLabel } from './adminI18n'

const store = useAppStore()
const { t, isEn } = useI18n()
const wfType = ref('TA')
const evalType = ref('TA')
const fieldType = ref('TA')
const newNodeKey = ref('SCHOOL_REVIEW')
const saving = ref(false)

// 智能推荐因子（与后端 RecommendationService 一致；未配置时用默认权重）
// 因子名称、说明见词条 admin.config.factors.<key>
const RECOMMEND_FACTORS = [
  { key: 'skill', default: 40 },
  { key: 'hours', default: 25 },
  { key: 'language', default: 15 },
  { key: 'dept', default: 10 },
  { key: 'deadline', default: 10 }
]
const factorLabel = (f) => t(`admin.config.factors.${f.key}.label`)

function cloneRules() {
  const rules = JSON.parse(JSON.stringify(store.db.configs.globalRules || {}))
  const w = rules.recommendWeights && typeof rules.recommendWeights === 'object' ? rules.recommendWeights : {}
  rules.recommendWeights = Object.fromEntries(RECOMMEND_FACTORS.map((f) => [f.key, w[f.key] ?? f.default]))
  return rules
}

const draftWorkflows = ref(JSON.parse(JSON.stringify(store.db.configs.workflows)))
const draftEvals = ref(JSON.parse(JSON.stringify(store.db.configs.evaluationTemplates)))
const draftRules = ref(cloneRules())
const draftSchemas = ref(cloneSchemas())

const weightSum = computed(() =>
  RECOMMEND_FACTORS.reduce((s, f) => s + (Number(draftRules.value.recommendWeights[f.key]) || 0), 0)
)

function weightShare(key) {
  const v = Number(draftRules.value.recommendWeights[key]) || 0
  return weightSum.value > 0 ? Math.round((v / weightSum.value) * 1000) / 10 : 0
}

// 推荐权重检查（后端会再校验一次）：非负数、不能全为 0
function checkWeights() {
  for (const f of RECOMMEND_FACTORS) {
    const raw = draftRules.value.recommendWeights[f.key]
    if (raw === '' || raw === null || raw === undefined || !Number.isFinite(Number(raw)) || Number(raw) < 0) {
      return t('admin.config.errors.weightInvalid', { f: factorLabel(f) })
    }
  }
  if (weightSum.value <= 0) return t('admin.config.errors.weightZero')
  return ''
}

const currentWeightSum = computed(() =>
  (draftEvals.value[evalType.value] || []).reduce((s, d) => s + Number(d.weight || 0), 0)
)

const currentSchema = computed(() => draftSchemas.value[fieldType.value])

// 岗位字段配置的编辑副本：补齐 defaults / allowedFundSources，避免模板里访问 undefined
function cloneSchemas() {
  const copy = JSON.parse(JSON.stringify(getJobTypeSchemas(store.db)))
  for (const t of Object.keys(copy)) {
    copy[t].fields = copy[t].fields || []
    copy[t].allowedFundSources = copy[t].allowedFundSources || []
    copy[t].defaults = copy[t].defaults || {}
  }
  return copy
}

function reloadLocal() {
  draftWorkflows.value = JSON.parse(JSON.stringify(store.db.configs.workflows))
  draftEvals.value = JSON.parse(JSON.stringify(store.db.configs.evaluationTemplates))
  draftRules.value = cloneRules()
  draftSchemas.value = cloneSchemas()
  store.notify(t('admin.config.restored'), 'info')
}

function moveNode(idx, dir) {
  const list = draftWorkflows.value[wfType.value]
  const target = idx + dir
  if (target <= 0 || target >= list.length - 1) return
  ;[list[idx], list[target]] = [list[target], list[idx]]
}

function removeNode(idx) { draftWorkflows.value[wfType.value].splice(idx, 1) }

function addNode() {
  const catalog = {
    // 节点 label 是写入配置的中文数据（与种子配置一致），界面按节点 key 翻译显示
    DEPT_REVIEW: { key: 'DEPT_REVIEW', label: '院系名额与经费初审', role: 'DEPT_ADMIN' }, // i18n-ignore
    INTL_REVIEW: { key: 'INTL_REVIEW', label: '国际生办专项会签', role: 'INTL_OFFICER', conditional: 'allowInternational' }, // i18n-ignore
    SCHOOL_REVIEW: { key: 'SCHOOL_REVIEW', label: '校级专项经费终审', role: 'SCHOOL_ADMIN' } // i18n-ignore
  }
  const list = draftWorkflows.value[wfType.value]
  if (list.some((n) => n.key === newNodeKey.value)) {
    store.notify(t('admin.config.nodeExists'), 'caution')
    return
  }
  list.splice(list.length - 1, 0, { ...catalog[newNodeKey.value] })
}

function addDimension() {
  draftEvals.value[evalType.value].push({ key: 'custom_' + Date.now().toString().slice(-3), label: t('admin.config.newDim'), weight: 0 })
}

// ---------------------------------------------------------------- 岗位差异化字段

function moveField(idx, dir) {
  const list = currentSchema.value.fields
  const target = idx + dir
  if (target < 0 || target >= list.length) return
  ;[list[idx], list[target]] = [list[target], list[idx]]
}

function addField() {
  const list = currentSchema.value.fields
  let n = list.length + 1
  while (list.some((f) => f.key === `custom${n}`)) n += 1
  list.push({ key: `custom${n}`, label: t('admin.config.newField'), type: 'text', required: false, placeholder: '', _new: true })
}

const LINE_BREAK = /\r?\n/

function optionsText(field) {
  return (field.options || []).map((o) => `${o.value}|${o.label}`).join('\n')
}

// 选项按“取值|名称”逐行解析；取值是数字时存为数字（与原有的语言分数下拉一致）
function setOptions(field, text) {
  field.options = String(text || '')
    .split(LINE_BREAK)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [rawValue, ...rest] = line.split('|')
      const value = rawValue.trim()
      const label = rest.join('|').trim() || value
      return { value: value !== '' && !Number.isNaN(Number(value)) ? Number(value) : value, label }
    })
}

function toggleFund(value) {
  const list = currentSchema.value.allowedFundSources
  const i = list.indexOf(value)
  if (i >= 0) list.splice(i, 1)
  else list.push(value)
}

// 保存前的前端检查（后端会再校验一次），返回错误文案；没有问题返回空字符串
function checkSchemas() {
  for (const [type, s] of Object.entries(draftSchemas.value)) {
    const keys = new Set()
    for (const f of s.fields) {
      if (!/^[A-Za-z][A-Za-z0-9_]{0,31}$/.test(f.key || '')) return t('admin.config.errors.keyFormat', { type, key: f.key })
      if (DERIVED_FIELD_KEYS.includes(f.key)) return t('admin.config.errors.derived', { key: f.key })
      if (keys.has(f.key)) return t('admin.config.errors.duplicate', { type, key: f.key })
      keys.add(f.key)
      if (!String(f.label || '').trim()) return t('admin.config.errors.unnamed', { type })
      if (f.type === 'select' && !(f.options || []).length) return t('admin.config.errors.noOptions', { type, label: f.label })
    }
    if (!s.allowedFundSources.length) return t('admin.config.errors.noFund', { type })
  }
  return ''
}

// 提交前去掉编辑用的临时标记
function schemasPayload() {
  const copy = JSON.parse(JSON.stringify(draftSchemas.value))
  for (const s of Object.values(copy)) s.fields.forEach((f) => delete f._new)
  return copy
}

async function handleSave() {
  const schemaError = checkSchemas()
  if (schemaError) {
    store.notify(schemaError, 'error')
    return
  }
  const weightError = checkWeights()
  if (weightError) {
    store.notify(weightError, 'error')
    return
  }
  saving.value = true
  try {
    const res = await store.mutate(() => api.saveConfigs({
      workflows: draftWorkflows.value,
      evaluationTemplates: draftEvals.value,
      globalRules: draftRules.value,
      jobTypeSchemas: schemasPayload()
    }, store.currentUser))
    if (res.code === 200) {
      draftSchemas.value = cloneSchemas()
      draftRules.value = cloneRules()
    }
  } finally { saving.value = false }
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.wf-editor-list { display: flex; flex-direction: column; gap: 8px; }
.wf-node-card { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 14px; background: rgba(255, 255, 255, 0.78); border: 1px solid var(--color-border-light); border-radius: 4px; }
.node-idx { width: 22px; height: 22px; border-radius: 50%; background: var(--color-fluent-blue); color: #ffffff; font-size: 11px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; }
.add-node-bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.dim-edit-row { display: flex; align-items: center; gap: 10px; }
.rules-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.weights-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(160px, 1fr)); gap: 16px; }
.field-edit-card { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; background: rgba(255, 255, 255, 0.78); border: 1px solid var(--color-border-light); border-radius: 4px; }
.field-edit-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.key-input { width: 140px; }
.type-input { width: 170px; }
.nowrap { white-space: nowrap; }
.f-checkbox { width: 15px; height: 15px; accent-color: var(--color-fluent-blue); }
.flex-1 { flex: 1; min-width: 160px; }
.mt-4 { margin-top: 4px; }

@media (max-width: 800px) {
  .rules-grid { grid-template-columns: 1fr; }
  .dim-edit-row { flex-wrap: wrap; }
}
</style>
