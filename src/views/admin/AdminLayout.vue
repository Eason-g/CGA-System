<template>
  <div class="admin-shell ambient-canvas" @click="roleMenuOpen = false">
    <!-- Mobile Top Bar (< 960px) -->
    <header class="mobile-topbar">
      <button type="button" class="f-btn f-btn-subtle f-btn-sm" @click="mobileNavOpen = true" aria-label="打开菜单">
        <FluentIcon name="menu" :size="19" />
      </button>
      <div class="fw-600 text-sm">三助管理工作台</div>
      <span class="f-pill f-pill-blue">{{ user?.roleLabel }}</span>
    </header>

    <div v-if="mobileNavOpen" class="mobile-backdrop" @click="mobileNavOpen = false" />

    <!-- Refined 252px Mica Sidebar with Grouped Sections -->
    <aside class="admin-sidebar" :class="{ open: mobileNavOpen }">
      <div class="sidebar-top">
        <div class="sidebar-brand">
          <div class="logo-box">
            <FluentIcon name="campus" :size="18" />
          </div>
          <div class="min-w-0 flex-1">
            <div class="fw-600 text-sm truncate">{{ store.currentUniversity.name }}</div>
            <div class="text-xs text-subtle truncate">三助平台 · {{ scopeLabel }}</div>
          </div>
          <button type="button" class="f-btn f-btn-subtle f-btn-sm mobile-close" @click="mobileNavOpen = false">
            <FluentIcon name="close" :size="16" />
          </button>
        </div>

        <nav class="sidebar-nav mt-20" aria-label="主导航">
          <div v-for="group in navGroups" :key="group.title" class="nav-group">
            <div class="nav-group-title">{{ group.title }}</div>
            <router-link
              v-for="item in group.items"
              :key="item.to"
              :to="item.to"
              class="nav-item"
              :class="{ active: isNavActive(item.to) }"
              @click="mobileNavOpen = false"
            >
              <span class="nav-indicator" />
              <FluentIcon :name="item.icon" :size="16" />
              <span class="nav-label">{{ item.label }}</span>
              <span v-if="item.badge > 0" class="nav-badge" :class="item.badgeTone">{{ item.badge }}</span>
            </router-link>
          </div>
        </nav>
      </div>

      <!-- Sleek Bottom Profile Trigger with Popover Role Switcher -->
      <div class="sidebar-bottom" @click.stop>
        <div v-if="roleMenuOpen" class="role-popover">
          <router-link to="/admin/profile" class="popover-item" @click="roleMenuOpen = false">
            <FluentIcon name="user" :size="14" class="text-blue" />
            <span class="flex-1 fw-600">进入个人主页</span>
          </router-link>
          <div class="popover-divider" />
          <div class="popover-header">快速切换演示身份</div>
          <button
            v-for="u in store.presetUsers"
            :key="u.id"
            type="button"
            class="popover-item"
            :class="{ current: u.id === user?.id }"
            @click="onSwitchRole(u.id)"
          >
            <span class="pop-dot" :class="u.portal" />
            <span class="flex-1 truncate">{{ u.roleLabel }} · {{ u.name }}</span>
            <FluentIcon v-if="u.id === user?.id" name="check" :size="13" class="text-blue" />
          </button>
          <div class="popover-divider" />
          <div class="popover-actions">
            <button type="button" class="popover-act-btn" @click="handleReset">
              <FluentIcon name="refresh" :size="13" />
              <span>重置数据</span>
            </button>
            <button type="button" class="popover-act-btn danger" @click="handleLogout">
              <FluentIcon name="logout" :size="13" />
              <span>退出登录</span>
            </button>
          </div>
        </div>

        <button type="button" class="profile-trigger" @click="roleMenuOpen = !roleMenuOpen">
          <div class="profile-avatar">{{ user?.name?.slice(0, 1) || 'A' }}</div>
          <div class="min-w-0 flex-1 text-left">
            <div class="fw-600 text-xs truncate">{{ user?.name }}</div>
            <div class="text-xs text-muted truncate">{{ user?.roleLabel }}</div>
          </div>
          <FluentIcon :name="roleMenuOpen ? 'chevron-down' : 'chevron-up'" :size="14" class="text-subtle" />
        </button>
      </div>
    </aside>

    <!-- Main Fluid Content Area -->
    <main class="admin-main">
      <div class="admin-content">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { filterJobsByRole, filterByRoleDept, scanAnomalies } from '@/services/ruleEngine'
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
const route = useRoute()
const router = useRouter()
const mobileNavOpen = ref(false)
const roleMenuOpen = ref(false)

