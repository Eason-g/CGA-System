<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal wide" role="dialog" aria-modal="true" aria-label="电子聘用协议签署">
      <header class="f-modal-header">
        <div>
          <div class="text-xs text-muted">协议编号：{{ contract.id }}</div>
          <h2 class="text-sub mt-4">高校“三助”岗位电子聘用协议</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" aria-label="关闭">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body flex-col gap-16">
        <!-- Contract Document Box -->
        <div class="contract-doc">
          <div class="doc-title">高校助教 / 助研 / 助管岗位聘用协议书</div>
          <div class="doc-grid">
            <div><span class="text-muted">甲方（设岗单位）：</span><strong>{{ store.getDeptName(contract.deptId) }}</strong></div>
            <div><span class="text-muted">指导教师/负责人：</span><strong>{{ mentor?.name || '-' }}</strong></div>
            <div><span class="text-muted">乙方（受聘学生）：</span><strong>{{ student?.name }}（学号 {{ student?.studentNo }}）</strong></div>
            <div><span class="text-muted">岗位类别与名称：</span><strong>[{{ job?.type }}] {{ job?.title }}</strong></div>
            <div><span class="text-muted">聘用起止日期：</span><strong class="tabular-nums">{{ contract.startDate }} 至 {{ contract.endDate }}</strong></div>
            <div><span class="text-muted">约定工时与酬金：</span><strong class="tabular-nums">{{ contract.weeklyHours }} 小时/周 · ¥{{ contract.hourlyRate }}/小时</strong></div>
          </div>

          <ol class="doc-clauses">
            <li>乙方应严格遵守学校“三助”岗位管理规定，按周如实填报实际工时与工作日志，接受指导教师审核与周期性考核。</li>
            <li>乙方在全校范围内兼任所有三助（TA/RA/GA）岗位之累计周工时，不得超过学校规定的每周 {{ student?.maxWeeklyHours || 20 }} 小时红线。</li>
            <li>实发薪酬按「审核通过工时 × 时薪 × 周期考核系数」自动核算，经财务处审核通过后从对应专项经费池发放。</li>
            <li>若聘期内发生学籍异动、签证失效或连续考核不合格，甲方有权依法依规中止本聘用协议并记录审计留痕。</li>
          </ol>
        </div>

        <!-- Mandatory International Student Compliance Declaration -->
        <div v-if="student?.isInternational" class="intl-declaration">
          <label class="checkbox-row">
            <input v-model="complianceAgreed" type="checkbox" class="f-checkbox" />
            <div>
              <div class="fw-600 text-sm">
                《国际生合法工时合规声明》（国际生强制确认项）
              </div>
              <p class="text-xs text-muted mt-4">
                本人确认持有有效学习类居留许可（{{ student.visaType }}，有效期至 {{ student.visaExpiry }}），严格遵守中国出入境管理法规及高校国际生勤工助学规定，承诺每周累计校内三助工时绝不超过 20 小时，若签证状态变更将第一时间向国际合作交流处报备。
              </p>
            </div>
          </label>
        </div>

        <!-- Handwritten Canvas Signature Pad -->
        <div class="sign-section">
          <div class="flex items-center justify-between mb-8">
            <label class="f-label">
              <FluentIcon name="pen" :size="15" />
              <span>乙方手写电子签名区（支持鼠标/触控手写）</span>
              <span class="req">*</span>
            </label>
            <button type="button" class="f-btn f-btn-sm" @click="clearCanvas">重写签名</button>
          </div>

          <div class="canvas-wrap">
            <canvas
              ref="canvasRef"
              width="680"
              height="160"
              class="sign-canvas"
              @mousedown="startDraw"
              @mousemove="draw"
              @mouseup="endDraw"
              @mouseleave="endDraw"
              @touchstart.prevent="startDraw"
              @touchmove.prevent="draw"
              @touchend.prevent="endDraw"
            />
            <span v-if="!hasSigned" class="canvas-placeholder">请在此区域内手写您的姓名完成电子签章</span>
          </div>
        </div>
      </div>

      <footer class="f-modal-footer">
        <button type="button" class="f-btn" @click="$emit('close')">暂不签署</button>
        <button
          type="button"
          class="f-btn f-btn-primary"
          :disabled="!canSubmit || submitting"
          @click="submitSign"
        >
          确认签署并生效
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  contract: { type: Object, required: true }
})

const emit = defineEmits(['close', 'signed'])
const store = useAppStore()

const student = computed(() => store.getUser(props.contract.studentId))
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
  ctx = canvas.getContext('2d')
  ctx.strokeStyle = '#201f1e'
  ctx.lineWidth = 2.4
  ctx.lineCap = 'round'
  ctx.lineJoin = 'round'
})

function getPos(e) {
  const canvas = canvasRef.value
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  const point = e.touches ? e.touches[0] : e
  return {
    x: (point.clientX - rect.left) * scaleX,
    y: (point.clientY - rect.top) * scaleY
  }
}

function startDraw(e) {
  if (!ctx) return
  isDrawing.value = true
  const { x, y } = getPos(e)
  ctx.beginPath()
  ctx.moveTo(x, y)
}

function draw(e) {
  if (!isDrawing.value || !ctx) return
  const { x, y } = getPos(e)
  ctx.lineTo(x, y)
  ctx.stroke()
  hasSigned.value = true
}

function endDraw() {
  if (!isDrawing.value) return
  isDrawing.value = false
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
</style>
