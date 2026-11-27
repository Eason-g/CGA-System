<template>
  <div class="rp-box mt-12">
    <div class="rp-head">
      <div class="text-xs text-muted">
        {{ $t('admin.research.desc') }}
      </div>
      <button type="button" class="f-btn f-btn-sm shrink-0" :disabled="busy || !code" @click="verify">
        <FluentIcon name="search" :size="13" />
        <span>{{ busy ? $t('admin.research.verifying') : $t('admin.research.verify') }}</span>
      </button>
    </div>

    <div v-if="error" class="rp-result error">{{ error }}</div>
    <div v-else-if="result" class="rp-result" :class="result.ok ? 'ok' : 'error'">
      <div v-if="result.project" class="fw-600 text-sm">
        {{ result.project.code }} · {{ result.project.name }}
        <span class="f-pill f-pill-neutral">{{ result.source }}</span>
      </div>
      <div v-if="result.project" class="text-xs text-muted mt-4 tabular-nums">
        {{ $t('admin.research.meta', { leader: result.project.leaderName, start: result.project.startDate, end: result.project.endDate }) }}{{ $t('admin.research.funds', { n: Number(result.project.availableFunds || 0).toLocaleString() }) }}
        <template v-if="result.jobEndDate">{{ $t('admin.research.jobEnd', { d: result.jobEndDate }) }}</template>
      </div>
      <ul class="rp-checks">
        <li v-for="c in result.checks" :key="c.key" :class="c.passed ? 'pass' : 'fail'">
          <FluentIcon :name="c.passed ? 'check-circle' : 'x-circle'" :size="13" />
          <span>{{ localized(c, 'label') }}{{ $t('admin.common.colon') }}{{ localized(c, 'detail') }}</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { integrationApi } from '@/services/integrationApi'
import FluentIcon from '@/components/FluentIcon.vue'
import { useI18n } from '@/i18n'

const props = defineProps({
  code: { type: String, default: '' },
  deptId: { type: String, default: '' },
  deadline: { type: String, default: '' },
  weeks: { type: [Number, String], default: null }
})

const { t, localized } = useI18n()
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
    else error.value = localized(res, 'message') || t('admin.research.failed')
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
