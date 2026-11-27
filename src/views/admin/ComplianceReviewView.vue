<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div>
        <h1 class="text-title">{{ $t('admin.compliance.title') }}</h1>
        <div class="text-xs text-muted mt-4">
          {{ $t('admin.compliance.desc') }}
        </div>
      </div>
      <div class="f-segmented">
        <button type="button" :class="{ active: statusFilter === 'PENDING' }" @click="setFilter('PENDING')">{{ $t('admin.compliance.pending') }}</button>
        <button type="button" :class="{ active: statusFilter === '' }" @click="setFilter('')">{{ $t('admin.common.all') }}</button>
      </div>
    </header>

    <!-- P2 B.2：学籍系统同步（模拟） -->
    <SisSyncPanel />

    <div v-if="!isHttpMode" class="f-banner caution">
      <FluentIcon name="alert" :size="16" />
      <span>{{ $t('admin.common.needBackend') }}</span>
    </div>

    <section v-else class="acrylic-card table-card">
      <div v-if="loading" class="p-16 text-xs text-muted">{{ $t('admin.common.loading') }}</div>
      <div v-else-if="items.length" class="f-table-wrap">
        <table class="f-table f-table-sticky-action">
          <thead>
            <tr>
              <th>{{ $t('admin.common.student') }}</th>
              <th>{{ $t('admin.compliance.colMaterial') }}</th>
              <th>{{ $t('admin.compliance.colRecord') }}</th>
              <th>{{ $t('admin.compliance.colUploaded') }}</th>
              <th>{{ $t('admin.common.status') }}</th>
              <th>{{ $t('admin.common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="it in items" :key="it.attachment.id">
              <td>
                <div class="fw-600">{{ it.student.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ it.student.studentNo }} · {{ deptShortName(store.db, it.student.deptId) }}</div>
              </td>
              <td>
                <div class="flex items-center gap-6">
                  <span class="f-pill f-pill-neutral">{{ ATTACHMENT_KIND_MAP[it.attachment.kind] }}</span>
                  <button type="button" class="f-btn-link text-xs truncate att-name" @click="download(it.attachment)">
                    {{ it.attachment.originalName }}
                  </button>
                </div>
              </td>
              <td class="text-xs">
                <template v-if="it.attachment.kind === 'LANG_CERT'">
                  {{ it.student.languageCert || $t('admin.common.notSet') }}{{ $t('admin.compliance.equiv', { n: it.student.languageLevel ?? '-' }) }}
                </template>
                <template v-else>
                  {{ it.student.visaType || $t('admin.common.notSet') }} · {{ $t('admin.compliance.validUntil', { d: it.student.visaExpiry || $t('admin.common.notSet') }) }}
                </template>
              </td>
              <td class="text-xs text-muted tabular-nums">{{ it.attachment.uploadedAt }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${LANG_CERT_STATUS_MAP[it.attachment.verifyStatus]?.tone || 'neutral'}`">
                  {{ statusLabel(LANG_CERT_STATUS_MAP, it.attachment.verifyStatus, '-') }}
                </span>
                <div v-if="it.attachment.verifyOpinion" class="text-xs text-muted mt-4 opinion">{{ it.attachment.verifyOpinion }}</div>
              </td>
              <td>
                <button
                  v-if="it.attachment.verifyStatus === 'PENDING'"
                  type="button"
                  class="f-btn f-btn-primary f-btn-sm"
                  @click="openVerify(it)"
                >
                  {{ $t('admin.compliance.verify') }}
                </button>
                <span v-else class="text-xs text-subtle">{{ $t('admin.compliance.handled') }}</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="shield" :description="statusFilter === 'PENDING' ? $t('admin.compliance.emptyPending') : $t('admin.compliance.empty')" />
    </section>

    <!-- 核验弹窗 -->
    <div v-if="current" class="f-overlay" @click.self="current = null">
      <div class="f-modal" role="dialog" aria-modal="true" :aria-label="$t('admin.compliance.dialogLabel')">
        <header class="f-modal-header">
          <div>
            <div class="text-xs text-muted">{{ ATTACHMENT_KIND_MAP[current.attachment.kind] }} · {{ current.attachment.id }}</div>
            <h2 class="text-sub mt-4">{{ current.student.name }}</h2>
          </div>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" :aria-label="$t('admin.common.close')" @click="current = null">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-12">
          <div class="flex items-center gap-8 text-sm">
            <span class="truncate">{{ current.attachment.originalName }}</span>
            <button type="button" class="f-btn f-btn-sm" @click="download(current.attachment)">{{ $t('admin.compliance.downloadOriginal') }}</button>
          </div>
          <template v-if="current.attachment.kind === 'LANG_CERT'">
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">{{ $t('admin.compliance.certName') }}</label>
                <input v-model="form.languageCert" type="text" class="f-input" :placeholder="$t('admin.compliance.certPh')" />
              </div>
              <div class="f-field">
                <label class="f-label">{{ $t('admin.compliance.level') }}</label>
                <input v-model="form.languageLevel" type="number" min="0" max="9" step="0.5" class="f-input tabular-nums" />
              </div>
            </div>
          </template>
          <template v-else>
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">{{ $t('admin.compliance.visaType') }}</label>
                <input v-model="form.visaType" type="text" class="f-input" />
              </div>
              <div class="f-field">
                <label class="f-label">{{ $t('admin.compliance.visaExpiry') }}</label>
                <input v-model="form.visaExpiry" type="date" class="f-input" />
              </div>
            </div>
          </template>
          <div class="f-field">
            <label class="f-label">{{ $t('admin.compliance.opinion') }}</label>
            <input v-model="form.opinion" type="text" class="f-input" :placeholder="$t('admin.compliance.opinionPh')" />
          </div>
        </div>
        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="current = null">{{ $t('admin.common.cancel') }}</button>
          <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="submitVerify('REJECT')">{{ $t('admin.compliance.rejectBtn') }}</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="submitVerify('APPROVE')">{{ $t('admin.compliance.approveBtn') }}</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { LANG_CERT_STATUS_MAP } from '@/services/ruleEngine'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi, ATTACHMENT_KIND_MAP } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import SisSyncPanel from '@/components/SisSyncPanel.vue'
import { useI18n } from '@/i18n'
import { deptShortName, statusLabel } from './adminI18n'

const store = useAppStore()
const { t, localized } = useI18n()
const statusFilter = ref('PENDING')
const items = ref([])
const loading = ref(false)
const busy = ref(false)
const current = ref(null)
const form = reactive({ languageCert: '', languageLevel: '', visaType: '', visaExpiry: '', opinion: '' })

async function load() {
  if (!isHttpMode) return
  loading.value = true
  try {
    const res = await materialFinanceApi.listComplianceMaterials(statusFilter.value)
    if (res.code === 200) items.value = res.data || []
    else store.notify(localized(res, 'message') || t('admin.common.loadFailed'), 'error')
  } finally {
    loading.value = false
  }
}

function setFilter(v) {
  statusFilter.value = v
  load()
}

function openVerify(it) {
  current.value = it
  form.languageCert = it.student.languageCert || ''
  form.languageLevel = it.student.languageLevel ?? ''
  form.visaType = it.student.visaType || ''
  form.visaExpiry = it.student.visaExpiry || ''
  form.opinion = ''
}

async function submitVerify(decision) {
  const it = current.value
  const isLang = it.attachment.kind === 'LANG_CERT'
  const payload = { attachmentId: it.attachment.id, decision, opinion: form.opinion.trim() }
  if (decision === 'APPROVE') {
    if (isLang) {
      if (form.languageCert.trim()) payload.languageCert = form.languageCert.trim()
      if (form.languageLevel !== '' && form.languageLevel !== null) payload.languageLevel = Number(form.languageLevel)
    } else {
      if (form.visaType.trim()) payload.visaType = form.visaType.trim()
      if (form.visaExpiry) payload.visaExpiry = form.visaExpiry
    }
  }
  busy.value = true
  try {
    const res = await store.mutate(() => materialFinanceApi.verifyCompliance(it.student.id, payload))
    if (res.code === 200) {
      current.value = null
      await load()
    }
  } finally {
    busy.value = false
  }
}

async function download(a) {
  const res = await materialFinanceApi.downloadAttachment(a)
  if (res.code !== 200) store.notify(localized(res, 'message') || t('admin.common.downloadFailed'), 'error')
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; }
.att-name { max-width: 220px; text-align: left; }
.opinion { max-width: 200px; white-space: normal; }
.mt-4 { margin-top: 4px; }

@media (max-width: 640px) {
  .grid-2 { grid-template-columns: 1fr; }
}

@media (max-width: 768px) {
  .f-modal-body > .flex { flex-wrap: wrap; }
  .f-modal-body > .flex > .truncate { flex: 1 1 100%; min-width: 0; }
  .f-modal-body > .flex > .f-btn { flex: 1 1 auto; }
}
</style>
