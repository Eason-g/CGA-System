<template>
  <main class="sso-viewport">
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

    <!-- 居中登录卡片 -->
    <section class="sso-center">
      <div class="sso-card">
        <!-- 品牌与校名（校名来自后端配置 app.school-name） -->
        <header class="card-brand">
          <div class="crest-badge" aria-hidden="true">
            <FluentIcon name="campus" :size="22" />
          </div>
          <h1 class="brand-name">{{ isEn ? 'Campus TA · RA · GA Platform' : '高校“三助”一体化平台' }}</h1>
          <div class="school-name">
            <span class="univ-dot" />
            <span class="truncate">{{ store.schoolName }}</span>
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
            :class="{ active: loginTab === 'sso' }"
            @click="loginTab = 'sso'"
          >
            {{ isEn ? 'Unified Identity' : '统一身份认证' }}
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
                :placeholder="isEn ? 'Enter password' : '请输入密码'"
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

        <!-- Tab 2：统一身份认证（P2 B.1，演示环境为模拟 CAS） -->
        <div v-else class="sso-section">
          <p class="sso-desc">
            {{ store.site.sso?.displayName || '统一身份认证（演示环境为模拟 CAS）' }}：跳转到认证中心输入学工号和密码，
            认证通过后自动回到本系统并完成登录。
          </p>
          <div v-if="errorMsg" class="error-strip">
            <FluentIcon name="x-circle" :size="14" />
            <span>{{ errorMsg }}</span>
          </div>
          <button type="button" class="submit-btn" :disabled="ssoBusy || !ssoAvailable" @click="startSsoLogin">
            <span>{{ ssoBusy ? '正在处理统一认证...' : '统一身份认证登录' }}</span>
          </button>
          <p v-if="!ssoAvailable" class="sso-note">
            {{ isHttpMode ? '统一身份认证未启用' : '该功能需要连接后端（当前为 mock 演示模式）' }}
          </p>
          <p v-else class="sso-note">模拟认证中心仅供演示，不连接任何真实学校的统一身份认证系统。</p>
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
        {{ isEn ? 'TA/RA/GA Platform · Unified identity (mock CAS in demo)' : '高校“三助”岗位一体化管理平台 · 统一身份认证（演示环境为模拟 CAS）' }}
      </p>
    </section>

    <!-- Sleek Role Switcher Modal -->
    <div v-if="showRoleModal" class="f-overlay" @click.self="showRoleModal = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="系统角色体验账号">
        <header class="f-modal-header">
          <div>
            <h2 class="text-sub">角色体验账号</h2>
            <p class="text-xs text-muted mt-2">覆盖学生端与管理端 6 类身份，使用演示密码登录</p>
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
          <div class="flex items-center gap-8">
            <span v-if="isHttpMode" class="demo-pwd-note">演示密码 {{ DEMO_PASSWORD }}</span>
            <button type="button" class="f-btn f-btn-sm" @click="showRoleModal = false">关闭</button>
          </div>
        </footer>
      </div>
    </div>

    <!-- Clean Help Modal -->
    <div v-if="showHelp" class="f-overlay" @click.self="showHelp = false">
      <div class="f-modal" role="dialog" aria-modal="true" aria-label="登录帮助">
        <header class="f-modal-header">
          <h2 class="text-sub">{{ isEn ? 'Sign-in Help' : '登录说明' }}</h2>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="showHelp = false">
            <FluentIcon name="close" :size="17" />
          </button>
        </header>
        <div class="f-modal-body flex-col gap-14 text-xs text-muted" style="line-height: 1.7;">
          <div>
            <div class="fw-600 text-sm" style="color: var(--color-text);">登录方式</div>
            <p class="mt-4">“账号登录”使用学工号、用户名或校园邮箱加密码；“统一身份认证”跳转到认证中心登录后，带一次性票据回到本系统。演示环境的认证中心为本系统自带的模拟 CAS，接入学校真实的统一身份认证需按其接口新增实现。</p>
          </div>
          <div>
            <div class="fw-600 text-sm" style="color: var(--color-text);">国际学生合规提示</div>
            <p class="mt-4">学籍状态与签证信息可由校级管理员、国际生办从学籍系统同步（演示环境为模拟学籍系统）；申请助教/助研岗位时前置校验学籍在读、居留许可、语言证明与每周工时上限。</p>
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
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import FluentIcon from '@/components/FluentIcon.vue'
import { isHttpMode, DEMO_PASSWORD } from '@/services/http'
import { integrationApi, ssoServiceUrl, consumePendingSsoTicket } from '@/services/integrationApi'

