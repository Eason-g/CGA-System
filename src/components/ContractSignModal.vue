<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal wide" role="dialog" aria-modal="true" :aria-label="t('sign.dialogLabel')">
      <header class="f-modal-header">
        <div>
          <div class="text-xs text-muted">{{ t('sign.contractNo', { id: contract.id }) }}</div>
          <h2 class="text-sub mt-4">{{ t('sign.title') }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" :aria-label="t('common.close')">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body flex-col gap-16">
        <!-- Contract Document Box -->
        <div class="contract-doc">
          <div class="doc-title">{{ t('sign.docTitle') }}</div>
          <div class="doc-grid">
            <div><span class="text-muted">{{ t('sign.partyA') }}</span><strong>{{ store.getDeptName(contract.deptId) }}</strong></div>
            <div><span class="text-muted">{{ t('sign.mentor') }}</span><strong>{{ mentor?.name || '-' }}</strong></div>
            <div><span class="text-muted">{{ t('sign.partyB') }}</span><strong>{{ t('sign.studentWithNo', { name: student?.name, no: student?.studentNo }) }}</strong></div>
            <div><span class="text-muted">{{ t('sign.position') }}</span><strong>[{{ job?.type }}] {{ job?.title }}</strong></div>
            <div><span class="text-muted">{{ t('sign.period') }}</span><strong class="tabular-nums">{{ t('sign.periodValue', { start: contract.startDate, end: contract.endDate }) }}</strong></div>
            <div><span class="text-muted">{{ t('sign.hoursPay') }}</span><strong class="tabular-nums">{{ t('sign.hoursPayValue', { h: contract.weeklyHours, rate: contract.hourlyRate }) }}</strong></div>
          </div>

          <ol class="doc-clauses">
            <li>{{ t('sign.clause1') }}</li>
            <li>{{ t('sign.clause2', { max: maxHours }) }}</li>
            <li>{{ t('sign.clause3') }}</li>
            <li>{{ t('sign.clause4') }}</li>
          </ol>
        </div>

        <!-- Mandatory International Student Compliance Declaration -->
        <div v-if="student?.isInternational" class="intl-declaration">
          <label class="checkbox-row">
            <input v-model="complianceAgreed" type="checkbox" class="f-checkbox" />
            <div>
              <div class="fw-600 text-sm">
                {{ t('sign.intlTitle') }}
              </div>
              <p class="text-xs text-muted mt-4">
                {{ t('sign.intlText', { visa: student.visaType, expiry: student.visaExpiry, max: maxHours }) }}
              </p>
            </div>
          </label>
        </div>

        <!-- Handwritten Canvas Signature Pad -->
        <div class="sign-section">
          <div class="flex items-center justify-between mb-8">
            <label class="f-label">
              <FluentIcon name="pen" :size="15" />
              <span>{{ t('sign.padLabel') }}</span>
              <span class="req">*</span>
            </label>
            <button type="button" class="f-btn f-btn-sm" @click="clearCanvas">{{ t('sign.clear') }}</button>
          </div>

          <div class="canvas-wrap">
            <canvas
              ref="canvasRef"
              width="680"
              height="160"
              class="sign-canvas"
              @pointerdown="startDraw"
              @pointermove="draw"
              @pointerup="endDraw"
              @pointercancel="endDraw"
              @lostpointercapture="endDraw"
            />
            <span v-if="!hasSigned" class="canvas-placeholder">{{ t('sign.padPlaceholder') }}</span>
          </div>
        </div>
      </div>

      <footer class="f-modal-footer">
        <button type="button" class="f-btn" @click="$emit('close')">{{ t('sign.later') }}</button>
        <button
          type="button"
          class="f-btn f-btn-primary"
          :disabled="!canSubmit || submitting"
          @click="submitSign"
        >
          {{ t('sign.submit') }}
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import { getMaxWeeklyHours } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  contract: { type: Object, required: true }
})

const emit = defineEmits(['close', 'signed'])
const store = useAppStore()
const { t } = useI18n()

const student = computed(() => store.getUser(props.contract.studentId))
const maxHours = computed(() => getMaxWeeklyHours(store.db, student.value))
const job = computed(() => store.getJob(props.contract.jobId))
const mentor = computed(() => (job.value ? store.getUser(job.value.mentorId) : null))

const canvasRef = ref(null)
const isDrawing = ref(false)
const hasSigned = ref(false)
const complianceAgreed = ref(false)
const submitting = ref(false)

