<template>
  <div class="f-drawer-overlay" @click.self="$emit('close')">
    <aside class="f-drawer" role="dialog" aria-modal="true" :aria-label="job.title">
      <header class="detail-head">
        <div class="min-w-0">
          <div class="flex items-center gap-8 flex-wrap">
            <span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span>
            <span class="text-xs text-muted">{{ store.getDeptName(job.deptId) }}</span>
            <span v-if="job.allowInternational" class="f-pill f-pill-blue">{{ t('jobs.intlEligible') }}</span>
          </div>
          <h2 class="detail-title mt-8">{{ job.title }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" :aria-label="t('detail.close')">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="detail-body">
        <!-- Key Numbers Strip -->
        <div class="num-strip">
          <div class="num-cell">
            <div class="text-xs text-muted">{{ t('detail.hourlyRate') }}</div>
            <div class="num-big text-blue tabular-nums">¥{{ job.hourlyRate }}/h</div>
          </div>
          <div class="num-cell">
            <div class="text-xs text-muted">{{ t('detail.weeklyHours') }}</div>
            <div class="num-big tabular-nums">{{ job.weeklyHours }}h</div>
          </div>
          <div class="num-cell">
            <div class="text-xs text-muted">{{ t('detail.estIncome') }}</div>
            <div class="num-big tabular-nums">¥{{ estTotalIncome.toLocaleString() }}</div>
          </div>
        </div>

        <!-- Pre-check Compliance Banner -->
        <div class="f-banner" :class="check.passed ? 'success' : 'caution'">
          <FluentIcon :name="check.passed ? 'check-circle' : 'alert'" :size="16" />
          <div class="text-xs">
            <div class="fw-600">
              {{ check.passed ? t('detail.checkOk') : t('detail.checkBlocked') }}
            </div>
            <div class="mt-4 tabular-nums">
              {{ t('detail.hoursCalc', { cur: check.currentHours, job: check.jobHours, total: check.projectedHours, max: check.maxHours }) }}
            </div>
          </div>
        </div>

        <!-- Description -->
        <section class="detail-sec">
          <h3 class="sec-title">{{ t('detail.duties') }}</h3>
          <p class="text-sm text-muted mt-6" style="line-height: 1.7;">{{ job.description }}</p>
        </section>

        <!-- Type-Specific Information -->
        <section class="detail-sec">
          <h3 class="sec-title">
            {{ job.type === 'TA' ? t('detail.secTA') : job.type === 'RA' ? t('detail.secRA') : t('detail.secGA') }}
          </h3>

          <div v-if="job.type === 'TA'" class="info-box mt-8">
            <div class="kv-row"><span>{{ t('detail.course') }}</span><strong>{{ job.extraFields?.courseCode }} {{ job.extraFields?.courseName }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.semester') }}</span><strong>{{ job.extraFields?.semester }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.langReq') }}</span><strong>{{ job.extraFields?.languageRequirement || t('detail.noLimit') }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.teachingSchedule') }}</span><strong>{{ job.extraFields?.scheduleText }}</strong></div>
          </div>

          <div v-else-if="job.type === 'RA'" class="info-box mt-8">
            <div class="kv-row"><span>{{ t('detail.project') }}</span><strong>{{ job.extraFields?.projectCode }} · {{ job.extraFields?.projectName }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.direction') }}</span><strong>{{ job.extraFields?.researchDirection }}</strong></div>
            <div v-if="job.extraFields?.milestones?.length" class="mt-8 pt-8 border-t">
              <div class="text-xs text-muted mb-6">{{ t('detail.milestones') }}</div>
              <div v-for="m in job.extraFields.milestones" :key="m.id" class="text-xs mt-4">
                · {{ m.title }} <span class="text-subtle tabular-nums">({{ m.dueDate }})</span>
              </div>
            </div>
          </div>

          <div v-else class="info-box mt-8">
            <div class="kv-row"><span>{{ t('detail.office') }}</span><strong>{{ job.extraFields?.officeDept }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.location') }}</span><strong>{{ job.extraFields?.location }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.dutyShift') }}</span><strong>{{ job.extraFields?.dutyShift }}</strong></div>
          </div>
        </section>

        <!-- Basic Recruitment Info -->
        <section class="detail-sec">
          <h3 class="sec-title">{{ t('detail.recruitment') }}</h3>
          <div class="info-box mt-8">
            <div class="kv-row"><span>{{ t('detail.mentor') }}</span><strong>{{ store.getUser(job.mentorId)?.name || '-' }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.quotaLeft') }}</span><strong class="tabular-nums">{{ t('detail.quotaLeftValue', { left: Math.max(0, job.quota - job.hiredCount), total: job.quota }) }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.weeks') }}</span><strong class="tabular-nums">{{ t('unit.weeks', { n: job.weeks }) }}</strong></div>
            <div class="kv-row"><span>{{ t('detail.deadline') }}</span><strong class="tabular-nums">{{ job.deadline }}</strong></div>
          </div>
        </section>

        <!-- Skills -->
        <section v-if="job.skills?.length" class="detail-sec">
          <h3 class="sec-title">{{ t('detail.skills') }}</h3>
          <div class="flex items-center gap-6 flex-wrap mt-8">
            <span v-for="sk in job.skills" :key="sk" class="skill-chip">{{ sk }}</span>
          </div>
        </section>
      </div>

      <footer class="detail-foot">
        <button
          type="button"
          class="f-btn"
          :class="{ 'is-compared': isCompared }"
          @click="$emit('toggle-compare', job.id)"
        >
          <FluentIcon name="compare" :size="15" />
          <span>{{ isCompared ? t('detail.inCompare') : t('detail.addCompare') }}</span>
        </button>
        <button
          type="button"
          class="f-btn f-btn-primary flex-1"
          :disabled="check.alreadyApplied || check.quotaFull"
          @click="$emit('apply', job)"
        >
          {{ check.alreadyApplied ? t('detail.applied') : check.quotaFull ? t('detail.full') : t('detail.apply') }}
        </button>
      </footer>
    </aside>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication } from '@/services/ruleEngine'
