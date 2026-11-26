<template>
  <main class="sso-viewport" @click="univMenuOpen = false">
    <!-- Serene Fluent Luminous Background -->
    <div class="sso-bg" aria-hidden="true">
      <div class="bg-glow bg-glow-top" />
      <div class="bg-glow bg-glow-bottom" />
      <svg class="bg-ribbon" viewBox="0 0 1440 900" fill="none" preserveAspectRatio="xMidYMid slice">
        <path
          d="M-120 680C240 520 560 760 920 590C1180 468 1340 310 1560 220"
          stroke="url(#fluentArc)"
          stroke-width="1.5"
          opacity="0.45"
        />
        <path
          d="M-80 760C280 610 620 820 980 650C1220 535 1380 390 1580 310"
          stroke="url(#fluentArc)"
          stroke-width="1"
          opacity="0.28"
        />
        <defs>
          <linearGradient id="fluentArc" x1="0" y1="700" x2="1440" y2="220" gradientUnits="userSpaceOnUse">
            <stop stop-color="#0078d4" stop-opacity="0" />
            <stop offset="0.45" stop-color="#0078d4" stop-opacity="0.42" />
            <stop offset="1" stop-color="#38bdf8" stop-opacity="0" />
          </linearGradient>
        </defs>
      </svg>
    </div>

    <!-- Centered Single-Focus Multi-University SSO Card -->
    <section class="sso-center">
      <div class="sso-card">
        <!-- Brand Header + Inline University Switcher Pill -->
        <header class="card-brand">
          <div class="crest-badge" aria-hidden="true">
            <FluentIcon name="campus" :size="22" />
          </div>
          <h1 class="brand-name">{{ isEn ? 'Campus TA · RA · GA Platform' : '高校“三助”一体化平台' }}</h1>

          <!-- Lightweight Institution Selector Pill -->
          <div class="univ-anchor" @click.stop>
            <button
              type="button"
              class="univ-pill"
              :aria-expanded="univMenuOpen"
              @click="univMenuOpen = !univMenuOpen"
            >
              <span class="univ-dot" />
              <span class="truncate">{{ isEn ? store.currentUniversity.enName : store.currentUniversity.name }}</span>
              <FluentIcon :name="univMenuOpen ? 'chevron-up' : 'chevron-down'" :size="13" />
            </button>

            <div v-if="univMenuOpen" class="univ-popover" role="listbox">
              <div class="univ-pop-head">{{ isEn ? 'Select Institution' : '切换所属高校' }}</div>
              <div class="univ-pop-list">
                <button
                  v-for="u in store.universities"
                  :key="u.id"
                  type="button"
                  class="univ-pop-item"
                  :class="{ active: u.id === store.currentUniversityId }"
                  @click="selectUniversity(u.id)"
                >
                  <span class="truncate">{{ isEn ? u.enName : u.name }}</span>
                  <span class="univ-code tabular-nums">{{ u.code }}</span>
                </button>
              </div>
            </div>
          </div>
        </header>

        <!-- Clean Underline Mode Tabs -->
        <div class="mode-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            class="mode-tab"
            :class="{ active: loginTab === 'password' }"
            @click="loginTab = 'password'"
          >
            {{ isEn ? 'Account Login' : '账号登录' }}
          </button>
          <button
            type="button"
            role="tab"
            class="mode-tab"
            :class="{ active: loginTab === 'qr' }"
            @click="loginTab = 'qr'"
          >
            {{ isEn ? 'Campus App QR' : '扫码登录' }}
          </button>
        </div>

        <!-- Tab 1: Crisp Two-Field Form (ID + Password) -->
        <form v-if="loginTab === 'password'" class="login-form" @submit.prevent="handlePasswordLogin">
          <div class="field-group">
            <label class="field-label" for="sso-account">{{ isEn ? 'Student / Staff ID or Email' : '学工号 / 校园邮箱' }}</label>
            <div class="input-box">
              <FluentIcon name="user" :size="15" class="input-ic" />
              <input
                id="sso-account"
                v-model="account"
                type="text"
                class="clean-input"
                autocomplete="username"
                :placeholder="isEn ? 'Enter ID or campus email' : '请输入学号、工号或邮箱'"
                required
              />
            </div>
          </div>

          <div class="field-group">
            <label class="field-label" for="sso-password">{{ isEn ? 'Password' : '密码' }}</label>
            <div class="input-box">
              <FluentIcon name="lock" :size="15" class="input-ic" />
              <input
                id="sso-password"
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                class="clean-input has-trail"
                autocomplete="current-password"
                :placeholder="isEn ? 'Enter password' : '请输入统一认证密码'"
                required
              />
              <button
                type="button"
                class="eye-btn"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <FluentIcon :name="showPassword ? 'eye-off' : 'eye'" :size="15" />
              </button>
            </div>
          </div>

          <div class="form-aux">
            <label class="check-label">
              <input v-model="rememberMe" type="checkbox" class="clean-check" />
              <span>{{ isEn ? 'Keep signed in' : '保持登录状态' }}</span>
            </label>
            <button type="button" class="text-link" @click="showHelp = true">
              {{ isEn ? 'Forgot password?' : '忘记密码？' }}
            </button>
          </div>

          <div v-if="errorMsg" class="error-strip">
            <FluentIcon name="x-circle" :size="14" />
            <span>{{ errorMsg }}</span>
          </div>

          <button type="submit" class="submit-btn" :disabled="loggingIn">
            <span>{{ loggingIn ? (isEn ? 'Verifying...' : '正在验证...') : (isEn ? 'Sign In' : '登 录') }}</span>
          </button>
        </form>

        <!-- Tab 2: Campus App QR Code -->
        <div v-else class="qr-section">
          <div class="qr-frame" @click="simulateQrScan" title="点击确认授权登录">
            <svg viewBox="0 0 120 120" class="qr-svg" aria-label="智慧校园扫码登录">
              <rect width="120" height="120" fill="#ffffff" />
              <rect x="10" y="10" width="28" height="28" rx="3" fill="none" stroke="#181c22" stroke-width="4" />
              <rect x="18" y="18" width="12" height="12" rx="1.5" fill="#0078d4" />
              <rect x="82" y="10" width="28" height="28" rx="3" fill="none" stroke="#181c22" stroke-width="4" />
              <rect x="90" y="18" width="12" height="12" rx="1.5" fill="#181c22" />
              <rect x="10" y="82" width="28" height="28" rx="3" fill="none" stroke="#181c22" stroke-width="4" />
              <rect x="18" y="90" width="12" height="12" rx="1.5" fill="#181c22" />
              <g fill="#181c22">
                <rect x="46" y="12" width="6" height="6" />
                <rect x="58" y="12" width="6" height="6" />
                <rect x="70" y="12" width="6" height="6" />
                <rect x="46" y="24" width="12" height="6" />
                <rect x="64" y="24" width="6" height="6" />
                <rect x="46" y="36" width="6" height="6" />
                <rect x="58" y="36" width="18" height="6" />
                <rect x="12" y="46" width="6" height="12" />
                <rect x="24" y="46" width="12" height="6" />
                <rect x="24" y="58" width="6" height="6" />
                <rect x="12" y="68" width="18" height="6" />
                <rect x="34" y="68" width="6" height="6" />
                <rect x="82" y="46" width="12" height="6" />
                <rect x="100" y="46" width="8" height="6" />
                <rect x="88" y="58" width="6" height="12" />
                <rect x="100" y="58" width="8" height="14" />
                <rect x="46" y="82" width="12" height="6" />
                <rect x="64" y="82" width="6" height="12" />
                <rect x="76" y="82" width="12" height="6" />
                <rect x="94" y="82" width="14" height="6" />
                <rect x="46" y="94" width="6" height="14" />
                <rect x="58" y="98" width="14" height="10" />
                <rect x="78" y="94" width="10" height="6" />
                <rect x="94" y="96" width="14" height="12" />
              </g>
              <rect x="47" y="47" width="26" height="26" rx="6" fill="#0078d4" />
              <path d="M52 58.5L60 54l8 4.5-8 4.5-8-4.5z" fill="#ffffff" />
            </svg>
          </div>
          <p class="qr-hint">{{ isEn ? 'Scan with your Campus Mobile App' : '使用本校智慧校园 App / 企业微信扫码登录' }}</p>
          <button type="button" class="text-link mt-6" @click="refreshQr">
            {{ isEn ? 'Refresh QR Code' : '刷新二维码' }}
          </button>
        </div>

        <!-- Minimal Card Utility Footer -->
        <footer class="card-foot">
          <button type="button" class="foot-btn" @click="isEn = !isEn">
            <FluentIcon name="globe" :size="13" />
            <span>{{ isEn ? '简体中文' : 'English' }}</span>
          </button>
          <div class="foot-right">
            <button type="button" class="foot-btn" @click="showHelp = true">
              {{ isEn ? 'Help' : '帮助' }}
            </button>
            <span class="foot-dot">·</span>
            <button type="button" class="foot-btn accent" @click="showRoleModal = true">
              {{ isEn ? 'Demo Roles' : '体验账号' }}
            </button>
          </div>
        </footer>
      </div>

      <p class="page-copyright">
        {{ isEn ? 'Higher Education TA/RA/GA Platform · CAS / OAuth 2.0 / CARSI Federation' : '高校“三助”岗位一体化管理云平台 · 支持多校 CAS / OAuth 2.0 / CARSI 联邦认证' }}
      </p>
    </section>

    <!-- Sleek Role Switcher Modal -->
    <div v-if="showRoleModal" class="f-overlay" @click.self="showRoleModal = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="系统角色体验账号">
        <header class="f-modal-header">
          <div>
            <h2 class="text-sub">角色体验账号</h2>
            <p class="text-xs text-muted mt-2">覆盖学生端与管理端 6 类身份，支持任意已选高校进入体验</p>
          </div>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showRoleModal = false">
            <FluentIcon name="close" :size="17" />
          </button>
        </header>

        <div class="f-modal-body flex-col gap-8">
          <div v-for="u in store.presetUsers" :key="u.id" class="role-row">
            <div class="role-avatar" :class="u.portal">{{ u.name.slice(0, 1) }}</div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-8">
                <span class="fw-600 text-sm">{{ u.roleLabel }}</span>
                <span class="f-pill" :class="u.portal === 'student' ? 'f-pill-blue' : 'f-pill-neutral'">
                  {{ u.portal === 'student' ? '学生端' : '管理端' }}
                </span>
              </div>
              <div class="text-xs text-muted mt-2 truncate">
                {{ u.name }} · <span class="tabular-nums">{{ u.studentNo || u.username }}</span> · {{ shortTag(u) }}
              </div>
            </div>
            <div class="flex items-center gap-6 shrink-0">
              <button type="button" class="f-btn f-btn-sm" @click="fillAccount(u)">填入</button>
              <button type="button" class="f-btn f-btn-primary f-btn-sm" @click="quickLogin(u)">进入</button>
            </div>
          </div>
        </div>

        <footer class="f-modal-footer justify-between">
          <button type="button" class="f-btn f-btn-sm" @click="handleReset">
            <FluentIcon name="refresh" :size="13" />
            <span>重置初始数据</span>
          </button>
          <button type="button" class="f-btn f-btn-sm" @click="showRoleModal = false">关闭</button>
        </footer>
      </div>
    </div>

    <!-- Clean Help Modal -->
    <div v-if="showHelp" class="f-overlay" @click.self="showHelp = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="登录帮助">
        <header class="f-modal-header">
          <h2 class="text-sub">{{ isEn ? 'Federation SSO Help' : '多校统一身份认证说明' }}</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showHelp = false">
            <FluentIcon name="close" :size="17" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-14 text-xs text-muted" style="line-height: 1.7;">
          <div>
            <div class="fw-600 text-sm" style="color: var(--color-text);">多校接入机制</div>
            <p class="mt-4">本平台支持多所高校独立租户部署与统一联邦认证（CAS 3.0 / OAuth 2.0 / CARSI）。点击顶部院校胶囊切换所属高校，再使用本校学工号或校园邮箱登录。</p>
          </div>
          <div>
            <div class="fw-600 text-sm" style="color: var(--color-text);">国际学生合规提示</div>
            <p class="mt-4">系统自动对接本校国际处学籍与签证信息，国际生申请助教/助研岗位时将前置校验 X1 居留许可、语言证明与每周 20 小时工时红线。</p>
          </div>
        </div>
        <footer class="f-modal-footer">
          <button type="button" class="f-btn f-btn-primary f-btn-sm" @click="showHelp = false">知道了</button>
        </footer>
      </div>
    </div>
  </main>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
