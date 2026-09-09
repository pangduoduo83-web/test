import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '../utils/authStorage'
import { getHubToken } from '../api/hub'

// 路由:/ 即登录页(与参考站一致),/app 学生端,/admin 管理端
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: () => import('../views/AuthView.vue') },
    { path: '/auth', name: 'auth', component: () => import('../views/AuthView.vue') },
    {
      path: '/app',
      component: () => import('../views/student/StudentLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/app/dashboard' },
        { path: 'dashboard', name: 'dashboard', component: () => import('../views/student/DashboardView.vue') },
        { path: 'projects', name: 'projects', component: () => import('../views/student/ProjectsView.vue') },
        { path: 'projects/:id', name: 'project-detail', component: () => import('../views/student/ProjectDetailView.vue') },
        { path: 'equipment', name: 'equipment', component: () => import('../views/student/EquipmentView.vue') },
        { path: 'borrowing', name: 'borrowing', component: () => import('../views/student/BorrowingView.vue') },
        { path: 'skills', name: 'skills', component: () => import('../views/student/SkillsView.vue') },
        { path: 'classes', name: 'my-classes', component: () => import('../views/student/MyClassesView.vue') },
        { path: 'ai', name: 'ai-assistant', component: () => import('../views/student/AiWorkbenchView.vue') }
      ]
    },
    {
      // 数据大屏:独立全屏页面,不套后台布局;数据按当前站点(Host)隔离
      path: '/admin/screen',
      name: 'admin-screen',
      component: () => import('../views/admin/AdminBigScreen.vue'),
      meta: { requiresAuth: true, requiresAdmin: true }
    },
    {
      path: '/teacher',
      component: () => import('../views/teacher/TeacherLayout.vue'),
      meta: { requiresAuth: true, requiresTeacher: true },
      children: [
        { path: '', redirect: '/teacher/workbench' },
        { path: 'workbench', name: 'teacher-workbench', component: () => import('../views/teacher/TeacherWorkbench.vue') },
        { path: 'classes', name: 'teacher-classes', component: () => import('../views/teacher/TeacherClasses.vue') },
        { path: 'submissions', name: 'teacher-submissions', component: () => import('../views/teacher/TeacherSubmissions.vue') }
      ]
    },
    {
      path: '/admin',
      component: () => import('../views/admin/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/admin/dashboard' },
        { path: 'dashboard', name: 'admin-dashboard', component: () => import('../views/admin/AdminDashboard.vue') },
        { path: 'equipment', name: 'admin-equipment', component: () => import('../views/admin/AdminEquipment.vue') },
        { path: 'borrows', name: 'admin-borrows', component: () => import('../views/admin/AdminBorrows.vue') },
        { path: 'projects', name: 'admin-projects', component: () => import('../views/admin/AdminProjects.vue') },
        { path: 'enrollments', name: 'admin-enrollments', component: () => import('../views/admin/AdminEnrollments.vue') },
        { path: 'submissions', name: 'admin-submissions', component: () => import('../views/admin/AdminSubmissions.vue') },
        { path: 'notifications', name: 'admin-notifications', component: () => import('../views/admin/AdminNotifications.vue') },
        { path: 'discussions', name: 'admin-discussions', component: () => import('../views/admin/AdminDiscussions.vue') },
        { path: 'skill-dimensions', name: 'admin-skill-dimensions', component: () => import('../views/admin/AdminSkillDimensions.vue') },
        { path: 'ai-settings', name: 'admin-ai-settings', component: () => import('../views/admin/AdminAiSettings.vue') },
        { path: 'site-settings', name: 'admin-site-settings', component: () => import('../views/admin/AdminSiteSettings.vue') },
        { path: 'store', name: 'admin-store', component: () => import('../views/admin/AdminStore.vue') },
        { path: 'ai-center', name: 'admin-ai-center', component: () => import('../views/admin/AdminAiCenter.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../views/admin/AdminUsers.vue') },
        { path: 'classes', name: 'admin-classes', component: () => import('../views/admin/AdminClasses.vue') },
        { path: 'audit-logs', name: 'admin-audit-logs', component: () => import('../views/admin/AdminAuditLogs.vue') }
      ]
    },
    // 项目商店平台管理端:独立登录(商店服务的平台管理员),与客户站点账号无关
    { path: '/platform/login', name: 'platform-login', component: () => import('../views/platform/PlatformLogin.vue') },
    // 平台总览大屏:全部客户站点汇总,独立全屏页面
    { path: '/platform/screen', name: 'platform-screen', component: () => import('../views/platform/PlatformScreen.vue'), meta: { requiresHubAdmin: true } },
    {
      path: '/platform',
      component: () => import('../views/platform/PlatformLayout.vue'),
      meta: { requiresHubAdmin: true },
      children: [
        { path: '', redirect: '/platform/home' },
        { path: 'home', name: 'platform-home', component: () => import('../views/platform/PlatformHome.vue') },
        { path: 'items', name: 'platform-items', component: () => import('../views/platform/PlatformItems.vue') },
        { path: 'sites', name: 'platform-sites', component: () => import('../views/platform/PlatformSites.vue') },
        { path: 'tenants', redirect: '/platform/sites' }
      ]
    }
  ]
})

router.beforeEach((to) => {
  if (to.meta.requiresHubAdmin && !getHubToken()) return '/platform/login'
  const token = getToken()
  const user = getUser()
  if (to.meta.requiresAuth && !token) return '/auth'
  if (to.meta.requiresAdmin && user?.role !== 'ADMIN' && user?.role !== 'LAB_ADMIN') return '/app/dashboard'
  // 实验室管理员只能进设备与借阅相关页面,其余后台页面回到看板
  if (to.meta.requiresAdmin && user?.role === 'LAB_ADMIN'
      && !/^\/admin\/(dashboard|equipment|borrows)/.test(to.path)) return '/admin/dashboard'
  if (to.meta.requiresTeacher && user?.role !== 'TEACHER' && user?.role !== 'ADMIN') return '/app/dashboard'
  return true
})

export default router
