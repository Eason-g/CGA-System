<template>
  <div class="f-drawer-overlay" @click.self="$emit('close')">
    <aside class="f-drawer" role="dialog" aria-modal="true" :aria-label="job.title">
      <header class="detail-head">
        <div class="min-w-0">
          <div class="flex items-center gap-8 flex-wrap">
            <span class="f-type-tag" :class="job.type.toLowerCase()">{{ job.type }}</span>
            <span class="text-xs text-muted">{{ store.getDeptName(job.deptId) }}</span>
            <span v-if="job.allowInternational" class="f-pill f-pill-blue">国际生可报</span>
          </div>
          <h2 class="detail-title mt-8">{{ job.title }}</h2>
        </div>
        <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')" aria-label="关闭详情">
          <FluentIcon name="close" :size="18" />
        </button>
      </header>

      <div class="detail-body">
        <!-- Key Numbers Strip -->
        <div class="num-strip">
          <div class="num-cell">
            <div class="text-xs text-muted">岗位时薪</div>
            <div class="num-big text-blue tabular-nums">¥{{ job.hourlyRate }}/h</div>
          </div>
          <div class="num-cell">
            <div class="text-xs text-muted">每周工时</div>
            <div class="num-big tabular-nums">{{ job.weeklyHours }}h</div>
          </div>
          <div class="num-cell">
            <div class="text-xs text-muted">聘期总收入预估</div>
            <div class="num-big tabular-nums">¥{{ estTotalIncome.toLocaleString() }}</div>
          </div>
        </div>

        <!-- Pre-check Compliance Banner -->
        <div class="f-banner" :class="check.passed ? 'success' : 'caution'">
          <FluentIcon :name="check.passed ? 'check-circle' : 'alert'" :size="16" />
          <div class="text-xs">
            <div class="fw-600">
              {{ check.passed ? '您的资质与跨岗工时均符合本岗要求' : '本岗位存在前置合规拦截项（可申请特批）' }}
            </div>
            <div class="mt-4 tabular-nums">
              跨岗周工时试算：当前 {{ check.currentHours }}h + 本岗 {{ check.jobHours }}h = {{ check.projectedHours }}h / {{ check.maxHours }}h
            </div>
          </div>
        </div>

        <!-- Description -->
        <section class="detail-sec">
          <h3 class="sec-title">岗位职责</h3>
          <p class="text-sm text-muted mt-6" style="line-height: 1.7;">{{ job.description }}</p>
        </section>

        <!-- Type-Specific Information -->
        <section class="detail-sec">
          <h3 class="sec-title">
            {{ job.type === 'TA' ? '课程与排班信息' : job.type === 'RA' ? '科研课题与阶段里程碑' : '行政科室与值班安排' }}
          </h3>

          <div v-if="job.type === 'TA'" class="info-box mt-8">
            <div class="kv-row"><span>绑定课程</span><strong>{{ job.extraFields?.courseCode }} {{ job.extraFields?.courseName }}</strong></div>
            <div class="kv-row"><span>授课学期</span><strong>{{ job.extraFields?.semester }}</strong></div>
            <div class="kv-row"><span>语言门槛</span><strong>{{ job.extraFields?.languageRequirement || '无特殊限制' }}</strong></div>
            <div class="kv-row"><span>教学排班</span><strong>{{ job.extraFields?.scheduleText }}</strong></div>
          </div>

          <div v-else-if="job.type === 'RA'" class="info-box mt-8">
            <div class="kv-row"><span>科研项目</span><strong>{{ job.extraFields?.projectCode }} · {{ job.extraFields?.projectName }}</strong></div>
            <div class="kv-row"><span>研究方向</span><strong>{{ job.extraFields?.researchDirection }}</strong></div>
            <div v-if="job.extraFields?.milestones?.length" class="mt-8 pt-8 border-t">
              <div class="text-xs text-muted mb-6">阶段性里程碑：</div>
              <div v-for="m in job.extraFields.milestones" :key="m.id" class="text-xs mt-4">
                · {{ m.title }} <span class="text-subtle tabular-nums">（{{ m.dueDate }}）</span>
              </div>
            </div>
          </div>

          <div v-else class="info-box mt-8">
            <div class="kv-row"><span>行政科室</span><strong>{{ job.extraFields?.officeDept }}</strong></div>
            <div class="kv-row"><span>办公地点</span><strong>{{ job.extraFields?.location }}</strong></div>
            <div class="kv-row"><span>值班时段</span><strong>{{ job.extraFields?.dutyShift }}</strong></div>
          </div>
        </section>

        <!-- Basic Recruitment Info -->
        <section class="detail-sec">
          <h3 class="sec-title">招募信息</h3>
          <div class="info-box mt-8">
            <div class="kv-row"><span>负责导师 / 老师</span><strong>{{ store.getUser(job.mentorId)?.name || '-' }}</strong></div>
            <div class="kv-row"><span>剩余名额</span><strong class="tabular-nums">{{ Math.max(0, job.quota - job.hiredCount) }} / {{ job.quota }} 人</strong></div>
            <div class="kv-row"><span>聘期周数</span><strong class="tabular-nums">{{ job.weeks }} 周</strong></div>
            <div class="kv-row"><span>申请截止</span><strong class="tabular-nums">{{ job.deadline }}</strong></div>
          </div>
        </section>

        <!-- Skills -->
        <section v-if="job.skills?.length" class="detail-sec">
          <h3 class="sec-title">推荐技能标签</h3>
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
          <span>{{ isCompared ? '已加入对比' : '加入岗位对比' }}</span>
        </button>
        <button
          type="button"
          class="f-btn f-btn-primary flex-1"
          :disabled="check.alreadyApplied || check.quotaFull"
          @click="$emit('apply', job)"
        >
          {{ check.alreadyApplied ? '已投递该岗位' : check.quotaFull ? '名额已招满' : '立即一键申请' }}
        </button>
      </footer>
    </aside>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { validateStudentApplication } from '@/services/ruleEngine'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  job: { type: Object, required: true },
  isCompared: { type: Boolean, default: false }
})

defineEmits(['close', 'apply', 'toggle-compare'])
const store = useAppStore()

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
</style>