const router = useRouter()

const isEn = ref(false)
const loginTab = ref('password')
const account = ref('zhang.mingyuan')
const password = ref('Campus@2026')
const showPassword = ref(false)
const rememberMe = ref(true)
const loggingIn = ref(false)
const errorMsg = ref('')

const univMenuOpen = ref(false)
const showRoleModal = ref(false)
const showHelp = ref(false)

function selectUniversity(id) {
  store.setUniversity(id)
  univMenuOpen.value = false
}

function shortTag(u) {
  return {
    U_INTL_STU: 'X1签证 · 20h上限',
    U_CN_STU: '已任RA 12h/周',
    U_MENTOR: '计算机学院',
    U_DEPT_ADMIN: '院系数据隔离',
    U_INTL_OFFICER: '国际生会签/特批',
    U_SCHOOL_ADMIN: '全校财务与配置'
  }[u.id] || ''
}

async function handlePasswordLogin() {
  errorMsg.value = ''
  if (!account.value.trim()) {
    errorMsg.value = '请输入学工号或校园邮箱'
    return
  }
  loggingIn.value = true
  try {
    const user = await store.loginWithUsername(account.value.trim())
    if (user) {
      router.push(user.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
    } else {
      errorMsg.value = isEn.value ? 'Account not found. Check Demo Roles below.' : '未找到该学工号，请核对或点击右下角“体验账号”'
    }
  } finally {
    loggingIn.value = false
  }
}

function refreshQr() {
  store.notify('二维码已刷新', 'info')
}

async function simulateQrScan() {
  const user = await store.loginAs('U_MENTOR')
  if (user) router.push('/admin/dashboard')
}

function fillAccount(u) {
  account.value = u.studentNo || u.username
  password.value = 'Campus@2026'
  loginTab.value = 'password'
  errorMsg.value = ''
  showRoleModal.value = false
  store.notify(`已填入 ${u.roleLabel}（${u.name}）账号`, 'info')
}

async function quickLogin(u) {
  showRoleModal.value = false
  const user = await store.loginAs(u.id)
  if (user) router.push(user.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
}

async function handleReset() {
  await store.resetDemoData()
}
</script>

<style scoped>
.sso-viewport {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background-color: #f3f6fb;
  overflow: hidden;
}

.sso-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(90px);
}

.bg-glow-top {
  width: 640px;
  height: 640px;
  top: -220px;
  right: -120px;
  background: rgba(0, 120, 212, 0.14);
}

.bg-glow-bottom {
  width: 580px;
  height: 580px;
  bottom: -220px;
  left: -140px;
  background: rgba(56, 189, 248, 0.12);
}

.bg-ribbon {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.sso-center {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 416px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.sso-card {
  width: 100%;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(28px) saturate(150%);
  -webkit-backdrop-filter: blur(28px) saturate(150%);
  border: 1px solid rgba(255, 255, 255, 0.95);
  border-radius: 16px;
  padding: 34px 34px 22px;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 20px 48px -12px rgba(15, 23, 42, 0.10);
}

.card-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.crest-badge {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: var(--color-fluent-blue);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px -4px rgba(0, 120, 212, 0.36);
}

.brand-name {
  margin-top: 14px;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text);
  letter-spacing: -0.01em;
}

/* Top Lightweight Institution Pill */
.univ-anchor {
  position: relative;
  margin-top: 10px;
}

.univ-pill {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 280px;
  height: 28px;
  padding: 0 11px;
  border-radius: 999px;
  border: 1px solid var(--color-blue-border);
  background: var(--color-blue-subtle);
  color: var(--color-fluent-blue);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--motion-fast), border-color var(--motion-fast);
}

.univ-pill:hover {
  background: rgba(0, 120, 212, 0.13);
  border-color: var(--color-fluent-blue);
}

.univ-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-fluent-blue);
  flex-shrink: 0;
}

