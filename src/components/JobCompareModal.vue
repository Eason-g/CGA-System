<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal xl" role="dialog" aria-modal="true" aria-label="三助岗位横向对比">
      <header class="f-modal-header">
        <div>
          <h2 class="text-sub">岗位横向对比（{{ items.length }} 个）</h2>
          <p class="text-xs text-muted mt-2">结合您的个人学籍与当前已占周工时实时试算，高亮最优项辅助决策</p>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" aria-label="关闭">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body compare-body">
        <div class="compare-table-wrap">
          <table class="compare-table">
            <thead>
              <tr>
                <th class="dim-col">对比维度</th>
                <th v-for="it in items" :key="it.job.id" class="job-col">
                  <div class="flex items-center justify-between gap-6">
                    <span class="f-type-tag" :class="it.job.type.toLowerCase()">{{ it.job.type }}</span>
                    <button
                      type="button"
                      class="remove-col-btn"
                      title="移出对比"
                      @click="$emit('remove', it.job.id)"
                    >
                      <FluentIcon name="close" :size="13" />
                    </button>
                  </div>
                  <div class="col-title mt-8">{{ it.job.title }}</div>
                  <div class="text-xs text-muted mt-4">{{ store.getDeptShortName(it.job.deptId) }}</div>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td class="dim-label">岗位时薪</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <span class="big-val" :class="{ best: it.job.hourlyRate === maxRate }">
                    ¥{{ it.job.hourlyRate }}/h
                  </span>
                  <span v-if="it.job.hourlyRate === maxRate && items.length > 1" class="f-pill f-pill-success ml-6">最高</span>
                </td>
              </tr>

              <tr>
                <td class="dim-label">聘期总收入预估</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <span class="fw-600" :class="{ best: it.totalIncome === maxIncome }">
                    ¥{{ it.totalIncome.toLocaleString() }}
                  </span>
                  <span v-if="it.totalIncome === maxIncome && items.length > 1" class="f-pill f-pill-blue ml-6">总酬最高</span>
                </td>
              </tr>

              <tr>
                <td class="dim-label">每周工时 / 聘期</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <strong>{{ it.job.weeklyHours }}h/周</strong> · 共 {{ it.job.weeks }} 周
                </td>
              </tr>

              <tr>
                <td class="dim-label">跨岗工时合规试算</td>
                <td v-for="it in items" :key="it.job.id">
                  <div class="flex items-center gap-6 flex-wrap">
                    <span class="f-pill" :class="it.check.passed ? 'f-pill-success' : 'f-pill-error'">
                      {{ it.check.passed ? '全部合规 ✓' : '触发拦截 ✕' }}
                    </span>
                  </div>
                  <div class="text-xs text-muted mt-4 tabular-nums">
                    选此岗累计：{{ it.check.projectedHours }}h / {{ it.check.maxHours }}h
                  </div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">专业技能匹配度</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <div class="flex items-center gap-8">
                    <span class="fw-600" :class="{ best: it.match.score === maxMatch }">{{ it.match.score }}%</span>
                    <div class="f-progress-track" style="max-width: 90px;">
                      <div class="f-progress-fill" :class="it.match.score >= 80 ? 'success' : ''" :style="{ width: it.match.score + '%' }" />
                    </div>
                  </div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">国际生与语言门槛</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs">
                  <div>{{ it.job.allowInternational ? '面向国际生开放' : '仅限中国籍学生' }}</div>
                  <div class="text-muted mt-2">{{ it.job.extraFields?.languageRequirement || '无特殊语言限制' }}</div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">剩余名额 / 截止</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs tabular-nums">
                  <div>余 <strong>{{ Math.max(0, it.job.quota - it.job.hiredCount) }}</strong> / {{ it.job.quota }} 人</div>
                  <div class="text-muted mt-2">{{ it.job.deadline }} 截止</div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">核心工作安排</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs text-muted">
                  <template v-if="it.job.type === 'TA'">{{ it.job.extraFields?.scheduleText || '-' }}</template>
                  <template v-else-if="it.job.type === 'RA'">{{ it.job.extraFields?.researchDirection }}（{{ it.job.extraFields?.milestones?.length || 0 }}项里程碑）</template>
                  <template v-else>{{ it.job.extraFields?.officeDept }} · {{ it.job.extraFields?.dutyShift }}</template>
                </td>
              </tr>

              <tr>
                <td class="dim-label">快速操作</td>
                <td v-for="it in items" :key="it.job.id">
                  <button
                    type="button"
                    class="f-btn f-btn-primary f-btn-sm"
                    style="width: 100%;"
                    :disabled="it.check.alreadyApplied || it.check.quotaFull"
                    @click="$emit('apply', it.job)"
                  >
                    {{ it.check.alreadyApplied ? '已投递' : it.check.quotaFull ? '已招满' : '申请此岗位' }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <footer class="f-modal-footer justify-between">
        <button type="button" class="f-btn f-btn-sm" @click="$emit('clear')">清空对比栏</button>
        <button type="button" class="f-btn f-btn-sm" @click="$emit('close')">关闭</button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication, calcSkillMatch } from '@/services/ruleEngine'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  jobs: { type: Array, required: true }
})

defineEmits(['close', 'remove', 'clear', 'apply'])
const store = useAppStore()

const items = computed(() =>
  props.jobs.map((job) => ({
    job,
    totalIncome: Number(job.hourlyRate) * Number(job.weeklyHours) * Number(job.weeks),
    check: validateStudentApplication(store.db, store.currentUser, job),
    match: calcSkillMatch(store.currentUser, job)
  }))
)

const maxRate = computed(() => Math.max(0, ...items.value.map((i) => i.job.hourlyRate)))
const maxIncome = computed(() => Math.max(0, ...items.value.map((i) => i.totalIncome)))
const maxMatch = computed(() => Math.max(0, ...items.value.map((i) => i.match.score)))
</script>

<style scoped>
.compare-body {
  padding: 0;
}

.compare-table-wrap {
  width: 100%;
  overflow-x: auto;
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  min-width: 640px;
}

.compare-table th,
.compare-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-border-light);
  border-right: 1px solid var(--color-border-light);
  vertical-align: top;
}

.compare-table th:last-child,
.compare-table td:last-child {
  border-right: none;
}

.compare-table tbody tr:last-child td {
  border-bottom: none;
}

.dim-col,
.dim-label {
  width: 148px;
  background: var(--surface-subtle);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-muted);
}

.job-col {
  background: #fafcff;
  text-align: left;
}

.col-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
  line-height: 1.4;
}

.remove-col-btn {
  width: 24px;
  height: 24px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.remove-col-btn:hover {
  background: var(--color-error-bg);
  color: var(--color-error);
}

.big-val {
  font-size: 17px;
  font-weight: 600;
}

.best {
  color: var(--color-fluent-blue);
}

.ml-6 { margin-left: 6px; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }
.mt-8 { margin-top: 8px; }
</style>
