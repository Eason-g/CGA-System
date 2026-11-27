<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal" role="dialog" aria-modal="true" :aria-label="t('apply.dialogLabel')">
      <header class="f-modal-header">
        <div>
          <div class="flex items-center gap-8">
            <span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span>
            <span class="text-xs text-muted">{{ store.getDeptName(job.deptId) }}</span>
          </div>
          <h2 class="text-sub mt-6">{{ job.title }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" :aria-label="t('common.close')">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body flex-col gap-16">
        <!-- Compliance Checklist (Auto-run hard checks) -->
        <section class="check-box" :class="checkResult.passed ? 'passed' : 'blocked'">
          <div class="flex items-center justify-between mb-10">
            <span class="fw-600 flex items-center gap-8">
              <FluentIcon name="shield" :size="16" />
              <span>{{ t('apply.checkTitle') }}</span>
            </span>
            <span class="f-pill" :class="checkResult.passed ? 'f-pill-success' : 'f-pill-error'">
              {{ checkResult.passed ? t('apply.allPassed') : t('apply.hasBlockers') }}
            </span>
          </div>

          <div class="check-list">
            <div v-for="item in checkResult.checks" :key="item.key" class="check-row">
              <span class="check-icon" :class="item.passed ? 'ok' : 'fail'">
                <FluentIcon :name="item.passed ? 'check-circle' : 'x-circle'" :size="18" />
              </span>
              <div class="check-content">
                <div class="fw-600 text-sm">{{ localized(item, 'label') }}</div>
                <div class="text-xs mt-2" :class="item.passed ? 'text-muted' : 'text-error fw-600'">
                  {{ localized(item, 'detail') }}
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
            <div class="text-xs"><strong>{{ localized(hint, 'label') }}{{ t('common.colon') }}</strong>{{ localized(hint, 'detail') }}</div>
          </div>

          <div v-if="checkResult.alreadyApplied" class="f-banner caution mt-12">
            <FluentIcon name="alert" :size="16" />
            <span>{{ t('apply.alreadyApplied', { status: localized(APP_STATUS_MAP[checkResult.existingApp?.status], 'label') || checkResult.existingApp?.status }) }}</span>
          </div>
          <div v-else-if="checkResult.quotaFull" class="f-banner error mt-12">
            <FluentIcon name="x-circle" :size="16" />
            <span>{{ t('apply.quotaFull', { hired: job.hiredCount, quota: job.quota }) }}</span>
          </div>
        </section>

        <!-- Standard Application Form -->
        <div class="f-field">
          <label class="f-label">{{ t('apply.statement') }} <span class="req">*</span></label>
          <textarea
            v-model="statement"
            class="f-textarea"
            rows="3"
            :placeholder="t('apply.statementPlaceholder')"
          />
        </div>

        <!-- http 模式：上传真实材料（pdf/jpg/png，≤10MB）；mock 模式保留简历文件名 -->
        <div v-if="isHttpMode" class="f-field">
          <label class="f-label">{{ t('apply.materials') }}</label>
          <div class="flex items-center gap-8 flex-wrap">
            <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
              <FluentIcon name="plus" :size="14" />
              <span>{{ t('apply.uploadResume') }}</span>
              <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'RESUME')" />
            </label>
            <label class="f-btn f-btn-sm upload-btn" :class="{ disabled: uploading }">
              <FluentIcon name="plus" :size="14" />
              <span>{{ t('apply.uploadOther') }}</span>
              <input type="file" :accept="ACCEPT_TYPES" hidden :disabled="uploading" @change="onPick($event, 'OTHER')" />
            </label>
            <span v-if="uploading" class="text-xs text-muted">{{ t('common.uploading') }}</span>
          </div>
          <ul v-if="attachments.length" class="att-list mt-8">
            <li v-for="a in attachments" :key="a.id" class="att-row">
              <span class="f-pill f-pill-neutral">{{ kindLabel(a.kind) }}</span>
              <span class="text-sm truncate">{{ a.originalName }}</span>
              <span class="text-xs text-muted">{{ formatFileSize(a.sizeBytes) }}</span>
              <button type="button" class="f-btn-link text-xs" @click="removeAttachment(a.id)">{{ t('common.remove') }}</button>
            </li>
          </ul>
          <div v-else class="text-xs text-muted mt-4">{{ t('apply.noMaterials') }}</div>
        </div>
        <div v-else class="f-field">
          <label class="f-label">{{ t('apply.resumeMock') }}</label>
          <input v-model="resumeFileName" type="text" class="f-input" />
        </div>

        <!-- Special Approval Trigger Link & Form when hard-blocked -->
        <div v-if="!checkResult.passed && checkResult.canSpecialApprove" class="special-section">
          <div class="flex items-center justify-between">
            <span class="text-xs text-muted">{{ t('apply.blockedNote') }}</span>
            <button
              type="button"
              class="f-btn-link text-xs"
              @click="showSpecialForm = !showSpecialForm"
            >
              {{ showSpecialForm ? t('apply.hideSpecial') : t('apply.showSpecial') }}
            </button>
          </div>

          <div v-if="showSpecialForm" class="special-box mt-12">
            <div class="f-banner caution mb-12">
              <FluentIcon name="alert" :size="16" />
              <div class="text-xs">
                <strong>{{ t('apply.specialNoteTitle') }}</strong>{{ t('apply.specialNote') }}
              </div>
            </div>
            <div class="f-field">
              <label class="f-label">{{ t('apply.specialReason') }} <span class="req">*</span></label>
              <textarea
                v-model="specialReason"
                class="f-textarea"
                rows="3"
                :placeholder="t('apply.specialReasonPlaceholder')"
              />
            </div>
          </div>
        </div>
        <div
          v-else-if="!checkResult.passed && !checkResult.alreadyApplied && !checkResult.quotaFull"
          class="f-banner error mt-12"
        >
          <FluentIcon name="alert" :size="16" />
          <div class="text-xs">{{ specialBlockedText }}</div>
        </div>
      </div>

      <footer class="f-modal-footer">
        <button type="button" class="f-btn" @click="$emit('close')">{{ t('common.cancel') }}</button>
        <button
          v-if="showSpecialForm && !checkResult.passed && checkResult.canSpecialApprove"
          type="button"
          class="f-btn f-btn-primary"
          :disabled="submitting || specialReason.trim().length < 6"
          @click="handleSubmit(true)"
        >
          {{ t('apply.submitSpecial') }}
        </button>
        <button
          v-else
          type="button"
          class="f-btn f-btn-primary"
          :disabled="!checkResult.passed || submitting"
          @click="handleSubmit(false)"
        >
          {{ t('apply.submit') }}
        </button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication, APP_STATUS_MAP } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
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
const { t, isEn, localized } = useI18n()