const user = computed(() => store.currentUser)

const scopeLabel = computed(() => {
  if (!user.value) return '-'
  if (user.value.role === 'SCHOOL_ADMIN') return '全校视野'
  if (user.value.role === 'DEPT_ADMIN') return `${store.getDeptShortName(user.value.deptId)}隔离`
  if (user.value.role === 'INTL_OFFICER') return '国际生会签'
  if (user.value.role === 'MENTOR') return `${store.getDeptShortName(user.value.deptId)} · 导师`
  return '-'
})

const pendingJobsCount = computed(() => {
  if (!user.value) return 0
  const jobs = filterJobsByRole(store.db, user.value)
  if (user.value.role === 'DEPT_ADMIN') return jobs.filter((j) => j.status === 'PENDING_DEPT_REVIEW').length
  if (user.value.role === 'INTL_OFFICER') return jobs.filter((j) => j.status === 'PENDING_INTL_REVIEW').length
  if (user.value.role === 'SCHOOL_ADMIN') return jobs.filter((j) => j.status === 'PENDING_SCHOOL_REVIEW').length
  return 0
})

const pendingAppsCount = computed(() => {
  if (!user.value) return 0
  const apps = filterByRoleDept(store.db, store.db.applications, user.value, 'APPLICATION')
  if (user.value.role === 'INTL_OFFICER') return apps.filter((a) => a.status === 'PENDING_SPECIAL_APPROVAL').length
  if (user.value.role === 'MENTOR') return apps.filter((a) => ['SUBMITTED', 'SPECIAL_APPROVED', 'INTERVIEW_SCHEDULED'].includes(a.status)).length
  return 0
})

const warningCount = computed(() => (user.value ? scanAnomalies(store.db, user.value).length : 0))

const navGroups = computed(() => {
  const role = user.value?.role
  return [
    {
      title: '概览',
      items: [{ to: '/admin/dashboard', label: '工作台总览', icon: 'dashboard', badge: 0 }]
    },
    {
      title: '聘用与业务',
      items: [
        { to: '/admin/jobs', label: '岗位与审批', icon: 'briefcase', badge: pendingJobsCount.value, badgeTone: 'caution' },
        ...(role === 'MENTOR' || role === 'SCHOOL_ADMIN' || role === 'DEPT_ADMIN'
          ? [{ to: '/admin/jobs/publish', label: '发布新岗位', icon: 'plus', badge: 0 }]
          : []),
        {
          to: '/admin/applications',
          label: role === 'INTL_OFFICER' ? '国际生特批' : '选拔与特批',
          icon: 'users',
          badge: pendingAppsCount.value,
          badgeTone: role === 'INTL_OFFICER' ? 'caution' : 'blue'
        },
        { to: '/admin/timesheets', label: '工时与预警', icon: 'clock', badge: warningCount.value, badgeTone: 'error' },
        { to: '/admin/payroll', label: '薪酬与预算', icon: 'wallet', badge: 0 },
        { to: '/admin/evaluations', label: '考核评价', icon: 'award', badge: 0 }
      ]
    },
    {
      title: '治理与归档',
      items: [
        ...(role === 'SCHOOL_ADMIN' ? [{ to: '/admin/config', label: '可配置化中心', icon: 'settings', badge: 0 }] : []),
        { to: '/admin/reports', label: '报表与归档', icon: 'chart', badge: 0 },
        { to: '/admin/audit', label: '审计留痕', icon: 'history', badge: 0 },
        { to: '/admin/profile', label: '个人主页', icon: 'user', badge: 0 }
      ]
    }
  ]
})

function isNavActive(path) {
  if (path === '/admin/jobs') return route.path === '/admin/jobs'
  return route.path === path || route.path.startsWith(path + '/')
}

