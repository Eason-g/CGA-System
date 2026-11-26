<template>
  <div class="flex-col gap-20">
    <header class="page-header">
      <div>
        <h1 class="text-title">合规材料核验</h1>
        <div class="text-xs text-muted mt-4">
          学生上传的语言证明、签证/居留许可材料在此核验；语言证明核验通过后才计入岗位申请的语言校验。
        </div>
      </div>
      <div class="f-segmented">
        <button type="button" :class="{ active: statusFilter === 'PENDING' }" @click="setFilter('PENDING')">待核验</button>
        <button type="button" :class="{ active: statusFilter === '' }" @click="setFilter('')">全部</button>
      </div>
    </header>

    <!-- P2 B.2：学籍系统同步（模拟） -->
    <SisSyncPanel />

    <div v-if="!isHttpMode" class="f-banner caution">
      <FluentIcon name="alert" :size="16" />
      <span>该功能需要连接后端</span>
    </div>

    <section v-else class="acrylic-card table-card">
      <div v-if="loading" class="p-16 text-xs text-muted">加载中…</div>
      <div v-else-if="items.length" class="f-table-wrap">
        <table class="f-table">
          <thead>
            <tr>
              <th>学生</th>
              <th>材料</th>
              <th>学籍当前登记</th>
              <th>上传时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="it in items" :key="it.attachment.id">
              <td>
                <div class="fw-600">{{ it.student.name }}</div>
                <div class="text-xs text-muted tabular-nums">{{ it.student.studentNo }} · {{ store.getDeptShortName(it.student.deptId) }}</div>
              </td>
              <td>
                <div class="flex items-center gap-6">
                  <span class="f-pill f-pill-neutral">{{ ATTACHMENT_KIND_MAP[it.attachment.kind] }}</span>
                  <button type="button" class="f-btn-link text-xs truncate att-name" @click="download(it.attachment)">
                    {{ it.attachment.originalName }}
                  </button>
                </div>
              </td>
              <td class="text-xs">
                <template v-if="it.attachment.kind === 'LANG_CERT'">
                  {{ it.student.languageCert || '未登记' }}（等效 {{ it.student.languageLevel ?? '-' }}）
                </template>
                <template v-else>
                  {{ it.student.visaType || '未登记' }} · 有效期至 {{ it.student.visaExpiry || '未登记' }}
                </template>
              </td>
              <td class="text-xs text-muted tabular-nums">{{ it.attachment.uploadedAt }}</td>
              <td>
                <span class="f-pill" :class="`f-pill-${LANG_CERT_STATUS_MAP[it.attachment.verifyStatus]?.tone || 'neutral'}`">
                  {{ LANG_CERT_STATUS_MAP[it.attachment.verifyStatus]?.label || '-' }}
                </span>
                <div v-if="it.attachment.verifyOpinion" class="text-xs text-muted mt-4 opinion">{{ it.attachment.verifyOpinion }}</div>
              </td>
              <td>
                <button
                  v-if="it.attachment.verifyStatus === 'PENDING'"
                  type="button"
                  class="f-btn f-btn-primary f-btn-sm"
                  @click="openVerify(it)"
                >
                  核验
                </button>
                <span v-else class="text-xs text-subtle">已处理</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <EmptyState v-else icon="shield" :description="statusFilter === 'PENDING' ? '暂无待核验材料' : '暂无材料'" />
    </section>

    <!-- 核验弹窗 -->
    <div v-if="current" class="f-overlay" @click.self="current = null">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="核验合规材料">
        <header class="f-modal-header">
          <div>
            <div class="text-xs text-muted">{{ ATTACHMENT_KIND_MAP[current.attachment.kind] }} · {{ current.attachment.id }}</div>
            <h2 class="text-sub mt-4">{{ current.student.name }}</h2>
          </div>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" aria-label="关闭" @click="current = null">
            <FluentIcon name="close" :size="18" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-12">
          <div class="flex items-center gap-8 text-sm">
            <span class="truncate">{{ current.attachment.originalName }}</span>
            <button type="button" class="f-btn f-btn-sm" @click="download(current.attachment)">下载查看原件</button>
          </div>
          <template v-if="current.attachment.kind === 'LANG_CERT'">
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">证书名称（可修正）</label>
                <input v-model="form.languageCert" type="text" class="f-input" placeholder="如 IELTS 7.0" />
              </div>
              <div class="f-field">
                <label class="f-label">等效分（雅思口径，可修正）</label>
                <input v-model="form.languageLevel" type="number" min="0" max="9" step="0.5" class="f-input tabular-nums" />
              </div>
            </div>
          </template>
          <template v-else>
            <div class="grid-2">
              <div class="f-field">
                <label class="f-label">签证类型（可修正）</label>
                <input v-model="form.visaType" type="text" class="f-input" />
              </div>
              <div class="f-field">
                <label class="f-label">有效期至（可修正）</label>
                <input v-model="form.visaExpiry" type="date" class="f-input" />
              </div>
            </div>
          </template>
          <div class="f-field">
            <label class="f-label">核验意见（写入审计留痕）</label>
            <input v-model="form.opinion" type="text" class="f-input" placeholder="如：原件核对一致 / 证书编号查询不到" />
          </div>
        </div>
        <footer class="f-modal-footer">
          <button type="button" class="f-btn" @click="current = null">取消</button>
          <button type="button" class="f-btn f-btn-danger-outline" :disabled="busy" @click="submitVerify('REJECT')">不通过</button>
          <button type="button" class="f-btn f-btn-primary" :disabled="busy" @click="submitVerify('APPROVE')">核验通过</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useAppStore } from '@/stores/appStore'
