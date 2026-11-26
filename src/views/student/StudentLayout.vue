<template>
  <div class="student-shell ambient-canvas" @click="closeMenus">
    <!-- Floating Frosted Glass Header (60px) -->
    <header class="student-topbar">
      <div class="topbar-inner">
        <router-link to="/student/jobs" class="topbar-brand">
          <div class="logo-tile">
            <FluentIcon name="campus" :size="17" />
          </div>
          <div class="brand-stack">
            <span class="fw-600 text-sm">{{ store.currentUniversity.name }}</span>
            <span class="brand-mini">三助招聘门户</span>
          </div>
        </router-link>

        <!-- Clean Center Navigation Tabs -->
        <nav class="topbar-tabs" aria-label="学生端导航">
          <router-link
            v-for="tab in tabs"
            :key="tab.to"
            :to="tab.to"
            class="tab-link"
            :class="{ active: route.path === tab.to }"
          >
            <span>{{ tab.label }}</span>
            <span v-if="tab.badge > 0" class="tab-dot">{{ tab.badge }}</span>
          </router-link>
        </nav>

        <!-- Right: Notification Bell + Clean User Popover Menu -->
        <div class="topbar-right" @click.stop>
          <div class="menu-anchor">
            <button
              type="button"
              class="icon-btn"
              aria-label="通知"
              @click="toggleNotif"
            >
              <FluentIcon name="bell" :size="17" />
              <span v-if="store.unreadNotifications.length" class="bell-dot" />
            </button>

            <div v-if="showNotif" class="flyout-panel notif-panel">
              <div class="flex items-center justify-between pb-10 border-b">
                <span class="fw-600 text-xs">消息通知</span>
                <button type="button" class="f-btn-link text-xs" @click="markAllRead">全部已读</button>
              </div>
              <div v-if="store.userNotifications.length" class="notif-list">
                <div
                  v-for="n in store.userNotifications"
                  :key="n.id"
                  class="notif-row"
                  :class="{ unread: !n.read }"
                >
                  <div class="flex items-center justify-between gap-8">
                    <span class="fw-600 text-xs">{{ n.title }}</span>
                    <span class="text-xs text-subtle tabular-nums">{{ n.createdAt.slice(5, 10) }}</span>
                  </div>
                  <div class="text-xs text-muted mt-4">{{ n.content }}</div>
                </div>
              </div>
              <div v-else class="text-xs text-muted p-16 text-center">暂无新消息</div>
            </div>
          </div>

          <div class="menu-anchor">
            <button type="button" class="user-chip" @click="toggleUserMenu">
              <span class="avatar-dot">{{ user?.name?.slice(0, 1) || 'S' }}</span>
              <span class="user-name">{{ user?.name }}</span>
              <span v-if="user?.isInternational" class="f-pill f-pill-blue">X1</span>
              <FluentIcon name="chevron-down" :size="13" class="text-subtle" />
            </button>

            <div v-if="showUserMenu" class="flyout-panel user-panel">
              <router-link to="/student/profile" class="user-panel-head-link" @click="closeMenus">
                <div class="fw-600 text-sm">{{ user?.name }}</div>
                <div class="text-xs text-blue mt-2">查看我的个人主页 →</div>
              </router-link>
              <div class="menu-section-title">快速切换演示身份</div>
              <button
                v-for="u in store.presetUsers"
                :key="u.id"
                type="button"
                class="menu-item"
                :class="{ current: u.id === user?.id }"
                @click="onSwitchRole(u.id)"
              >
                <span class="m-dot" :class="u.portal" />
                <span class="flex-1 truncate">{{ u.roleLabel }} · {{ u.name }}</span>
                <FluentIcon v-if="u.id === user?.id" name="check" :size="13" class="text-blue" />
              </button>
              <div class="menu-divider" />
              <button type="button" class="menu-item danger" @click="handleLogout">
                <FluentIcon name="logout" :size="14" />
                <span>退出当前登录</span>
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Mobile Tabs -->
      <nav class="mobile-tabs">
        <router-link
          v-for="tab in tabs"
          :key="tab.to"
          :to="tab.to"
          class="mobile-tab-link"
          :class="{ active: route.path === tab.to }"
        >
          {{ tab.label }}
        </router-link>
      </nav>
    </header>

    <main class="student-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { api } from '@/services/api'
import FluentIcon from '@/components/FluentIcon.vue'

const store = useAppStore()
const route = useRoute()
const router = useRouter()

const showNotif = ref(false)
const showUserMenu = ref(false)

const user = computed(() => store.currentUser)
const pendingSignCount = computed(() =>
  store.db.contracts.filter((c) => c.studentId === user.value?.id && c.status === 'PENDING_SIGN').length
)

const tabs = computed(() => [
  { to: '/student/jobs', label: '岗位大厅', badge: 0 },
  { to: '/student/applications', label: '我的投递', badge: 0 },
  { to: '/student/contracts', label: '工时与合同', badge: pendingSignCount.value },
  { to: '/student/payroll', label: '薪酬记录', badge: 0 },
  { to: '/student/profile', label: '个人主页', badge: 0 }
])

function closeMenus() {
  showNotif.value = false
  showUserMenu.value = false
}

function toggleNotif() {
  showUserMenu.value = false
  showNotif.value = !showNotif.value
}

function toggleUserMenu() {
  showNotif.value = false
  showUserMenu.value = !showUserMenu.value
}

async function markAllRead() {
  if (!user.value) return
  await store.mutate(() => api.markNotificationsRead(user.value.id), false)
}

