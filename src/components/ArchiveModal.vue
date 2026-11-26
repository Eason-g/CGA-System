<template>
  <div class="f-overlay" @click.self="$emit('close')">
    <div class="f-modal xl archive-printable" role="dialog" aria-modal="true" aria-label="三助聘用全流程电子归档视图">
      <header class="f-modal-header no-print">
        <div>
          <div class="text-xs text-muted">全生命周期电子归档 · 可直接打印或另存为 PDF</div>
          <h2 class="text-sub mt-4">聘用电子档案（{{ contractId }}）</h2>
        </div>
        <div class="flex items-center gap-8">
          <button type="button" class="f-btn f-btn-primary" @click="printArchive">
            <FluentIcon name="print" :size="16" />
            <span>打印 / 导出 PDF</span>
          </button>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="$emit('close')">
            <FluentIcon name="close" :size="18" />
          </button>
        </div>
      </header>

      <div v-if="contract" class="f-modal-body archive-body">
        <div class="archive-banner">
          <div>
            <div class="text-xs text-muted">{{ store.schoolName }} · 高校“三助”（TA/RA/GA）一体化管理平台</div>
            <h1 class="archive-h1">三助岗位聘用全流程电子归档凭证</h1>
          </div>
          <div class="text-right text-xs text-muted tabular-nums">
            <div>归档编号：ARC-{{ contract.id }}</div>
            <div>生成日期：2026-11-20</div>
          </div>
        </div>

        <!-- 1. Basic & Contract -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">一、聘用基本信息与电子协议</h3>
          <div class="archive-kv">
            <div><span class="k">受聘学生：</span><strong>{{ student?.name }}</strong>（{{ student?.studentNo }} · {{ student?.degree }}）</div>
            <div><span class="k">国际生属性：</span><strong>{{ student?.isInternational ? `国际生（${student.visaType}）` : '中国籍学生' }}</strong></div>
            <div><span class="k">设岗院系：</span><strong>{{ store.getDeptName(contract.deptId) }}</strong></div>
            <div><span class="k">岗位名称：</span><strong>[{{ job?.type }}] {{ job?.title }}</strong></div>
            <div><span class="k">聘用周期：</span><strong class="tabular-nums">{{ contract.startDate }} ~ {{ contract.endDate }}</strong></div>
            <div><span class="k">约定工时与酬金：</span><strong class="tabular-nums">{{ contract.weeklyHours }}h/周 · ¥{{ contract.hourlyRate }}/h</strong></div>
            <div><span class="k">经费来源：</span><strong>{{ budget?.name }}（{{ budget?.code }}）</strong></div>
            <div><span class="k">协议签署时间：</span><strong class="tabular-nums">{{ contract.signedAt || '待签署' }}</strong></div>
          </div>

          <div class="signature-box mt-12">
            <div class="text-xs text-muted">乙方电子签章与合规声明确认：</div>
            <div class="flex items-center gap-16 mt-6">
              <img v-if="contract.signatureDataUrl" :src="contract.signatureDataUrl" alt="手写签名" class="sig-img" />
              <div v-else class="sig-text-stamp">{{ contract.signatureText || '未签署' }}</div>
              <div class="text-xs text-muted">
                <div>《工时合规红线及岗位履职承诺》：{{ contract.complianceAgreed ? '已勾选确认 ✓' : '未确认' }}</div>
                <div v-if="student?.isInternational">《国际生合法工时合规声明》：{{ contract.complianceAgreed ? '已强制签署确认 ✓' : '待签署' }}</div>
              </div>
            </div>
          </div>
        </section>

        <!-- 2. Application & Compliance -->
        <section v-if="application" class="archive-sec">
          <h3 class="archive-sec-title">二、申请与前置合规校验记录</h3>
          <div class="text-xs mb-8">
            申请单号：<span class="fw-600">{{ application.id }}</span> · 面试得分：<span class="fw-600">{{ application.score ?? '-' }}</span> · 特批标记：<span class="fw-600">{{ application.isSpecialApproval ? '是（已留痕）' : '否' }}</span>
          </div>
          <div class="archive-checks">
            <div v-for="c in application.complianceCheckResult?.checks || []" :key="c.key" class="text-xs">
              [{{ c.passed ? '✓' : '✕' }}] <strong>{{ c.label }}</strong>：{{ c.detail }}
            </div>
          </div>
        </section>

        <!-- 3. Timesheets -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">三、在岗工时审核明细（共 {{ timesheets.length }} 条，审核通过 {{ totalApprovedHours }} 小时）</h3>
          <table class="archive-table">
            <thead>
              <tr>
                <th>周次</th>
                <th>月份</th>
                <th>工时(h)</th>
                <th>工作日志与里程碑</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in timesheets" :key="t.id">
                <td class="tabular-nums">第 {{ t.weekNo }} 周</td>
                <td class="tabular-nums">{{ t.month }}</td>
                <td class="tabular-nums fw-600">{{ t.hours }}</td>
                <td>{{ t.workLog }}</td>
                <td>{{ t.status === 'APPROVED' ? '已通过' : t.status === 'REJECTED' ? '已驳回' : '待审核' }}</td>
              </tr>
              <tr v-if="!timesheets.length">
                <td colspan="5" class="text-muted">暂无工时记录</td>
              </tr>
            </tbody>
          </table>
        </section>

        <!-- 4. Evaluations & Payrolls -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">四、周期考核评价与薪酬发放记录</h3>
          <div class="archive-two-col">
            <div>
              <div class="text-xs fw-600 mb-6">考核评价（{{ evaluations.length }}）</div>
              <div v-for="ev in evaluations" :key="ev.id" class="archive-mini-card">
                <div class="flex justify-between text-xs fw-600">
                  <span>{{ ev.period }}</span>
                  <span>{{ ev.totalScore }} 分 · {{ ev.grade }}（{{ ev.coefficient }}x）</span>
                </div>
                <div class="text-xs text-muted mt-4">续聘资格：{{ ev.renewalEligible ? '可续聘' : '不予续聘' }} · {{ ev.comment }}</div>
              </div>
              <div v-if="!evaluations.length" class="text-xs text-muted">暂无考核评价</div>
            </div>

            <div>
              <div class="text-xs fw-600 mb-6">薪酬发放（{{ payrolls.length }}）</div>
              <div v-for="p in payrolls" :key="p.id" class="archive-mini-card">
                <div class="flex justify-between text-xs fw-600 tabular-nums">
                  <span>{{ p.month }}（{{ p.totalHours }}h × ¥{{ p.hourlyRate }} × {{ p.coefficient }}x）</span>
                  <span class="text-blue">¥{{ p.amount.toFixed(2) }}</span>
                </div>
                <div class="text-xs text-muted mt-4">
                  状态：{{ p.financeStatus === 'PAID' ? `已发放（凭证 ${p.voucherNo}）` : FINANCE_STATUS_MAP[p.financeStatus]?.label || '待核算' }}
                </div>
              </div>
              <div v-if="!payrolls.length" class="text-xs text-muted">暂无薪酬记录</div>
            </div>
          </div>
        </section>

        <!-- 5. Audit Chain -->
        <section class="archive-sec">
          <h3 class="archive-sec-title">五、全流程审计留痕链（{{ relatedLogs.length }} 条）</h3>
          <table class="archive-table">
            <thead>
              <tr>
                <th>时间</th>
                <th>操作人 / 角色</th>
                <th>操作节点</th>
                <th>审批意见 / 数值变更</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="log in relatedLogs" :key="log.id">
                <td class="tabular-nums">{{ log.timestamp }}</td>
                <td>{{ log.operatorName }}（{{ log.operatorRole }}）</td>
                <td class="fw-600">{{ log.action }}</td>
                <td>{{ log.opinion }}</td>
              </tr>
            </tbody>
          </table>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { FINANCE_STATUS_MAP } from '@/services/ruleEngine'
