<template>
  <div class="feedback-page">
    <div class="acrylic-card p-24 feedback-card flex-col gap-16">
      <header class="flex-col gap-4">
        <h1 class="text-title">服务评价</h1>
        <p class="text-xs text-muted">请输入助教 / 助管提供的 8 位评价码，匿名为其服务打分。无需登录。</p>
      </header>

      <form v-if="!info" class="flex-col gap-12" @submit.prevent="lookup">
        <div class="f-field">
          <label class="f-label">评价码</label>
          <input
            v-model="code"
            type="text"
            class="f-input code-input"
            maxlength="8"
            autocomplete="off"
            placeholder="如 7K2QXMPA"
          />
        </div>
        <div v-if="error" class="f-banner error"><span class="text-xs">{{ error }}</span></div>
        <button type="submit" class="f-btn f-btn-primary" :disabled="busy || code.trim().length !== 8">查询</button>
      </form>

      <template v-else>
        <div class="info-box flex-col gap-4">
          <div class="flex items-center gap-8 flex-wrap">
            <span v-if="info.jobType" class="f-type-tag" :class="info.jobType.toLowerCase()">{{ info.jobType }}</span>
            <span class="fw-600">{{ info.jobTitle }}</span>
          </div>
          <div class="text-sm">服务助理：{{ info.assistantName }}</div>
          <div class="text-xs text-muted">评价截止：{{ info.deadline }}</div>
        </div>

        <div v-if="submitted" class="f-banner success">
          <span class="text-sm">{{ submittedText }}</span>
        </div>
        <div v-else-if="!info.open" class="f-banner caution">
          <span class="text-sm">{{ info.closedReason || '本轮评价已结束' }}</span>
        </div>

        <form v-else class="flex-col gap-12" @submit.prevent="submit">
          <div class="f-field">
            <label class="f-label">服务满意度 <span class="req">*</span></label>
            <div class="star-row" role="radiogroup" aria-label="评分">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="star-btn"
                :class="{ on: n <= rating }"
                :aria-label="`${n} 星`"
                :aria-checked="rating === n"
                role="radio"
                @click="rating = n"
              >
                ★
              </button>
              <span class="text-xs text-muted ml-8">{{ ratingText }}</span>
            </div>
          </div>
          <div class="f-field">
            <label class="f-label">一句话评价（选填）</label>
            <textarea v-model="comment" class="f-textarea" rows="2" maxlength="200" placeholder="例如：答疑很耐心，反馈及时" />
            <div class="text-xs text-muted align-right">{{ comment.length }}/200</div>
          </div>
          <div v-if="error" class="f-banner error"><span class="text-xs">{{ error }}</span></div>
          <button type="submit" class="f-btn f-btn-primary" :disabled="busy || rating < 1">提交评价</button>
        </form>

        <button type="button" class="f-btn-link text-xs" @click="reset">换一个评价码</button>
      </template>

      <footer class="text-xs text-muted">评价匿名提交，助理本人只能看到平均分；同一浏览器每轮只能提交一次。</footer>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '@/services/api'

const CLIENT_KEY = 'CGA_FEEDBACK_CLIENT_ID'
const DONE_KEY = 'CGA_FEEDBACK_SUBMITTED'

const route = useRoute()
const code = ref('')
const info = ref(null)
const rating = ref(0)
const comment = ref('')
const busy = ref(false)
const error = ref('')
const submitted = ref(false)
const submittedText = ref('')

const RATING_TEXT = ['', '很不满意', '不满意', '一般', '满意', '非常满意']
const ratingText = computed(() => RATING_TEXT[rating.value] || '点击星星打分')

// 浏览器随机 ID：存在 localStorage，后端只保存它的 SHA-256，用于“每轮只能提交一次”
function clientId() {
  try {
    let id = localStorage.getItem(CLIENT_KEY)
    if (!id) {
      const bytes = new Uint8Array(16)
      crypto.getRandomValues(bytes)
      id = 'fb-' + Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
      localStorage.setItem(CLIENT_KEY, id)
    }
    return id
  } catch {
    // 无法使用 localStorage（如隐私模式）时退回本次页面内的随机 ID
    return 'fb-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
  }
}

function submittedCodes() {
  try {
    return JSON.parse(localStorage.getItem(DONE_KEY) || '[]')
  } catch {
    return []
  }
}

function markSubmitted(c) {
  try {
    const list = submittedCodes()
    if (!list.includes(c)) list.push(c)
    localStorage.setItem(DONE_KEY, JSON.stringify(list.slice(-50)))
  } catch {
    // 忽略
  }
}

async function lookup() {
  error.value = ''
  busy.value = true
  try {
    const c = code.value.trim().toUpperCase()
    const res = await api.getPublicFeedback({ code: c })
    if (res.code === 200 && res.data) {
      info.value = res.data
      code.value = c
      submitted.value = submittedCodes().includes(c)
      submittedText.value = '您已提交过本轮评价，感谢参与！'
    } else {
      error.value = res.message || '评价码无效'
    }
  } finally {
    busy.value = false
  }
}

async function submit() {
  error.value = ''
  busy.value = true
  try {
    const res = await api.submitPublicFeedback({
      code: code.value,
      rating: rating.value,
      comment: comment.value.trim(),
      clientId: clientId()
    })
    if (res.code === 200) {
      submitted.value = true
      submittedText.value = '评价已提交，感谢您的反馈！'
      markSubmitted(code.value)
    } else if (res.code === 409) {
      submitted.value = true
      submittedText.value = res.message
      markSubmitted(code.value)
    } else {
      error.value = res.message || '提交失败，请稍后再试'
    }
  } finally {
    busy.value = false
  }
}

function reset() {
  info.value = null
  code.value = ''
  rating.value = 0
  comment.value = ''
  error.value = ''
  submitted.value = false
}

onMounted(() => {
  const q = String(route.query.code || '').trim()
  if (q) {
    code.value = q.toUpperCase()
    lookup()
  }
})
</script>

<style scoped>
.feedback-page {
  min-height: 100vh;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 48px 16px;
  box-sizing: border-box;
}
.feedback-card {
  width: 100%;
  max-width: 440px;
}
.code-input {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 20px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  text-align: center;
}
.info-box {
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--color-blue-subtle);
}
.star-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.star-btn {
  font-size: 30px;
  line-height: 1;
  background: none;
  border: none;
  cursor: pointer;
  color: #c8c6c4;
  padding: 2px;
}
.star-btn.on {
  color: #d9a400;
}
.star-btn:focus-visible {
  outline: 2px solid var(--color-fluent-blue);
  border-radius: 4px;
}
.ml-8 { margin-left: 8px; }
.align-right { text-align: right; }
.gap-4 { gap: 4px; }
</style>