const student = computed(() => store.currentUser)
const checkResult = computed(() => validateStudentApplication(store.db, student.value, props.job))

// 申请陈述预填：国际生或英文界面用英文模板，其余用中文模板（学生可自行修改）
const statement = ref(
  student.value?.isInternational || isEn.value
    ? t('apply.defaultStatementEn', { name: student.value?.name || '', major: student.value?.major || '', degree: student.value?.degree || '' })
    : t('apply.defaultStatementZh', { dept: store.getDeptShortName(student.value?.deptId), degree: student.value?.degree || '', major: student.value?.major || '' })
)
const resumeFileName = ref(t('apply.defaultResumeName', { name: student.value?.name || t('role.STUDENT') }))

// 附件类型按代码翻译
function kindLabel(kind) {
  const key = `attachmentKind.${kind}`
  const text = t(key)
  return text === key ? ATTACHMENT_KIND_MAP[kind] || kind : text
}

// 不可特批的原因：英文模式优先用规则引擎的 specialBlockedReasonEn，没有时按状态给出通用英文说明
const specialBlockedText = computed(() => {
  const r = checkResult.value
  if (!isEn.value || r.specialBlockedReasonEn) return localized(r, 'specialBlockedReason')
  if (r.alreadyApplied) return t('apply.blockedApplied')
  if (r.quotaFull) return t('apply.blockedFull')
  const hard = (r.checks || []).filter((c) => !c.passed && !c.overridable).map((c) => localized(c, 'label'))
  return hard.length ? t('apply.blockedHard', { items: hard.join(', ') }) : t('apply.blockedNone')
})
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
      store.notify(t('apply.uploaded', { name: res.data.originalName }), 'success')
    } else {
      store.notify(localized(res, 'message') || t('apply.uploadFailed'), 'error')
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

@media (max-width: 768px) {
  .check-box { padding: 12px; }
  .check-box > .flex.justify-between { flex-wrap: wrap; gap: 8px; }
  .check-row .text-xs { font-size: 14px; }
  .special-section > .flex { flex-wrap: wrap; gap: 4px; }
  .special-box { padding: 12px; }
  .upload-btn { flex: 1 1 140px; }
  .att-row { flex-wrap: wrap; }
}
</style>
