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
          <option value="REPORT">报表导出</option>
        </select>
        <input v-model="keyword" type="text" class="f-input" style="width: 200px;" placeholder="搜索操作人或单号..." />
        <button
          v-if="isSchoolAdmin"
          type="button"
          class="f-btn f-btn-primary"
          :disabled="verifying"
          @click="verifyChain"
        >
          <FluentIcon name="shield" :size="15" />
          <span>{{ verifying ? '校验中…' : '校验完整性' }}</span>
        </button>
      </div>
    </header>

    <!-- 审计日志防篡改校验结果（P1 第二批 2.2） -->
    <div v-if="verifyResult" class="f-banner" :class="verifyResult.ok ? 'success' : 'error'" role="status">
      <FluentIcon :name="verifyResult.ok ? 'check-circle' : 'alert'" :size="18" />
      <div class="flex-1 text-sm">
        <template v-if="verifyResult.ok">
          <div class="fw-600">哈希链完整：共校验 {{ verifyResult.checked }} 条日志，未发现篡改</div>
          <div class="text-xs mt-4">每条日志的哈希都包含上一条的哈希，任何一条被修改、删除或调换都会在这里报出来。</div>
        </template>
        <template v-else>
          <div class="fw-600">
            发现异常：{{ verifyResult.brokenAt?.reason }}
            <span v-if="verifyResult.brokenAt?.id">（日志 {{ verifyResult.brokenAt.id }}，序号 {{ verifyResult.brokenAt.seq }}）</span>
            <span v-else-if="verifyResult.brokenAt?.seq">（序号 {{ verifyResult.brokenAt.seq }}）</span>
          </div>
          <div class="text-xs mt-4">
            {{ verifyResult.brokenAt?.detail }}。此前 {{ verifyResult.checked }} 条日志校验正常。
          </div>
        </template>
        <div class="text-xs mt-4 text-muted">说明：哈希链能发现篡改，但不能阻止拥有数据库权限的人篡改。</div>
      </div>
      <button type="button" class="f-btn f-btn-subtle f-btn-sm" aria-label="关闭校验结果" @click="verifyResult = null">
        <FluentIcon name="close" :size="13" />
      </button>
    </div>

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
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
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
      store.notify(res.message, res.data?.ok ? 'success' : 'error')
    } else {
      verifyResult.value = null
      store.notify(res.message || '校验失败', 'error')
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
