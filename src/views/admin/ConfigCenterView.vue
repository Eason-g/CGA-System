<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">可配置化中心</h1>
      <div class="flex gap-8">
        <button type="button" class="f-btn" @click="reloadLocal">还原</button>
        <button type="button" class="f-btn f-btn-primary" :disabled="saving" @click="handleSave">
          <FluentIcon name="check" :size="15" />
          <span>保存并生效</span>
        </button>
      </div>
    </header>

    <!-- Section 1: Approval Workflows -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <h2 class="text-lg fw-600">审批流节点配置</h2>
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
              <span class="fw-600 text-sm">{{ node.label }}</span>
              <span class="text-xs text-muted">{{ node.role }}</span>
              <span v-if="node.conditional === 'allowInternational'" class="f-pill f-pill-caution">仅国际生岗触发</span>
            </div>
          </div>

          <div class="flex items-center gap-6">
            <button v-if="idx > 1 && idx < draftWorkflows[wfType].length - 1" type="button" class="f-btn f-btn-sm" @click="moveNode(idx, -1)">上移</button>
            <button v-if="idx > 0 && idx < draftWorkflows[wfType].length - 2" type="button" class="f-btn f-btn-sm" @click="moveNode(idx, 1)">下移</button>
            <button v-if="node.key !== 'MENTOR_SUBMIT' && node.key !== 'PUBLISHED'" type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="removeNode(idx)">移除</button>
          </div>
        </div>
      </div>

      <div class="add-node-bar">
        <select v-model="newNodeKey" class="f-select" style="max-width: 240px;">
          <option value="DEPT_REVIEW">院系初审</option>
          <option value="INTL_REVIEW">国际生办会签</option>
          <option value="SCHOOL_REVIEW">校级终审</option>
        </select>
        <button type="button" class="f-btn" @click="addNode">
          <FluentIcon name="plus" :size="14" />
          <span>插入节点</span>
        </button>
      </div>
    </section>

    <!-- Section 2: Evaluation Weights -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <h2 class="text-lg fw-600">考核维度权重</h2>
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
          <button v-if="draftEvals[evalType].length > 1" type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="draftEvals[evalType].splice(idx, 1)">删除</button>
        </div>
      </div>

      <div class="flex items-center justify-between">
        <button type="button" class="f-btn f-btn-sm" @click="addDimension">
          <FluentIcon name="plus" :size="14" />
          <span>新增维度</span>
        </button>
        <span class="text-xs fw-600 tabular-nums" :class="currentWeightSum === 100 ? 'text-success' : 'text-error'">
          合计 {{ currentWeightSum }}% {{ currentWeightSum === 100 ? '✓' : '（须为 100%）' }}
        </span>
      </div>
    </section>

    <!-- Section 3: Job Type Differential Fields（P1：发布页按此渲染，后端建岗按此校验） -->
    <section class="acrylic-card p-20 flex-col gap-14">
      <div class="flex items-center justify-between flex-wrap gap-12">
        <div>
          <h2 class="text-lg fw-600">岗位差异化字段</h2>
          <div class="text-xs text-muted mt-4">发布页按此渲染表单；后端建岗时校验必填项、下拉选项与经费来源。语言要求文案、里程碑截止日期由系统自动生成。</div>
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
            <input v-model="field.label" type="text" class="f-input flex-1" maxlength="32" placeholder="字段名称" />
            <input
              v-model="field.key"
              type="text"
              class="f-input key-input"
              :disabled="!field._new"
              placeholder="字段标识"
              title="字段标识即提交到 extraFields 的字段名；已有字段不可修改"
            />
            <select v-model="field.type" class="f-select type-input">
              <option v-for="opt in FIELD_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
            </select>
            <label class="flex items-center gap-6 text-xs nowrap">
              <input v-model="field.required" type="checkbox" class="f-checkbox" />
              <span>必填</span>
            </label>
            <div class="flex items-center gap-4">
              <button type="button" class="f-btn f-btn-sm" :disabled="idx === 0" @click="moveField(idx, -1)">上移</button>
              <button type="button" class="f-btn f-btn-sm" :disabled="idx === currentSchema.fields.length - 1" @click="moveField(idx, 1)">下移</button>
              <button type="button" class="f-btn f-btn-danger-outline f-btn-sm" @click="currentSchema.fields.splice(idx, 1)">删除</button>
            </div>
          </div>
          <div class="field-edit-row">
            <input v-model="field.placeholder" type="text" class="f-input flex-1" placeholder="输入提示（选填）" />
            <textarea
              v-if="field.type === 'select'"
              :value="optionsText(field)"
              class="f-textarea flex-1"
              rows="2"
              placeholder="每行一个选项：取值|显示名称，如 6.5|IELTS 6.5"
              @change="setOptions(field, $event.target.value)"
            />
          </div>
        </div>
      </div>

      <div class="flex items-center justify-between flex-wrap gap-12">
        <button type="button" class="f-btn f-btn-sm" @click="addField">
          <FluentIcon name="plus" :size="14" />
          <span>新增自定义字段</span>
        </button>
        <div class="flex items-center gap-12 flex-wrap text-xs">
          <span class="fw-600">允许的经费来源：</span>
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
          <label class="f-label">默认时薪（元/h）</label>
          <input v-model.number="currentSchema.defaults.hourlyRate" type="number" min="1" max="1000" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">默认周工时（h）</label>
          <input v-model.number="currentSchema.defaults.weeklyHours" type="number" min="1" max="40" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">默认聘期周数</label>
          <input v-model.number="currentSchema.defaults.weeks" type="number" min="1" max="52" class="f-input tabular-nums" />
        </div>
      </div>
    </section>

    <!-- Section 4: Global Compliance Rules -->
    <section class="acrylic-card p-20">
      <h2 class="text-lg fw-600 mb-12">全校红线阈值</h2>
      <div class="rules-grid">
        <div class="f-field">
          <label class="f-label">周工时上限（h）</label>
          <input v-model.number="draftRules.maxWeeklyHoursDefault" type="number" min="8" max="30" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">国际生工时上限（h）</label>
          <input v-model.number="draftRules.maxWeeklyHoursIntl" type="number" min="8" max="20" class="f-input tabular-nums" />
        </div>
        <div class="f-field">
          <label class="f-label">临期预警天数（天）</label>
          <input v-model.number="draftRules.expiryWarningDays" type="number" min="5" max="60" class="f-input tabular-nums" />
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

