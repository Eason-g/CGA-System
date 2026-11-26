import { createRouter, createWebHashHistory } from 'vue-router'
import { useAppStore } from '@/stores/appStore'
import { isHttpMode } from '@/services/http'

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
    meta: { public: true }
  },
  {
    // P1：服务对象评价公开页（无需登录，凭评价码评价）
    path: '/feedback',
    name: 'Feedback',
    component: FeedbackView,
    meta: { public: true }
  },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { portal: 'admin' },
    redirect: '/admin/dashboard',
    children: [
      { path: 'dashboard', name: 'AdminDashboard', component: AdminDashboard, meta: { title: '工作台总览' } },
      { path: 'jobs', name: 'AdminJobs', component: JobListView, meta: { title: '岗位管理与审批' } },
      { path: 'jobs/publish', name: 'AdminJobPublish', component: JobPublishView, meta: { title: '发布新岗位' } },
      { path: 'applications', name: 'AdminApplications', component: ApplicationReviewView, meta: { title: '简历筛选与国际生特批' } },
      { path: 'compliance', name: 'AdminCompliance', component: ComplianceReviewView, meta: { title: '合规材料核验', roles: ['INTL_OFFICER', 'SCHOOL_ADMIN'] } },
      { path: 'timesheets', name: 'AdminTimesheets', component: TimesheetAuditView, meta: { title: '在岗工时与预警' } },
      { path: 'payroll', name: 'AdminPayroll', component: PayrollView, meta: { title: '薪酬核算与预算联动' } },
      { path: 'evaluations', name: 'AdminEvaluations', component: EvaluationView, meta: { title: '周期性考核评价' } },
      { path: 'config', name: 'AdminConfig', component: ConfigCenterView, meta: { title: '可配置化中心', roles: ['SCHOOL_ADMIN'] } },
      { path: 'reports', name: 'AdminReports', component: ReportsArchiveView, meta: { title: '多维报表与电子归档' } },
      { path: 'audit', name: 'AdminAudit', component: AuditLogView, meta: { title: '全流程审计留痕' } },
      { path: 'profile', name: 'AdminProfile', component: ProfileView, meta: { title: '个人主页' } }
    ]
  },
  {
    path: '/student',
    component: StudentLayout,
    meta: { portal: 'student' },
    redirect: '/student/jobs',
    children: [
      { path: 'jobs', name: 'StudentJobs', component: JobHallView, meta: { title: '岗位大厅' } },
      { path: 'applications', name: 'StudentApplications', component: MyApplicationsView, meta: { title: '我的投递' } },
      { path: 'contracts', name: 'StudentContracts', component: ContractsTimesheetsView, meta: { title: '工时与合同' } },
      { path: 'payroll', name: 'StudentPayroll', component: MyPayrollView, meta: { title: '薪酬记录' } },
      { path: 'profile', name: 'StudentProfile', component: ProfileView, meta: { title: '个人主页' } }
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
    store.notify('当前角色无权访问该配置模块', 'caution')
    return { path: '/admin/dashboard' }
  }

  return true
})

export default router