const store = useAppStore()
const router = useRouter()

const isEn = ref(false)
const loginTab = ref('password')
const account = ref('zhang.mingyuan')
const password = ref('')
const showPassword = ref(false)
const rememberMe = ref(true)
const loggingIn = ref(false)
const errorMsg = ref('')

const showRoleModal = ref(false)
const showHelp = ref(false)

// P2 B.1 统一身份认证
const ssoBusy = ref(false)
const ssoAvailable = computed(() => isHttpMode && Boolean(store.site.sso?.enabled))

function enterPortal(user) {
  router.push(user.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
}

// 跳到认证中心；回跳地址为当前页面（不含 hash），认证中心会在 # 之前追加 ?ticket=
async function startSsoLogin() {
  errorMsg.value = ''
  ssoBusy.value = true
  const res = await integrationApi.getSsoLoginUrl(ssoServiceUrl())
  if (res.code === 200 && res.data?.loginUrl) {
    window.location.href = res.data.loginUrl
    return
  }
  ssoBusy.value = false
  errorMsg.value = res.message || '统一身份认证暂不可用'
}

// 从认证中心回跳：main.js 已取出地址栏里的 ticket 并清掉，这里换取本系统 JWT
onMounted(async () => {
  const ticket = consumePendingSsoTicket()
  if (!ticket) return
  loginTab.value = 'sso'
  ssoBusy.value = true
  try {
    const user = await store.loginWithSsoTicket(ticket)
    if (user) enterPortal(user)
    else errorMsg.value = store.lastLoginError
  } finally {
    ssoBusy.value = false
  }
})

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
  if (isHttpMode && !password.value) {
    errorMsg.value = '请输入密码'
    return
  }
  loggingIn.value = true
  try {
    const user = await store.loginWithUsername(account.value.trim(), password.value)
    if (user) {
      router.push(user.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
    } else if (isHttpMode) {
      // 显示后端返回的错误文案（如“账号或密码错误”“无法连接服务器”）
      errorMsg.value = store.lastLoginError
    } else {
      errorMsg.value = isEn.value ? 'Account not found. Check Demo Roles below.' : '未找到该学工号，请核对或点击右下角“体验账号”'
    }
  } finally {
    loggingIn.value = false
  }
}

function fillAccount(u) {
  account.value = u.studentNo || u.username
  password.value = DEMO_PASSWORD
  loginTab.value = 'password'
  errorMsg.value = ''
  showRoleModal.value = false
  store.notify(`已填入 ${u.roleLabel}（${u.name}）账号`, 'info')
}

// “进入”：http 模式下由 store.loginAs 使用该账号 + 演示密码走正常登录
async function quickLogin(u) {
  showRoleModal.value = false
  errorMsg.value = ''
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



.univ-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-fluent-blue);
  flex-shrink: 0;
}







.school-name {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  max-width: 100%;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.sso-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 8px 0 4px;
}

.sso-desc { margin: 0; font-size: 13px; line-height: 1.7; color: var(--color-muted); }
.sso-note { margin: 0; font-size: 12px; color: var(--color-muted); text-align: center; }

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

.demo-pwd-note {
  font-size: 11px;
  color: var(--color-subtle);
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
