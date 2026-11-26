<template>
  <div class="portal-page">
    <header class="flex items-center justify-between flex-wrap gap-12 mb-16">
      <h1 class="text-title">我的投递</h1>
      <router-link to="/student/jobs" class="f-btn f-btn-primary f-btn-sm">浏览岗位</router-link>
    </header>

    <div v-if="myApps.length" class="flex-col gap-12">
      <article v-for="app in myApps" :key="app.id" class="acrylic-card p-16">
        <div class="flex items-center justify-between gap-12 flex-wrap">
          <div class="flex items-center gap-8 min-w-0 flex-wrap">
            <span class="f-type-tag" :class="store.getJob(app.jobId)?.type?.toLowerCase()">
              {{ store.getJob(app.jobId)?.type }}
            </span>
            <h2 class="text-lg fw-600">{{ store.getJob(app.jobId)?.title }}</h2>
            <span class="f-pill" :class="`f-pill-${APP_STATUS_MAP[app.status]?.tone || 'neutral'}`">
              {{ APP_STATUS_MAP[app.status]?.label || app.status }}
            </span>
            <span v-if="app.isSpecialApproval" class="f-pill f-pill-caution">特批留痕</span>
          </div>

          <div class="flex items-center gap-8">
            <button type="button" class="f-expand-trigger" @click="toggle(app.id)">
              <span>{{ expandedIds.has(app.id) ? '收起' : '详情' }}</span>
              <FluentIcon :name="expandedIds.has(app.id) ? 'chevron-up' : 'chevron-down'" :size="14" />
            </button>
            <router-link v-if="app.status === 'HIRED'" to="/student/contracts" class="f-btn f-btn-primary f-btn-sm">
              合同管理 →
            </router-link>
          </div>
        </div>

        <div class="f-meta-line mt-8">
          <span class="tabular-nums">投递于 {{ app.appliedAt.slice(0, 16) }}</span>
          <template v-if="app.interviewInfo">
            <span class="f-meta-sep">·</span>
            <span class="text-blue fw-600">面试：{{ app.interviewInfo.time }}（{{ app.interviewInfo.location }}）</span>
          </template>
          <template v-if="app.score !== null && app.score !== undefined">
            <span class="f-meta-sep">·</span>
            <span class="tabular-nums fw-600">评分 {{ app.score }}</span>
          </template>
        </div>

        <!-- Click-to-expand Details (Compliance Checks, Special Reason, Feedback) -->
        <div v-if="expandedIds.has(app.id)" class="f-expand-panel flex-col gap-8">
          <div>
            <div v-for="c in app.complianceCheckResult?.checks || []" :key="c.key" class="flex items-center gap-6">
              <FluentIcon :name="c.passed ? 'check-circle' : 'x-circle'" :size="14" :class="c.passed ? 'text-success' : 'text-error'" />
              <span><strong>{{ c.label }}</strong>：{{ c.detail }}</span>
            </div>
          </div>
          <div v-if="app.isSpecialApproval" class="text-caution">
            <strong>特批理由：</strong>{{ app.specialReason }}
          </div>
          <div v-if="app.mentorComment" class="text-muted">导师评语：{{ app.mentorComment }}</div>
          <div class="text-muted">简历附件：{{ app.resumeFileName }}</div>
        </div>
      </article>
    </div>

    <div v-else class="acrylic-card">
      <EmptyState
        icon="file"
        description="暂无投递记录"
        action-text="前往岗位大厅"
        @action="router.push('/student/jobs')"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { APP_STATUS_MAP } from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'

const store = useAppStore()
const router = useRouter()
const expandedIds = ref(new Set())

const myApps = computed(() => store.db.applications.filter((a) => a.studentId === store.currentUser?.id))

function toggle(id) {
  const s = new Set(expandedIds.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedIds.value = s
}
</script>

<style scoped>
.portal-page { max-width: 1200px; margin: 24px auto 0; padding: 0 24px; }
.min-w-0 { min-width: 0; }
@media (max-width: 960px) { .portal-page { padding: 0 16px; } }
</style>