.univ-popover {
  position: absolute;
  top: 34px;
  left: 50%;
  transform: translateX(-50%);
  width: 276px;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(24px);
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  padding: 8px;
  z-index: 40;
  text-align: left;
  animation: fadeIn 140ms ease-out;
}

.univ-pop-head {
  padding: 4px 10px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-subtle);
}

.univ-pop-list {
  max-height: 224px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.univ-pop-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 10px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: 12.5px;
  color: var(--color-text);
  cursor: pointer;
  transition: background var(--motion-fast);
}

.univ-pop-item:hover { background: var(--surface-subtle); }
.univ-pop-item.active {
  background: var(--color-blue-subtle);
  color: var(--color-fluent-blue);
  font-weight: 600;
}

.univ-code {
  font-size: 11px;
  color: var(--color-subtle);
  flex-shrink: 0;
}

.mode-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-top: 22px;
  border-bottom: 1px solid var(--color-border-light);
}

.mode-tab {
  position: relative;
  height: 38px;
  border: none;
  background: transparent;
  font-size: 13.5px;
  font-weight: 400;
  color: var(--color-muted);
  cursor: pointer;
  transition: color var(--motion-fast);
}

.mode-tab:hover { color: var(--color-text); }
.mode-tab.active { color: var(--color-fluent-blue); font-weight: 600; }

