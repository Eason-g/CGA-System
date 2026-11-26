<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal" role="dialog" aria-modal="true" aria-label="岗位申请与合规前置校验">
      <header class="f-modal-header">
        <div>
          <div class="flex items-center gap-8">
            <span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span>
            <span class="text-xs text-muted">{{ store.getDeptName(job.deptId) }}</span>
          </div>
          <h2 class="text-sub mt-6">{{ job.title }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" aria-label="关闭">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body flex-col gap-16">
        <!-- Compliance Checklist (Auto-run hard checks) -->
        <section class="check-box" :class="checkResult.passed ? 'passed' : 'blocked'">
          <div class="flex items-center justify-between mb-10">
            <span class="fw-600 flex items-center gap-8">
              <FluentIcon name="shield" :size="16" />
              <span>申请资格与合规前置硬校验</span>
            </span>
            <span class="f-pill" :class="checkResult.passed ? 'f-pill-success' : 'f-pill-error'">
              {{ checkResult.passed ? '全部达标 · 允许提交' : '存在拦截项 · 禁止直投' }}
            </span>
          </div>

          <div class="check-list">
            <div v-for="item in checkResult.checks" :key="item.key" class="check-row">
              <span class="check-icon" :class="item.passed ? 'ok' : 'fail'">
                <FluentIcon :name="item.passed ? 'check-circle' : 'x-circle'" :size="18" />
              </span>
              <div class="check-content">
                <div class="fw-600 text-sm">{{ item.label }}</div>
                <div class="text-xs mt-2" :class="item.passed ? 'text-muted' : 'text-error fw-600'">
                  {{ item.detail }}
                </div>
              </div>
            </div>
          </div>

          <!-- 提示性检查项（不拦截），如签证有效期是否覆盖预计聘期 -->
          <div
            v-for="hint in (checkResult.hints || []).filter((h) => !h.passed)"
            :key="hint.key"
            class="f-banner caution mt-12"
          >
            <FluentIcon name="alert" :size="16" />
            <div class="text-xs"><strong>{{ hint.label }}：</strong>{{ hint.detail }}</div>
          </div>

          <div v-if="checkResult.alreadyApplied" class="f-banner caution mt-12">
            <FluentIcon name="alert" :size="16" />
            <span>您已投递过该岗位（状态：{{ checkResult.existingApp?.status }}），不可重复申请。</span>
          </div>
          <div v-else-if="checkResult.quotaFull" class="f-banner error mt-12">
            <FluentIcon name="x-circle" :size="16" />
            <span>该岗位招聘名额已招满（{{ job.hiredCount }}/{{ job.quota }}）。</span>
          </div>
        </section>

        <!-- Standard Application Form -->
        <div class="f-field">
          <label class="f-label">个人申请陈述 <span class="req">*</span></label>
          <textarea
            v-model="statement"
            class="f-textarea"
            rows="3"
            placeholder="请简述您的专业背景、相关课程/科研经验以及每周可稳定投入的时间..."
          />
        </div>

        <!-- http 模式：上传真实材料（pdf/jpg/png，≤10MB）；mock 模式保留简历文件名 -->
        <div v-if="isHttpMode" class="f-field">
          <label class="f-label">申请材料（简历及其他证明，PDF / JPG / PNG，单个 ≤ 10MB）</label>
          <div class="flex items-center gap-8 flex-wrap">
            <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
              <FluentIcon name="plus" :size="14" />
              <span>上传简历</span>
              <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'RESUME')" />
            </label>
            <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
              <FluentIcon name="plus" :size="14" />
              <span>上传其他材料</span>
              <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'OTHER')" />
            </label>
            <span v-if="uploading" class="text-xs text-muted">上传中…</span>
          </div>
          <ul v-if="attachments.length" class="att-list mt-8">
            <li v-for="a in attachments" :key="a.id" class="att-row">
              <span class="f-pill f-pill-neutral">{{ ATTACHMENT_KIND_MAP[a.kind] }}</span>
              <span class="text-sm truncate">{{ a.originalName }}</span>
              <span class="text-xs text-muted">{{ formatFileSize(a.sizeBytes) }}</span>
              <button type="button" class="f-btn-link text-xs" @click="removeAttachment(a.id)">移除</button>
            </li>
          </ul>
          <div v-else class="text-xs text-muted mt-4">未上传材料时，将使用学籍档案中的默认简历。</div>
        </div>
        <div v-else class="f-field">
          <label class="f-label">附件简历（已关联学籍档案）</label>
          <input v-model="resumeFileName" type="text" class="f-input" />
        </div>

        <!-- Special Approval Trigger Link & Form when hard-blocked -->
        <div v-if="!checkResult.passed && checkResult.canSpecialApprove" class="special-section">
          <div class="flex items-center justify-between">
            <span class="text-xs text-muted">系统硬校验未通过，主申请按钮已禁用。</span>
            <button
              type="button"
              class="f-btn-link text-xs"
              @click="showSpecialForm = !showSpecialForm"
            >
              {{ showSpecialForm ? '收起特批表单' : '确需超限/豁免？发起国际生办特批申请' }}
            </button>
          </div>

          <div v-if="showSpecialForm" class="special-box mt-12">
            <div class="f-banner caution mb-12">
              <FluentIcon name="alert" :size="16" />
              <div class="text-xs">
                <strong>特批留痕说明：</strong>提交后单据将以<span class="fw-600">「特批留痕」</span>标黄推送至国际合作交流处专项审核，审批通过后方可进入导师选拔流程。
              </div>
            </div>
            <div class="f-field">
              <label class="f-label">特批申请理由（必填，不少于 6 字） <span class="req">*</span></label>
              <textarea
                v-model="specialReason"
                class="f-textarea"
                rows="3"
                placeholder="请详细说明申请工时/语言特批的合理依据（如全英文学位背景、短期集中助教排班说明等）..."
              />
            </div>
          </div>
        </div>
        <div
          v-else-if="!checkResult.passed && !checkResult.alreadyApplied && !checkResult.quotaFull"
          class="f-banner error mt-12"
        >
          <FluentIcon name="alert" :size="16" />
          <div class="text-xs">{{ checkResult.specialBlockedReason }}</div>
        </div>
      </div>

      <footer class="f-modal-footer">
        <button type="button" class="f-btn" @click="$emit('close')">取消</button>
        <button
          v-if="showSpecialForm && !checkResult.passed && checkResult.canSpecialApprove"
          type="button"
          class="f-btn f-btn-primary"
          :disabled="submitting || specialReason.trim().length < 6"
          @click="handleSubmit(true)"
        >
          提交特批申请（留痕审核）
        </button>
        <button
          v-else
          type="button"
          class="f-btn f-btn-primary"
          :disabled="!checkResult.passed || submitting"
          @click="handleSubmit(false)"
        >
          确认提交申请
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication } from '@/services/ruleEngine'
import { api } from '@/services/api'
import { isHttpMode } from '@/services/http'
import {
  materialFinanceApi,
  ACCEPT_TYPES,
  ATTACHMENT_KIND_MAP,
  formatFileSize
} from '@/services/materialFinanceApi'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  job: { type: Object, required: true }
})

