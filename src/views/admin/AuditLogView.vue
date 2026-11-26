<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <h1 class="text-title">全流程审计日志</h1>
      <div class="flex items-center gap-8 flex-wrap">
        <select v-model="targetFilter" class="f-select" style="width: 140px;">
          <option value="ALL">全部对象</option>
          <option value="JOB">岗位审批</option>
          <option value="APPLICATION">选拔特批</option>
          <option value="CONTRACT">电子合同</option>
          <option value="TIMESHEET">工时审核</option>
          <option value="PAYROLL">薪酬发放</option>
          <option value="EVALUATION">考核评价</option>
          <option value="CONFIG">校级配置</option>
        </select>
        <input v-model="keyword" type="text" class="f-input" style="width: 200px;" placeholder="搜索操作人或单号..." />
      </div>
    </header>

    <section class="acrylic-card table-card">
      <div v-if="filteredLogs.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>时间</th>
              <th>操作人</th>
              <th>对象</th>
              <th>操作节点</th>
              <th>详情</th>
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
                <td><span class="f-pill f-pill-neutral">{{ log.targetType }}</span></td>
                <td class="fw-600">{{ log.action }}</td>
                <td>
                  <button type="button" class="f-expand-trigger">
                    <span>{{ expandedIds.has(log.id) ? '收起' : '展开' }}</span>
                    <FluentIcon :name="expandedIds.has(log.id) ? 'chevron-up' : 'chevron-down'" :size="13" />
                  </button>
                </td>
              </tr>
              <tr v-if="expandedIds.has(log.id)" class="expand-row">
                <td colspan="5" class="text-xs">
                  <div><strong>单据号：</strong><span class="tabular-nums">{{ log.targetId }}</span></div>
                  <div v-if="log.opinion" class="mt-4"><strong>审批意见：</strong>{{ log.opinion }}</div>
                  <div v-if="log.beforeValue || log.afterValue" class="mt-4">
                    <strong>数值变更：</strong><span class="text-muted">{{ log.beforeValue || '-' }}</span> → <span class="text-blue fw-600">{{ log.afterValue || '-' }}</span>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="history" description="暂无匹配的审计日志" />
    </section>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
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
</style>
