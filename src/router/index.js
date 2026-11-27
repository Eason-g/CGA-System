import { createRouter, createWebHashHistory } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { isHttpMode } from '@/services/http'
import { t, locale } from '@/i18n'
import { watch } from 'vue'

import LoginView from '@/views/LoginView.vue'
import AdminLayout from '@/views/admin/AdminLayout.vue'
import AdminDashboard from '@/views/admin/AdminDashboard.vue'
import JobListView from '@/views/admin/JobListView.vue'
import JobPublishView from '@/views/admin/JobPublishView.vue'
import ApplicationReviewView from '@/views/admin/ApplicationReviewView.vue'
import TimesheetAuditView from '@/views/admin/TimesheetAuditView.vue'
import ComplianceReviewView from '@/views/admin/ComplianceReviewView.vue'
import PayrollView from '@/views/admin/PayrollView.vue'
import EvaluationView from '@/views/admin/EvaluationView.vue'
import ConfigCenterView from '@/views/admin/ConfigCenterView.vue'
import ReportsArchiveView from '@/views/admin/ReportsArchiveView.vue'
import AuditLogView from '@/views/admin/AuditLogView.vue'

import StudentLayout from '@/views/student/StudentLayout.vue'
import JobHallView from '@/views/student/JobHallView.vue'
import MyApplicationsView from '@/views/student/MyApplicationsView.vue'
import ContractsTimesheetsView from '@/views/student/ContractsTimesheetsView.vue'
import MyPayrollView from '@/views/student/MyPayrollView.vue'
import ProfileView from '@/views/ProfileView.vue'
import FeedbackView from '@/views/FeedbackView.vue'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: LoginView,
    meta: { public: true, titleKey: 'route.login' }
  },
  {
    // P1：服务对象评价公开页（无需登录，凭评价码评价）
    path: '/feedback',
    name: 'Feedback',
    component: FeedbackView,
    meta: { public: true, titleKey: 'route.feedback' }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { portal: 'admin' },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: AdminDashboard, meta: { titleKey: 'route.adminDashboard' } },
      { path: 'jobs', name: 'AdminJobs', component: JobListView, meta: { titleKey: 'route.adminJobs' } },
      { path: 'jobs/publish', name: 'AdminJobPublish', component: JobPublishView, meta: { titleKey: 'route.adminJobPublish' } },
      { path: 'applications', name: 'AdminApplications', component: ApplicationReviewView, meta: { titleKey: 'route.adminApplications' } },
      { path: 'compliance', name: 'AdminCompliance', component: ComplianceReviewView, meta: { titleKey: 'route.adminCompliance', roles: ['INTL_OFFICER', 'SCHOOL_ADMIN'] } },
      { path: 'timesheets', name: 'AdminTimesheets', component: TimesheetAuditView, meta: { titleKey: 'route.adminTimesheets' } },
      { path: 'payroll', name: 'AdminPayroll', component: PayrollView, meta: { titleKey: 'route.adminPayroll' } },
      { path: 'evaluations', name: 'AdminEvaluations', component: EvaluationView, meta: { titleKey: 'route.adminEvaluations' } },
      { path: 'config', name: 'AdminConfig', component: ConfigCenterView, meta: { titleKey: 'route.adminConfig', roles: ['SCHOOL_ADMIN'] } },
      { path: 'reports', name: 'AdminReports', component: ReportsArchiveView, meta: { titleKey: 'route.adminReports' } },
      { path: 'audit', name: 'AdminAudit', component: AuditLogView, meta: { titleKey: 'route.adminAudit' } },
      { path: 'profile', name: 'AdminProfile', component: ProfileView, meta: { titleKey: 'route.profile' } }
    ]
  },
  {
    path: '/student',
    component: StudentLayout,
    meta: { portal: 'student' },
    redirect: '/student/jobs',
    children: [
      { path: 'jobs', name: 'StudentJobs', component: JobHallView, meta: { titleKey: 'route.studentJobs' } },
      { path: 'applications', name: 'StudentApplications', component: MyApplicationsView, meta: { titleKey: 'route.studentApplications' } },
      { path: 'contracts', name: 'StudentContracts', component: ContractsTimesheetsView, meta: { titleKey: 'route.studentContracts' } },
      { path: 'payroll', name: 'StudentPayroll', component: MyPayrollView, meta: { titleKey: 'route.studentPayroll' } },
      { path: 'profile', name: 'StudentProfile', component: ProfileView, meta: { titleKey: 'route.profile' } }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/login'
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach(async (to) => {
  const store = useAppStore()
  // http 模式：等待用 token 恢复登录完成，避免刷新页面时误跳登录页（mock 模式立即完成）
  await store.restoreSession()
  const user = store.currentUser

  if (to.meta.public) {
    // http 模式：已登录（如刷新后 token 恢复成功）访问登录页时直接进入对应门户
    if (isHttpMode && to.name === 'Login' && user) {
      return { path: user.portal === 'student' ? '/student/jobs' : '/admin/dashboard' }
    }
    return true
  }

  if (!user) {
    return { name: 'Login' }
  }

  if (to.meta.portal === 'admin' && user.portal !== 'admin') {
    return { path: '/student/jobs' }
  }

  if (to.meta.portal === 'student' && user.portal !== 'student') {
    return { path: '/admin/dashboard' }
  }

  if (to.meta.roles && !to.meta.roles.includes(user.role)) {
    store.notify(t('route.noPermission'), 'caution')
    return { path: '/admin/dashboard' }
  }

  return true
})

// 浏览器标签页标题：路由 meta.titleKey 对应词条，切换语言时同步更新
function updateDocumentTitle(route) {
  if (typeof document === 'undefined') return
  const key = route?.meta?.titleKey
  document.title = key ? `${t(key)} · ${t('app.name')}` : t('app.name')
}
router.afterEach((to) => updateDocumentTitle(to))
watch(locale, () => updateDocumentTitle(router.currentRoute.value))

export default router
