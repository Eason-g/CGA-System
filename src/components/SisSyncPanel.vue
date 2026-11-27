<template>
  <section class="acrylic-card p-16 flex-col gap-12">
    <div class="sis-head">
      <div class="min-w-0">
        <div class="fw-600 text-sm">{{ $t('admin.sis.title') }} <span class="f-pill f-pill-neutral">{{ $t('admin.sis.mock') }}</span></div>
        <div class="text-xs text-muted mt-4">
          {{ $t('admin.sis.desc') }}
          {{ store.currentUser?.role === 'INTL_OFFICER' ? $t('admin.sis.intlOnly') : '' }}
          {{ $t('admin.sis.demoData') }}
        </div>
      </div>
      <button type="button" class="f-btn f-btn-primary f-btn-sm shrink-0" :disabled="busy || !isHttpMode" @click="sync">
        <FluentIcon name="refresh" :size="13" />
        <span>{{ busy ? $t('admin.sis.syncing') : $t('admin.sis.sync') }}</span>
      </button>
    </div>

    <div v-if="!isHttpMode" class="text-xs text-muted">{{ $t('admin.common.needBackend') }}</div>

    <div v-if="result" class="flex-col gap-8">
      <div class="text-xs text-muted">
        {{ $t('admin.sis.summary', { t: result.syncedAt, s: result.source, n: result.checked, c: result.changedStudents, d: result.diffs.length }) }}
      </div>
      <div v-if="result.diffs.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>{{ $t('admin.common.student') }}</th>
              <th>{{ $t('admin.sis.colField') }}</th>
              <th>{{ $t('admin.sis.colBefore') }}</th>
              <th>{{ $t('admin.sis.colAfter') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(d, i) in result.diffs" :key="i">
              <td>
                <div class="fw-600">{{ d.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ d.studentNo }}</div>
              </td>
              <td>{{ localized(d, 'fieldLabel') }}</td>
              <td class="tabular-nums">{{ d.before }}</td>
              <td class="tabular-nums fw-600">{{ d.after }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="text-xs text-muted">{{ $t('admin.sis.noDiff') }}</div>
      <div v-if="result.unmatched.length" class="text-xs text-muted">
        {{ $t('admin.sis.unmatched', { list: result.unmatched.join($t('admin.common.listSep')) }) }}
      </div>
      <div v-if="result.skipped.length" class="text-xs text-caution">{{ localizedList(result, 'skipped').join($t('admin.sis.skippedSep')) }}</div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { isHttpMode } from '@/services/http'
import { integrationApi } from '@/services/integrationApi'
import FluentIcon from '@/components/FluentIcon.vue'
import { useI18n } from '@/i18n'
import { localizedList } from '@/views/admin/adminI18n'

const store = useAppStore()
const { localized } = useI18n()
const busy = ref(false)
const result = ref(null)

async function sync() {
  busy.value = true
  try {
    const res = await store.mutate(() => integrationApi.syncSis())
    if (res.code === 200 && res.data) result.value = res.data
  } finally {
    busy.value = false
  }
}
</script>

<style scoped>
.sis-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.min-w-0 { min-width: 0; flex: 1; }
.shrink-0 { flex-shrink: 0; }
.mt-4 { margin-top: 4px; }
</style>
