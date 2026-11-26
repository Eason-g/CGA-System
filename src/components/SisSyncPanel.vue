<template>
  <section class="acrylic-card p-16 flex-col gap-12">
    <div class="sis-head">
      <div class="min-w-0">
        <div class="fw-600 text-sm">学籍系统同步 <span class="f-pill f-pill-neutral">模拟</span></div>
        <div class="text-xs text-muted mt-4">
          从学籍系统拉取学籍状态（在读/休学/毕业）与签证类型、有效期，更新本系统学生档案并写审计日志。
          {{ store.currentUser?.role === 'INTL_OFFICER' ? '国际生办只同步国际生。' : '' }}
          演示环境数据来自 integration/sis-students.json。
        </div>
      </div>
      <button type="button" class="f-btn f-btn-primary f-btn-sm shrink-0" :disabled="busy || !isHttpMode" @click="sync">
        <FluentIcon name="refresh" :size="13" />
        <span>{{ busy ? '同步中…' : '同步学籍' }}</span>
      </button>
    </div>

    <div v-if="!isHttpMode" class="text-xs text-muted">该功能需要连接后端</div>

    <div v-if="result" class="flex-col gap-8">
      <div class="text-xs text-muted">
        {{ result.syncedAt }} · {{ result.source }} · 比对 {{ result.checked }} 名学生，更新 {{ result.changedStudents }} 名，
        差异 {{ result.diffs.length }} 项
      </div>
      <div v-if="result.diffs.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>学生</th>
              <th>字段</th>
              <th>原值</th>
              <th>新值</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(d, i) in result.diffs" :key="i">
              <td>
                <div class="fw-600">{{ d.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ d.studentNo }}</div>
              </td>
              <td>{{ d.fieldLabel }}</td>
              <td class="tabular-nums">{{ d.before }}</td>
              <td class="tabular-nums fw-600">{{ d.after }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="text-xs text-muted">本系统学生档案与学籍系统一致，无差异。</div>
      <div v-if="result.unmatched.length" class="text-xs text-muted">
        学籍系统中有、本系统未开通的学生：{{ result.unmatched.join('、') }}
      </div>
      <div v-if="result.skipped.length" class="text-xs text-caution">{{ result.skipped.join('；') }}</div>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { isHttpMode } from '@/services/http'
import { integrationApi } from '@/services/integrationApi'
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
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