import { useI18n } from '@/i18n'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  job: { type: Object, required: true },
  isCompared: { type: Boolean, default: false }
})

defineEmits(['close', 'apply', 'toggle-compare'])
const store = useAppStore()
const { t } = useI18n()

const check = computed(() => validateStudentApplication(store.db, store.currentUser, props.job))
const estTotalIncome = computed(() => Number(props.job.hourlyRate) * Number(props.job.weeklyHours) * Number(props.job.weeks))
</script>

<style scoped>
.detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  padding: 22px 24px 16px;
  border-bottom: 1px solid var(--color-border-light);
}

.detail-title {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.35;
}

.detail-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.num-strip {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.num-cell {
  padding: 12px;
  border-radius: 10px;
  background: var(--surface-subtle);
  text-align: center;
}

.num-big {
  font-size: 19px;
  font-weight: 600;
  margin-top: 4px;
}

.sec-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.info-box {
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--surface-subtle);
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.kv-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 12.5px;
}

.kv-row span { color: var(--color-muted); flex-shrink: 0; }
.kv-row strong { text-align: right; }

.skill-chip {
  font-size: 12px;
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
  padding: 3px 10px;
  border-radius: 999px;
}

.detail-foot {
  padding: 16px 24px;
  border-top: 1px solid var(--color-border-light);
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  align-items: center;
  gap: 10px;
}

.is-compared {
  border-color: var(--color-fluent-blue);
  color: var(--color-fluent-blue);
  background: var(--color-blue-subtle);
}

.border-t { border-top: 1px solid var(--color-border-light); }
.pt-8 { padding-top: 8px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mb-6 { margin-bottom: 6px; }
.min-w-0 { min-width: 0; }
.flex-1 { flex: 1; }

@media (max-width: 768px) {
  .detail-head { padding: 14px 16px 12px; }
  .detail-title { font-size: 17px; }
  .detail-body { padding: 16px; gap: 16px; overscroll-behavior: contain; }
  .num-strip { gap: 6px; }
  .num-cell { padding: 10px 6px; min-width: 0; }
  .num-big { font-size: 16px; overflow-wrap: anywhere; }
  .sec-title { font-size: 15px; }
  .kv-row { font-size: 14px; }
  .kv-row strong { min-width: 0; overflow-wrap: anywhere; }
  .skill-chip { font-size: 13px; padding: 5px 12px; }
  .detail-foot { padding: 12px 16px calc(12px + env(safe-area-inset-bottom)); flex-wrap: wrap; }
  .detail-foot .f-btn { flex: 1 1 140px; }
}
</style>