async function onSwitchRole(userId) {
  roleMenuOpen.value = false
  const switched = await store.loginAs(userId)
  if (switched) router.push(switched.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
}

async function handleReset() {
  roleMenuOpen.value = false
  await store.resetDemoData()
}

function handleLogout() {
  roleMenuOpen.value = false
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
.admin-shell {
  display: flex;
  min-height: 100vh;
}

.mobile-topbar,
.mobile-backdrop,
.mobile-close {
  display: none;
}

.admin-sidebar {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 252px;
  background: var(--color-acrylic-sidebar);
  backdrop-filter: blur(24px) saturate(140%);
  -webkit-backdrop-filter: blur(24px) saturate(140%);
  border-right: 1px solid var(--color-border-light);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 20px 14px 14px;
  z-index: 600;
}

.sidebar-top {
  flex: 1;
  overflow-y: auto;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 6px;
}

.logo-box {
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: 9px;
  background: var(--color-fluent-blue);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(0, 120, 212, 0.24);
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.nav-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-group-title {
  padding: 0 10px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-subtle);
  letter-spacing: 0.04em;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 11px;
  border-radius: var(--radius-nav);
  color: var(--color-muted);
  font-size: 13.5px;
  font-weight: var(--weight-regular);
  transition: background var(--motion-fast), color var(--motion-fast);
}

.nav-item:hover {
  background: rgba(15, 23, 42, 0.04);
  color: var(--color-text);
}

.nav-item.active {
  background: #ffffff;
  font-weight: var(--weight-semibold);
  color: var(--color-text);
  box-shadow: var(--shadow-xs);
}

.nav-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 16px;
  border-radius: 0 3px 3px 0;
  background: transparent;
}

.nav-item.active .nav-indicator {
  background: var(--color-fluent-blue);
}

.nav-item.active svg {
  color: var(--color-fluent-blue);
}

.nav-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.nav-badge {
  min-width: 19px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.nav-badge.blue { background: var(--color-blue-subtle); color: var(--color-fluent-blue); }
.nav-badge.caution { background: var(--color-caution-bg); color: var(--color-caution-text); }
.nav-badge.error { background: var(--color-error-bg); color: var(--color-error); }

/* Bottom User Profile + Floating Role Switcher */
.sidebar-bottom {
  position: relative;
  padding-top: 10px;
  border-top: 1px solid var(--color-border-light);
}

.profile-trigger {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  transition: background var(--motion-fast), border-color var(--motion-fast);
}

.profile-trigger:hover {
  background: rgba(255, 255, 255, 0.85);
  border-color: var(--color-border-light);
}

.profile-avatar {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-blue-subtle);
  color: var(--color-fluent-blue);
  font-size: 13px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.role-popover {
  position: absolute;
  bottom: 58px;
  left: 0;
  right: 0;
  background: rgba(255, 255, 255, 0.96);
  backdrop-filter: blur(28px);
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  padding: 8px;
  z-index: 700;
  animation: fadeIn 140ms ease-out;
}

.popover-header {
  padding: 4px 8px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-subtle);
}

.popover-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 8px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
  transition: background var(--motion-fast);
}

.popover-item:hover { background: var(--surface-subtle); }
.popover-item.current { background: var(--color-blue-subtle); font-weight: 600; }

.pop-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.pop-dot.student { background: var(--color-fluent-blue); }
.pop-dot.admin { background: #64748b; }

.popover-divider {
  height: 1px;
  background: var(--color-border-light);
  margin: 6px 0;
}

.popover-actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
}

.popover-act-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  height: 30px;
  border: none;
  border-radius: 6px;
  background: transparent;
  font-size: 12px;
  color: var(--color-muted);
  cursor: pointer;
}

.popover-act-btn:hover { background: var(--surface-subtle); color: var(--color-text); }
.popover-act-btn.danger:hover { background: var(--color-error-bg); color: var(--color-error); }

.admin-main {
  flex: 1;
  margin-left: 252px;
  min-width: 0;
  padding: 32px 36px 56px;
}

.admin-content {
  max-width: 1320px;
  margin: 0 auto;
}

.text-left { text-align: left; }
.min-w-0 { min-width: 0; }
.flex-1 { flex: 1; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 960px) {
  .admin-shell { flex-direction: column; }
  .mobile-topbar {
    position: sticky; top: 0; z-index: 500;
    display: flex; align-items: center; justify-content: space-between;
    height: 54px; padding: 0 16px;
    background: rgba(255, 255, 255, 0.88);
    backdrop-filter: blur(20px);
    border-bottom: 1px solid var(--color-border-light);
  }
  .mobile-backdrop { display: block; position: fixed; inset: 0; background: rgba(15, 23, 42, 0.28); z-index: 650; }
  .mobile-close { display: inline-flex; }
  .admin-sidebar {
    transform: translateX(-100%);
    transition: transform 220ms cubic-bezier(0.2, 0, 0, 1);
    z-index: 700;
    background: rgba(255, 255, 255, 0.96);
  }
  .admin-sidebar.open { transform: translateX(0); }
  .admin-main { margin-left: 0; padding: 20px 16px 40px; }
}
</style>