const emit = defineEmits(['close', 'submitted'])
const store = useAppStore()

const student = computed(() => store.currentUser)
const checkResult = computed(() => validateStudentApplication(store.db, student.value, props.job))

const statement = ref(
  student.value?.isInternational
    ? `I am ${student.value.name} (${student.value.major}, ${student.value.degree}). I have verified my academic schedule and wish to apply for this position.`
    : `本人为${store.getDeptShortName(student.value?.deptId)}${student.value?.degree}，专业方向${student.value?.major}，已核对时间安排并申请该岗位。`
)
const resumeFileName = ref(`${student.value?.name || '学生'}_个人简历.pdf`)
const showSpecialForm = ref(false)
const specialReason = ref('')
const submitting = ref(false)
const attachments = ref([])
const uploading = ref(false)

async function onPick(event, kind) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    const res = await materialFinanceApi.uploadAttachment(file, kind)
    if (res.code === 200 && res.data) {
      attachments.value.push(res.data)
      store.notify(`已上传「${res.data.originalName}」`, 'success')
    } else {
      store.notify(res.message || '上传失败', 'error')
    }
  } finally {
    uploading.value = false
  }
}

function removeAttachment(id) {
  attachments.value = attachments.value.filter((a) => a.id !== id)
}

async function handleSubmit(isSpecial) {
  submitting.value = true
  try {
    const res = await store.mutate(() =>
      api.submitApplication(
        {
          jobId: props.job.id,
          statement: statement.value,
          // http 模式下简历文件名由后端取自上传的简历（未上传则用默认名）
          resumeFileName: isHttpMode ? '' : resumeFileName.value,
          isSpecialApproval: isSpecial,
          specialReason: specialReason.value,
          attachmentIds: attachments.value.map((a) => a.id)
        },
        student.value
      )
    )
    if (res.code === 200) {
      emit('submitted', res.data)
      emit('close')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.check-box {
  padding: 16px;
  border-radius: var(--radius-buttons);
  border: 1px solid var(--color-border-light);
  background: #faf9f8;
}

.check-box.passed {
  border-color: rgba(0, 204, 106, 0.38);
  background: rgba(0, 204, 106, 0.04);
}

.check-box.blocked {
  border-color: rgba(232, 17, 35, 0.35);
  background: rgba(232, 17, 35, 0.03);
}

.check-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.check-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 10px;
  background: #ffffff;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
}

.check-icon {
  display: inline-flex;
  margin-top: 1px;
}

.check-icon.ok {
  color: var(--color-success);
}

.check-icon.fail {
  color: var(--color-error);
}

.special-section {
  padding-top: 12px;
  border-top: 1px dashed var(--color-border);
}

.special-box {
  padding: 14px;
  border: 1px solid var(--color-caution);
  border-radius: 4px;
  background: rgba(255, 185, 0, 0.05);
}

.upload-btn { cursor: pointer; display: inline-flex; align-items: center; gap: 4px; }
.upload-btn.disabled { opacity: 0.5; pointer-events: none; }
.att-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 6px; }
.att-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
  background: #ffffff;
  min-width: 0;
}
.att-row .truncate { flex: 1; min-width: 0; }
.mt-4 { margin-top: 4px; }
.mt-8 { margin-top: 8px; }
.mb-10 { margin-bottom: 10px; }
.mt-2 { margin-top: 2px; }
.mt-6 { margin-top: 6px; }
</style>