import FluentIcon from './FluentIcon.vue'

const props = defineProps({
  contractId: { type: String, required: true }
})

defineEmits(['close'])
const store = useAppStore()

const contract = computed(() => store.db.contracts.find((c) => c.id === props.contractId))
const student = computed(() => (contract.value ? store.getUser(contract.value.studentId) : null))
const job = computed(() => (contract.value ? store.getJob(contract.value.jobId) : null))
const budget = computed(() => (job.value ? store.getBudget(job.value.budgetId) : null))
const application = computed(() =>
  contract.value?.applicationId ? store.db.applications.find((a) => a.id === contract.value.applicationId) : null
)
const timesheets = computed(() => store.db.timesheets.filter((t) => t.contractId === props.contractId))
const totalApprovedHours = computed(() =>
  timesheets.value.filter((t) => t.status === 'APPROVED').reduce((s, t) => s + Number(t.hours), 0)
)
const evaluations = computed(() => store.db.evaluations.filter((e) => e.contractId === props.contractId))
const payrolls = computed(() => store.db.payrolls.filter((p) => p.contractId === props.contractId))

const relatedLogs = computed(() => {
  const ids = new Set([
    props.contractId,
    contract.value?.jobId,
    contract.value?.applicationId,
    ...timesheets.value.map((t) => t.id),
    ...evaluations.value.map((e) => e.id),
    ...payrolls.value.map((p) => p.id)
  ].filter(Boolean))
  return store.db.auditLogs.filter((l) => ids.has(l.targetId))
})

function printArchive() {
  window.print()
}
</script>

<style scoped>
.archive-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.archive-banner {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-bottom: 14px;
  border-bottom: 2px solid var(--color-fluent-blue);
}

.archive-h1 {
  font-size: 22px;
  margin-top: 4px;
}

.archive-sec-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-fluent-blue);
  margin-bottom: 10px;
  padding-bottom: 4px;
  border-bottom: 1px solid var(--color-border-light);
}

.archive-kv {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 16px;
  font-size: 13px;
}

.archive-kv .k {
  color: var(--color-muted);
}

.signature-box {
  padding: 12px 14px;
  background: #faf9f8;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
}

.sig-img {
  height: 54px;
  border: 1px solid var(--color-border-light);
  background: #ffffff;
  padding: 4px 8px;
}

.sig-text-stamp {
  font-family: Georgia, serif;
  font-style: italic;
  font-size: 20px;
  padding: 6px 16px;
  border: 1px solid var(--color-border);
  background: #ffffff;
}

.archive-checks {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  background: #faf9f8;
  border-radius: 4px;
}

.archive-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.archive-table th,
.archive-table td {
  border: 1px solid var(--color-border-light);
  padding: 6px 10px;
  text-align: left;
}

.archive-table th {
  background: #f3f2f1;
  color: var(--color-muted);
}

.archive-two-col {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.archive-mini-card {
  padding: 10px 12px;
  border: 1px solid var(--color-border-light);
  border-radius: 4px;
  margin-bottom: 8px;
}

.text-right { text-align: right; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }
.mb-6 { margin-bottom: 6px; }
.mb-8 { margin-bottom: 8px; }

@media (max-width: 720px) {
  .archive-kv,
  .archive-two-col {
    grid-template-columns: 1fr;
  }
}
</style>
