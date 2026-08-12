import { createRouter, createWebHistory } from 'vue-router'
import { getToken, getUser } from '../utils/authStorage'

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
        { path: 'skills', name: 'skills', component: () => import('../views/student/SkillsView.vue') }
      ]
    },
    {
      path: '/teacher',
      component: () => import('../views/teacher/TeacherLayout.vue'),
      meta: { requiresAuth: true, requiresTeacher: true },
      children: [
        { path: '', redirect: '/teacher/workbench' },
        { path: 'workbench', name: 'teacher-workbench', component: () => import('../views/teacher/TeacherWorkbench.vue') }
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
        { path: 'ai-settings', name: 'admin-ai-settings', component: () => import('../views/admin/AdminAiSettings.vue') },
        { path: 'site-settings', name: 'admin-site-settings', component: () => import('../views/admin/AdminSiteSettings.vue') },
        { path: 'users', name: 'admin-users', component: () => import('../views/admin/AdminUsers.vue') }
      ]
    }
  ]
})

router.beforeEach((to) => {
  const token = getToken()
  const user = getUser()
  if (to.meta.requiresAuth && !token) return '/auth'
  if (to.meta.requiresAdmin && user?.role !== 'ADMIN') return '/app/dashboard'
  if (to.meta.requiresTeacher && user?.role !== 'TEACHER' && user?.role !== 'ADMIN') return '/app/dashboard'
  return true
})

export default router
