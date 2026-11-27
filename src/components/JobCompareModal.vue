<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal xl" role="dialog" aria-modal="true" :aria-label="t('compare.dialogLabel')">
      <header class="f-modal-header">
        <div>
          <h2 class="text-sub">{{ t('compare.title', { n: items.length }) }}</h2>
          <p class="text-xs text-muted mt-2">{{ t('compare.subtitle') }}</p>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" :aria-label="t('common.close')">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="f-modal-body compare-body">
        <div class="compare-table-wrap">
          <table class="compare-table">
            <thead>
              <tr>
                <th class="dim-col">{{ t('compare.dimension') }}</th>
                <th v-for="it in items" :key="it.job.id" class="job-col">
                  <div class="flex items-center justify-between gap-6">
                    <span class="f-type-tag" :class="it.job.type.toLowerCase()">{{ it.job.type }}</span>
                    <button
                      type="button"
                      class="remove-col-btn"
                      :title="t('compare.removeCol')"
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
                <td class="dim-label">{{ t('detail.hourlyRate') }}</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <span class="big-val" :class="{ best: it.job.hourlyRate === maxRate }">
                    ¥{{ it.job.hourlyRate }}/h
                  </span>
                  <span v-if="it.job.hourlyRate === maxRate && items.length > 1" class="f-pill f-pill-success ml-6">{{ t('compare.highest') }}</span>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('detail.estIncome') }}</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <span class="fw-600" :class="{ best: it.totalIncome === maxIncome }">
                    ¥{{ it.totalIncome.toLocaleString() }}
                  </span>
                  <span v-if="it.totalIncome === maxIncome && items.length > 1" class="f-pill f-pill-blue ml-6">{{ t('compare.highestTotal') }}</span>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.hoursAndWeeks') }}</td>
                <td v-for="it in items" :key="it.job.id" class="tabular-nums">
                  <strong>{{ t('unit.hoursPerWeek', { n: it.job.weeklyHours }) }}</strong> · {{ t('compare.totalWeeks', { n: it.job.weeks }) }}
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.hoursCheck') }}</td>
                <td v-for="it in items" :key="it.job.id">
                  <div class="flex items-center gap-6 flex-wrap">
                    <span class="f-pill" :class="it.check.passed ? 'f-pill-success' : 'f-pill-error'">
                      {{ it.check.passed ? t('compare.compliant') : t('compare.blocked') }}
                    </span>
                  </div>
                  <div class="text-xs text-muted mt-4 tabular-nums">
                    {{ t('compare.projected', { total: it.check.projectedHours, max: it.check.maxHours }) }}
                  </div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.skillMatch') }}</td>
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
                <td class="dim-label">{{ t('compare.intlLang') }}</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs">
                  <div>{{ it.job.allowInternational ? t('compare.intlOpen') : t('compare.domesticOnly') }}</div>
                  <div class="text-muted mt-2">{{ it.job.extraFields?.languageRequirement || t('compare.noLangReq') }}</div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.quotaDeadline') }}</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs tabular-nums">
                  <div>{{ t('compare.leftPre') }}<strong>{{ Math.max(0, it.job.quota - it.job.hiredCount) }}</strong> {{ t('compare.leftPost', { total: it.job.quota }) }}</div>
                  <div class="text-muted mt-2">{{ t('jobs.deadlineOn', { date: it.job.deadline }) }}</div>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.arrangement') }}</td>
                <td v-for="it in items" :key="it.job.id" class="text-xs text-muted">
                  <template v-if="it.job.type === 'TA'">{{ it.job.extraFields?.scheduleText || '-' }}</template>
                  <template v-else-if="it.job.type === 'RA'">{{ it.job.extraFields?.researchDirection }} ({{ t('compare.milestoneCount', { n: it.job.extraFields?.milestones?.length || 0 }) }})</template>
                  <template v-else>{{ it.job.extraFields?.officeDept }} · {{ it.job.extraFields?.dutyShift }}</template>
                </td>
              </tr>

              <tr>
                <td class="dim-label">{{ t('compare.quickAction') }}</td>
                <td v-for="it in items" :key="it.job.id">
                  <button
                    type="button"
                    class="f-btn f-btn-primary f-btn-sm"
                    style="width: 100%;"
                    :disabled="it.check.alreadyApplied || it.check.quotaFull"
                    @click="$emit('apply', it.job)"
                  >
                    {{ it.check.alreadyApplied ? t('jobs.applied') : it.check.quotaFull ? t('jobs.full') : t('compare.apply') }}
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <footer class="f-modal-footer justify-between">
        <button type="button" class="f-btn f-btn-sm" @click="$emit('clear')">{{ t('compare.clear') }}</button>
        <button type="button" class="f-btn f-btn-sm" @click="$emit('close')">{{ t('common.close') }}</button>
      </footer>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication, calcSkillMatch } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  jobs: { type: Array, required: true }
})

defineEmits(['close', 'remove', 'clear', 'apply'])
const store = useAppStore()
const { t } = useI18n()

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

@media (max-width: 768px) {
  /* 对比表只在弹窗内横向滚动；维度列固定在左侧便于对照 */
  .compare-table { min-width: 560px; }
  .compare-table th, .compare-table td { padding: 12px; }
  .dim-col, .dim-label {
    width: 96px;
    font-size: 13px;
    position: sticky;
    left: 0;
    z-index: 1;
  }
  .remove-col-btn { width: 36px; height: 36px; }
  .compare-table .text-xs { font-size: 13px; }
}
</style>