const store = useAppStore()
const wfType = ref('TA')
const evalType = ref('TA')
const fieldType = ref('TA')
const newNodeKey = ref('SCHOOL_REVIEW')
const saving = ref(false)

const draftWorkflows = ref(JSON.parse(JSON.stringify(store.db.configs.workflows)))
const draftEvals = ref(JSON.parse(JSON.stringify(store.db.configs.evaluationTemplates)))
const draftRules = ref({ ...store.db.configs.globalRules })
const draftSchemas = ref(cloneSchemas())

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
  draftRules.value = { ...store.db.configs.globalRules }
  draftSchemas.value = cloneSchemas()
  store.notify('已还原当前配置', 'info')
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
    DEPT_REVIEW: { key: 'DEPT_REVIEW', label: '院系名额与经费初审', role: 'DEPT_ADMIN' },
    INTL_REVIEW: { key: 'INTL_REVIEW', label: '国际生办专项会签', role: 'INTL_OFFICER', conditional: 'allowInternational' },
    SCHOOL_REVIEW: { key: 'SCHOOL_REVIEW', label: '校级专项经费终审', role: 'SCHOOL_ADMIN' }
  }
  const list = draftWorkflows.value[wfType.value]
  if (list.some((n) => n.key === newNodeKey.value)) {
    store.notify('该审批节点已存在', 'caution')
    return
  }
  list.splice(list.length - 1, 0, { ...catalog[newNodeKey.value] })
}

function addDimension() {
  draftEvals.value[evalType.value].push({ key: 'custom_' + Date.now().toString().slice(-3), label: '新增评价维度', weight: 0 })
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
  list.push({ key: `custom${n}`, label: '新增字段', type: 'text', required: false, placeholder: '', _new: true })
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
      if (!/^[A-Za-z][A-Za-z0-9_]{0,31}$/.test(f.key || '')) return `${type} 字段标识「${f.key}」须以字母开头，只含字母、数字、下划线`
      if (DERIVED_FIELD_KEYS.includes(f.key)) return `「${f.key}」由系统自动生成，不能作为表单字段`
      if (keys.has(f.key)) return `${type} 字段标识「${f.key}」重复`
      keys.add(f.key)
      if (!String(f.label || '').trim()) return `${type} 存在未命名的字段`
      if (f.type === 'select' && !(f.options || []).length) return `${type} 下拉字段「${f.label}」至少需要一个选项`
    }
    if (!s.allowedFundSources.length) return `${type} 岗位至少允许一种经费来源`
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
  saving.value = true
  try {
    const res = await store.mutate(() => api.saveConfigs({
      workflows: draftWorkflows.value,
      evaluationTemplates: draftEvals.value,
      globalRules: draftRules.value,
      jobTypeSchemas: schemasPayload()
    }, store.currentUser))
    if (res.code === 200) draftSchemas.value = cloneSchemas()
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
}
</style>