.mode-tab.active::after {
  content: '';
  position: absolute;
  left: 22%;
  right: 22%;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-fluent-blue);
}

.login-form {
  margin-top: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-muted);
}

.input-box { position: relative; }

.input-ic {
  position: absolute;
  left: 13px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-subtle);
  pointer-events: none;
}

.clean-input {
  width: 100%;
  height: 42px;
  padding: 0 14px 0 38px;
  border: 1px solid rgba(15, 23, 42, 0.12);
  border-radius: 8px;
  background: #ffffff;
  font-size: 14px;
  color: var(--color-text);
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}

.clean-input.has-trail { padding-right: 40px; }
.clean-input:hover { border-color: rgba(15, 23, 42, 0.24); }
.clean-input:focus {
  outline: none;
  border-color: var(--color-fluent-blue);
  box-shadow: 0 0 0 3px rgba(0, 120, 212, 0.14);
}

.eye-btn {
  position: absolute;
  right: 6px;
  top: 50%;
  transform: translateY(-50%);
  width: 30px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.eye-btn:hover { background: var(--surface-subtle); color: var(--color-text); }

.form-aux {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: -2px;
}

.check-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  font-size: 12.5px;
  color: var(--color-muted);
  cursor: pointer;
}

.clean-check {
  width: 15px;
  height: 15px;
  accent-color: var(--color-fluent-blue);
  cursor: pointer;
}