async function onSwitchRole(userId) {
  closeMenus()
  const switched = await store.loginAs(userId)
  if (switched) router.push(switched.portal === 'student' ? '/student/jobs' : '/admin/dashboard')
}

function handleLogout() {
  closeMenus()
  store.logout()
  router.push('/login')
}
</script>

<style scoped>
.student-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.student-topbar {
  position: sticky;
  top: 0;
  z-index: 600;
  background: rgba(255, 255, 255, 0.80);
  backdrop-filter: blur(24px) saturate(150%);
  -webkit-backdrop-filter: blur(24px) saturate(150%);
  border-bottom: 1px solid var(--color-border-light);
}

.topbar-inner {
  max-width: 1180px;
  height: 60px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.topbar-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--color-text);
  flex-shrink: 0;
}

.brand-stack {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-mini {
  font-size: 11px;
  color: var(--color-subtle);
}

.logo-tile {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: var(--color-fluent-blue);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px rgba(0, 120, 212, 0.24);
}

.topbar-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(15, 23, 42, 0.04);
  padding: 4px;
  border-radius: 10px;
}

.tab-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 14px;
  border-radius: 7px;
  color: var(--color-muted);
  font-size: 13.5px;
  font-weight: var(--weight-regular);
  transition: all var(--motion-fast);
}

.tab-link:hover {
  color: var(--color-text);
}

.tab-link.active {
  background: #ffffff;
  color: var(--color-fluent-blue);
  font-weight: var(--weight-semibold);
  box-shadow: var(--shadow-xs);
}

.tab-dot {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--color-error);
  color: #ffffff;
  font-size: 10.5px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.menu-anchor { position: relative; }

.icon-btn {
  position: relative;
  width: 36px;
  height: 36px;
  border-radius: 9px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--color-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background var(--motion-fast), color var(--motion-fast);
}

.icon-btn:hover {
  background: rgba(15, 23, 42, 0.05);
  color: var(--color-text);
}

.bell-dot {
  position: absolute;
  top: 8px;
  right: 9px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-error);
  box-shadow: 0 0 0 2px #ffffff;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 3px 10px 3px 4px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid var(--color-border-light);
  cursor: pointer;
  transition: border-color var(--motion-fast), box-shadow var(--motion-fast);
}

.user-chip:hover {
  border-color: var(--color-blue-border);
  box-shadow: var(--shadow-xs);
}

.avatar-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: var(--color-fluent-blue);
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flyout-panel {
  position: absolute;
  top: 44px;
  right: 0;
  background: rgba(255, 255, 255, 0.97);
  backdrop-filter: blur(28px);
  border: 1px solid var(--color-border-light);
  border-radius: 12px;
  box-shadow: var(--shadow-modal);
  z-index: 800;
  animation: fadeIn 140ms ease-out;
}

.notif-panel {
  width: 340px;
  max-height: 400px;
  overflow-y: auto;
  padding: 14px;
}

.notif-list {
  display: flex;
  flex-direction: column;
  margin-top: 6px;
}

.notif-row {
  padding: 10px 6px;
  border-bottom: 1px solid var(--color-border-light);
}

.notif-row:last-child { border-bottom: none; }
.notif-row.unread { padding-left: 10px; border-left: 2.5px solid var(--color-fluent-blue); }

.user-panel {
  width: 256px;
  padding: 8px;
}

.user-panel-head-link {
  display: block;
  padding: 8px 10px 10px;
  border-bottom: 1px solid var(--color-border-light);
  margin-bottom: 6px;
  border-radius: 7px;
  color: var(--color-text);
  transition: background var(--motion-fast);
}

.user-panel-head-link:hover {
  background: var(--surface-subtle);
  color: var(--color-text);
}

.menu-section-title {
  padding: 4px 10px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-subtle);
}

.menu-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border: none;
  border-radius: 7px;
  background: transparent;
  font-size: 12.5px;
  text-align: left;
  cursor: pointer;
  transition: background var(--motion-fast);
}

.menu-item:hover { background: var(--surface-subtle); }
.menu-item.current { background: var(--color-blue-subtle); font-weight: 600; }
.menu-item.danger { color: var(--color-error); }
.menu-item.danger:hover { background: var(--color-error-bg); }

.m-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
.m-dot.student { background: var(--color-fluent-blue); }
.m-dot.admin { background: #64748b; }

.menu-divider {
  height: 1px;
  background: var(--color-border-light);
  margin: 6px 0;
}

.mobile-tabs { display: none; }
.student-main { flex: 1; padding-bottom: 64px; }
.pb-10 { padding-bottom: 10px; }
.border-b { border-bottom: 1px solid var(--color-border-light); }
.text-center { text-align: center; }
.flex-1 { flex: 1; }
.truncate { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mt-2 { margin-top: 2px; }
.mt-4 { margin-top: 4px; }

@media (max-width: 960px) {
  .topbar-inner { padding: 0 16px; }
  .topbar-tabs, .user-name { display: none; }
  .mobile-tabs {
    display: flex;
    border-top: 1px solid var(--color-border-light);
    overflow-x: auto;
  }
  .mobile-tab-link {
    flex: 1;
    text-align: center;
    padding: 10px 8px;
    font-size: 13px;
    color: var(--color-muted);
    white-space: nowrap;
    border-bottom: 2px solid transparent;
  }
  .mobile-tab-link.active {
    color: var(--color-fluent-blue);
    font-weight: 600;
    border-bottom-color: var(--color-fluent-blue);
  }
}
</style>