const canSubmit = computed(() => {
  if (!hasSigned.value) return false
  if (student.value?.isInternational && !complianceAgreed.value) return false
  return true
})

let ctx = null

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  // 画布像素尺寸按实际显示尺寸 × 设备像素比设置，手机窄屏下笔迹不被横向拉伸
  const rect = canvas.getBoundingClientRect()
  const dpr = Math.min(window.devicePixelRatio || 1, 3)
  if (rect.width > 0 && rect.height > 0) {
    canvas.width = Math.round(rect.width * dpr)
    canvas.height = Math.round(rect.height * dpr)
  }
  ctx = canvas.getContext('2d')
  ctx.strokeStyle = '#201f1e'
  ctx.lineWidth = 2.4 * (canvas.width / (rect.width || canvas.width))
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
})

function getPos(e) {
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  return {
    x: (e.clientX - rect.left) * scaleX,
    y: (e.clientY - rect.top) * scaleY
  }
}

// pointer 事件统一处理鼠标 / 触摸 / 手写笔；canvas 设了 touch-action: none，触摸时不滚动页面
function startDraw(e) {
  if (!ctx) return
  if (e.pointerType === 'mouse' && e.button !== 0) return
  e.preventDefault()
  try {
    canvasRef.value?.setPointerCapture?.(e.pointerId)
  } catch {
    // 个别环境（如合成事件）不支持捕获，忽略即可
  }
  isDrawing.value = true
  const { x, y } = getPos(e)
  ctx.beginPath()
  ctx.moveTo(x, y)
}

function draw(e) {
  if (!isDrawing.value || !ctx) return
  e.preventDefault()
  const { x, y } = getPos(e)
  ctx.lineTo(x, y)
  ctx.stroke()
  hasSigned.value = true
}

function endDraw(e) {
  if (!isDrawing.value) return
  isDrawing.value = false
  if (e?.pointerId !== undefined && canvasRef.value?.hasPointerCapture?.(e.pointerId)) {
    canvasRef.value.releasePointerCapture(e.pointerId)
  }
  ctx?.closePath()
}

function clearCanvas() {
  if (!ctx || !canvasRef.value) return
  ctx.clearRect(0, 0, canvasRef.value.width, canvasRef.value.height)
  hasSigned.value = false
}

async function submitSign() {
  if (!canvasRef.value || !canSubmit.value) return
  submitting.value = true
  try {
    const dataUrl = canvasRef.value.toDataURL('image/png')
    const res = await store.mutate(() =>
      api.signContract(
        {
          contractId: props.contract.id,
          signatureDataUrl: dataUrl,
          complianceAgreed: student.value?.isInternational ? complianceAgreed.value : true
        },
        student.value
      )
    )
    if (res.code === 200) {
      emit('signed', res.data)
      emit('close')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.contract-doc {
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
  padding: 20px;
  background: #faf9f8;
}

.doc-title {
  text-align: center;
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--color-border-light);
}

.doc-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  font-size: var(--text-label);
  margin-bottom: 14px;
}

.doc-clauses {
  margin: 0;
  padding-left: 18px;
  font-size: var(--text-label);
  color: var(--color-muted);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.intl-declaration {
  padding: 14px;
  border: 1px solid var(--color-caution);
  background: var(--color-caution-bg);
  border-radius: 4px;
}

.checkbox-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  cursor: pointer;
}

.f-checkbox {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  accent-color: var(--color-fluent-blue);
  cursor: pointer;
}

.canvas-wrap {
  position: relative;
  border: 1px dashed #8a8886;
  border-radius: 4px;
  background: #ffffff;
  overflow: hidden;
}

.sign-canvas {
  display: block;
  width: 100%;
  height: 150px;
  cursor: crosshair;
  touch-action: none;
}

.canvas-placeholder {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  color: #a19f9d;
  font-size: var(--text-body);
}

.mb-8 { margin-bottom: 8px; }
.mt-4 { margin-top: 4px; }

@media (max-width: 640px) {
  .doc-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .contract-doc { padding: 14px; }
  .doc-title { font-size: 16px; }
  .doc-grid, .doc-clauses { font-size: 14px; }
  .doc-grid > div { overflow-wrap: anywhere; }
  .intl-declaration .text-xs { font-size: 14px; }
  .sign-section > .flex { flex-wrap: wrap; gap: 8px; }
  .sign-canvas { height: 180px; }
  .f-checkbox { width: 20px; height: 20px; }
  .canvas-placeholder { padding: 0 16px; text-align: center; }
}
</style>
