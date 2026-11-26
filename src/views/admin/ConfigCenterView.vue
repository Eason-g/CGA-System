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

    <!-- Section 3: Global Compliance Rules -->
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
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
const wfType = ref('TA')
const evalType = ref('TA')
const newNodeKey = ref('SCHOOL_REVIEW')
const saving = ref(false)

const draftWorkflows = ref(JSON.parse(JSON.stringify(store.db.configs.workflows)))
const draftEvals = ref(JSON.parse(JSON.stringify(store.db.configs.evaluationTemplates)))
const draftRules = ref({ ...store.db.configs.globalRules })

const currentWeightSum = computed(() =>
  (draftEvals.value[evalType.value] || []).reduce((s, d) => s + Number(d.weight || 0), 0)
)

function reloadLocal() {
  draftWorkflows.value = JSON.parse(JSON.stringify(store.db.configs.workflows))
  draftEvals.value = JSON.parse(JSON.stringify(store.db.configs.evaluationTemplates))
  draftRules.value = { ...store.db.configs.globalRules }
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

async function handleSave() {
  saving.value = true
  try {
    await store.mutate(() => api.saveConfigs({ workflows: draftWorkflows.value, evaluationTemplates: draftEvals.value, globalRules: draftRules.value }, store.currentUser))
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
.flex-1 { flex: 1; }

@media (max-width: 800px) {
  .rules-grid { grid-template-columns: 1fr; }
}
</style>
