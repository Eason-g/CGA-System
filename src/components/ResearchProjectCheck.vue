<template>
  <div class="rp-box mt-12">
    <div class="rp-head">
      <div class="text-xs text-muted">
        绑定的科研项目在建岗时由后端到科研系统（模拟）校验：项目存在、负责人为发岗导师（管理员代建要求同院系）、
        项目结束日不早于预计聘期结束日。
      </div>
      <button type="button" class="f-btn f-btn-sm shrink-0" :disabled="busy || !code" @click="verify">
        <FluentIcon name="search" :size="13" />
        <span>{{ busy ? '校验中…' : '校验项目' }}</span>
      </button>
    </div>

    <div v-if="error" class="rp-result error">{{ error }}</div>
    <div v-else-if="result" class="rp-result" :class="result.ok ? 'ok' : 'error'">
      <div v-if="result.project" class="fw-600 text-sm">
        {{ result.project.code }} · {{ result.project.name }}
        <span class="f-pill f-pill-neutral">{{ result.source }}</span>
      </div>
      <div v-if="result.project" class="text-xs text-muted mt-4 tabular-nums">
        负责人 {{ result.project.leaderName }} · 周期 {{ result.project.startDate }} 至 {{ result.project.endDate }}
        · 可用经费 ¥{{ Number(result.project.availableFunds || 0).toLocaleString() }}
        <template v-if="result.jobEndDate"> · 岗位预计聘期至 {{ result.jobEndDate }}</template>
      </div>
      <ul class="rp-checks">
        <li v-for="c in result.checks" :key="c.key" :class="c.passed ? 'pass' : 'fail'">
          <FluentIcon :name="c.passed ? 'check-circle' : 'x-circle'" :size="13" />
          <span>{{ c.label }}：{{ c.detail }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { integrationApi } from '@/services/integrationApi'
import FluentIcon from '@/components/FluentIcon.vue'

const props = defineProps({
  code: { type: String, default: '' },
  deptId: { type: String, default: '' },
  deadline: { type: String, default: '' },
  weeks: { type: [Number, String], default: null }
})

const busy = ref(false)
const result = ref(null)
const error = ref('')

// 表单变化后旧的校验结果不再可信；进行中的请求返回后也丢弃（按序号判断）
let seq = 0
watch(() => [props.code, props.deptId, props.deadline, props.weeks], () => {
  seq += 1
  result.value = null
  error.value = ''
})

async function verify() {
  const mine = ++seq
  busy.value = true
  error.value = ''
  result.value = null
  try {
    const res = await integrationApi.getResearchProject({
      code: props.code,
      deptId: props.deptId,
      deadline: props.deadline,
      weeks: props.weeks
    })
    if (mine !== seq) return
    if (res.code === 200 && res.data) result.value = res.data
    else error.value = res.message || '校验失败'
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.rp-box {
  border: 1px dashed var(--color-border, #d6dde8);
  border-radius: 8px;
  padding: 10px 12px;
}
.rp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.rp-head .text-xs { flex: 1; min-width: 200px; }
.shrink-0 { flex-shrink: 0; }
.rp-result { margin-top: 10px; font-size: 13px; }
.rp-result.error { color: var(--color-error, #b91c1c); }
.rp-checks { list-style: none; margin: 8px 0 0; padding: 0; display: flex; flex-direction: column; gap: 4px; }
.rp-checks li { display: flex; align-items: flex-start; gap: 6px; font-size: 12px; line-height: 1.6; }
.rp-checks li.pass { color: var(--color-success, #15803d); }
.rp-checks li.fail { color: var(--color-error, #b91c1c); }
.mt-4 { margin-top: 4px; }
.mt-12 { margin-top: 12px; }
</style>