import { LANG_CERT_STATUS_MAP } from '@/services/ruleEngine'
import { isHttpMode } from '@/services/http'
import { materialFinanceApi, ATTACHMENT_KIND_MAP } from '@/services/materialFinanceApi'
import FluentIcon from '@/components/FluentIcon.vue'
import EmptyState from '@/components/EmptyState.vue'
import SisSyncPanel from '@/components/SisSyncPanel.vue'

const store = useAppStore()
const statusFilter = ref('PENDING')
const items = ref([])
const loading = ref(false)
const busy = ref(false)
const current = ref(null)
const form = reactive({ languageCert: '', languageLevel: '', visaType: '', visaExpiry: '', opinion: '' })

async function load() {
  if (!isHttpMode) return
  loading.value = true
  try {
    const res = await materialFinanceApi.listComplianceMaterials(statusFilter.value)
    if (res.code === 200) items.value = res.data || []
    else store.notify(res.message || '加载失败', 'error')
  } finally {
    loading.value = false
  }
}

function setFilter(v) {
  statusFilter.value = v
  load()
}

function openVerify(it) {
  current.value = it
  form.languageCert = it.student.languageCert || ''
  form.languageLevel = it.student.languageLevel ?? ''
  form.visaType = it.student.visaType || ''
  form.visaExpiry = it.student.visaExpiry || ''
  form.opinion = ''
}

async function submitVerify(decision) {
  const it = current.value
  const isLang = it.attachment.kind === 'LANG_CERT'
  const payload = { attachmentId: it.attachment.id, decision, opinion: form.opinion.trim() }
  if (decision === 'APPROVE') {
    if (isLang) {
      if (form.languageCert.trim()) payload.languageCert = form.languageCert.trim()
      if (form.languageLevel !== '' && form.languageLevel !== null) payload.languageLevel = Number(form.languageLevel)
    } else {
      if (form.visaType.trim()) payload.visaType = form.visaType.trim()
      if (form.visaExpiry) payload.visaExpiry = form.visaExpiry
    }
  }
  busy.value = true
  try {
    const res = await store.mutate(() => materialFinanceApi.verifyCompliance(it.student.id, payload))
    if (res.code === 200) {
      current.value = null
      await load()
    }
  } finally {
    busy.value = false
  }
}

async function download(a) {
  const res = await materialFinanceApi.downloadAttachment(a)
  if (res.code !== 200) store.notify(res.message || '下载失败', 'error')
}

onMounted(load)
</script>

<style scoped>
.page-header { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.table-card { overflow: hidden; }
.grid-2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 12px; }
.att-name { max-width: 220px; text-align: left; }
.opinion { max-width: 200px; white-space: normal; }
.mt-4 { margin-top: 4px; }

@media (max-width: 640px) {
  .grid-2 { grid-template-columns: 1fr; }
}
</style>