.text-link {
  border: none;
  background: transparent;
  padding: 2px 4px;
  font-size: 12.5px;
  color: var(--color-fluent-blue);
  cursor: pointer;
}

.text-link:hover { text-decoration: underline; }

.error-strip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--color-error-bg);
  color: var(--color-error);
  font-size: 12.5px;
}

.submit-btn {
  margin-top: 4px;
  width: 100%;
  height: 44px;
  border: none;
  border-radius: 8px;
  background: var(--color-fluent-blue);
  color: #ffffff;
  font-size: 14.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  cursor: pointer;
  box-shadow: 0 4px 14px -2px rgba(0, 120, 212, 0.38);
  transition: background var(--motion-fast), box-shadow var(--motion-fast), transform var(--motion-fast);
}

.submit-btn:hover:not(:disabled) {
  background: var(--color-deep-blue);
  box-shadow: 0 6px 18px -2px rgba(0, 120, 212, 0.42);
}

.submit-btn:active:not(:disabled) { transform: translateY(0.5px); }
.submit-btn:disabled { opacity: 0.55; cursor: not-allowed; }

.qr-section {
  padding: 26px 0 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.qr-frame {
  width: 164px;
  height: 164px;
  padding: 10px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid var(--color-border-light);
  box-shadow: var(--shadow-xs);
  cursor: pointer;
  transition: border-color var(--motion-fast), transform var(--motion-fast);
}

.qr-frame:hover { border-color: var(--color-blue-border); transform: scale(1.02); }
.qr-svg { width: 100%; height: 100%; display: block; }
.qr-hint { margin-top: 14px; font-size: 13px; color: var(--color-muted); }

.card-foot {
  margin-top: 26px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.foot-right { display: flex; align-items: center; gap: 6px; }

.foot-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  background: transparent;
  padding: 4px 6px;
  border-radius: 5px;
  font-size: 12px;
  color: var(--color-subtle);
  cursor: pointer;
  transition: color var(--motion-fast), background var(--motion-fast);
}

.foot-btn:hover { color: var(--color-text); background: var(--surface-subtle); }
.foot-btn.accent { color: var(--color-fluent-blue); font-weight: 600; }
.foot-btn.accent:hover { background: var(--color-blue-subtle); }
.foot-dot { color: #cbd5e1; font-size: 12px; }

.page-copyright {
  margin-top: 20px;
  font-size: 12px;
  color: var(--color-subtle);
  text-align: center;
}

.role-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--surface-subtle);
  transition: background var(--motion-fast);
}

.role-row:hover { background: #edf3fa; }

.role-avatar {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-avatar.student { background: var(--color-blue-subtle); color: var(--color-fluent-blue); }
.role-avatar.admin { background: rgba(15, 23, 42, 0.08); color: var(--color-text); }

.min-w-0 { min-width: 0; }
.flex-1 { flex: 1; }
.shrink-0 { flex-shrink: 0; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }
.mt-6 { margin-top: 6px; }

@media (max-width: 480px) {
  .sso-card { padding: 26px 20px 18px; }
}
</style>
