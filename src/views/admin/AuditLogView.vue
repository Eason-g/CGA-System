<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">{{ $t('admin.audit.title') }}</h1>
      <div class="flex items-center gap-8 flex-wrap">
        <select v-model="targetFilter" class="f-select" style="width: 140px;">
          <option v-for="code in TARGET_TYPES" :key="code" :value="code">{{ auditTargetLabel(code) }}</option>
        </select>
        <input v-model="keyword" type="text" class="f-input" style="width: 200px;" :placeholder="$t('admin.audit.searchPh')" />
        <button
          v-if="isSchoolAdmin"
          type="button"
          class="f-btn f-btn-primary"
          :disabled="verifying"
          @click="verifyChain"
        >
          <FluentIcon name="shield" :size="15" />
          <span>{{ verifying ? $t('admin.audit.verifying') : $t('admin.audit.verify') }}</span>
        </button>
      </div>
    </header>

    <!-- 审计日志防篡改校验结果（P1 第二批 2.2） -->
    <div v-if="verifyResult" class="f-banner" :class="verifyResult.ok ? 'success' : 'error'" role="status">
      <FluentIcon :name="verifyResult.ok ? 'check-circle' : 'alert'" :size="18" />
      <div class="flex-1 text-sm">
        <template v-if="verifyResult.ok">
          <div class="fw-600">{{ $t('admin.audit.okTitle', { n: verifyResult.checked }) }}</div>
          <div class="text-xs mt-4">{{ $t('admin.audit.okDesc') }}</div>
        </template>
        <template v-else>
          <div class="fw-600">
            {{ $t('admin.audit.broken', { r: localized(verifyResult.brokenAt, 'reason') }) }}
            <span v-if="verifyResult.brokenAt?.id">{{ $t('admin.audit.brokenAt', { id: verifyResult.brokenAt.id, seq: verifyResult.brokenAt.seq }) }}</span>
            <span v-else-if="verifyResult.brokenAt?.seq">{{ $t('admin.audit.brokenSeq', { seq: verifyResult.brokenAt.seq }) }}</span>
          </div>
          <div class="text-xs mt-4">
            {{ $t('admin.audit.brokenDetail', { d: localized(verifyResult.brokenAt, 'detail'), n: verifyResult.checked }) }}
          </div>
        </template>
        <div class="text-xs mt-4 text-muted">{{ $t('admin.audit.note') }}</div>
      </div>
      <button type="button" class="f-btn f-btn-subtle f-btn-sm" :aria-label="$t('admin.audit.closeResult')" @click="verifyResult = null">
        <FluentIcon name="close" :size="13" />
      </button>
    </div>

    <section class="acrylic-card table-card">
      <div v-if="filteredLogs.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.audit.colTime') }}</th>
              <th>{{ $t('admin.audit.colOperator') }}</th>
              <th>{{ $t('admin.audit.colTarget') }}</th>
              <th>{{ $t('admin.audit.colAction') }}</th>
              <th>{{ $t('admin.common.details') }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="log in filteredLogs" :key="log.id">
              <tr class="cursor-pointer" @click="toggle(log.id)">
                <td class="text-xs tabular-nums text-muted" style="white-space: nowrap;">{{ log.timestamp }}</td>
                <td>
                  <span class="fw-600">{{ log.operatorName }}</span>
                  <span class="text-xs text-muted ml-6">{{ log.operatorRole }}</span>
                </td>
                <td><span class="f-pill f-pill-neutral">{{ auditTargetLabel(log.targetType) }}</span></td>
                <td class="fw-600">{{ log.action }}</td>
                <td>
                  <button type="button" class="f-expand-trigger">
                    <span>{{ expandedIds.has(log.id) ? $t('admin.common.collapse') : $t('admin.common.expand') }}</span>
                    <FluentIcon :name="expandedIds.has(log.id) ? 'chevron-up' : 'chevron-down'" :size="13" />
                  </button>
                </td>
              </tr>
              <tr v-if="expandedIds.has(log.id)" class="expand-row">
                <td colspan="5" class="text-xs">
                  <div><strong>{{ $t('admin.audit.recordNo') }}</strong><span class="tabular-nums">{{ log.targetId }}</span></div>
                  <div v-if="log.opinion" class="mt-4"><strong>{{ $t('admin.audit.opinion') }}</strong>{{ log.opinion }}</div>
                  <div v-if="log.beforeValue || log.afterValue" class="mt-4">
                    <strong>{{ $t('admin.audit.change') }}</strong><span class="text-muted">{{ log.beforeValue || '-' }}</span> → <span class="text-blue fw-600">{{ log.afterValue || '-' }}</span>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="history" :description="$t('admin.audit.empty')" />
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import { useI18n } from '@/i18n'
import { auditTargetLabel } from './adminI18n'

const TARGET_TYPES = ['ALL', 'JOB', 'APPLICATION', 'CONTRACT', 'TIMESHEET', 'PAYROLL', 'EVALUATION', 'CONFIG', 'REPORT', 'USER', 'INTEGRATION']

const store = useAppStore()
const { t, localized } = useI18n()
const isSchoolAdmin = computed(() => store.currentUser?.role === 'SCHOOL_ADMIN')
const verifying = ref(false)
const verifyResult = ref(null)

// 从头重算审计哈希链（仅校级管理员）
async function verifyChain() {
  verifying.value = true
  try {
    const res = await api.verifyAuditChain()
    if (res.code === 200) {
      verifyResult.value = res.data
      store.notify(localized(res, 'message'), res.data?.ok ? 'success' : 'error')
    } else {
      verifyResult.value = null
      store.notify(localized(res, 'message') || t('admin.audit.verifyFailed'), 'error')
    }
  } finally {
    verifying.value = false
  }
}

const targetFilter = ref('ALL')
const keyword = ref('')
const expandedIds = ref(new Set())

const filteredLogs = computed(() => {
  return store.db.auditLogs.filter((l) => {
    if (targetFilter.value !== 'ALL' && l.targetType !== targetFilter.value) return false
    if (keyword.value.trim()) {
      const q = keyword.value.trim().toLowerCase()
      const hay = `${l.operatorName} ${l.operatorRole} ${l.action} ${l.targetId} ${l.opinion}`.toLowerCase()
      if (!hay.includes(q)) return false
    }
    return true
  })
})

function toggle(id) {
  const s = new Set(expandedIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedIds.value = s
}
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.cursor-pointer { cursor: pointer; }
.expand-row { background: rgba(243, 242, 241, 0.55); }
.ml-6 { margin-left: 6px; }
.mt-4 { margin-top: 4px; }
.flex-1 { flex: 1; }
</style>
