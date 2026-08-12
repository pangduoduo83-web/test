import { get, post, put, request } from '@/utils/request'

// ---------- 公开 ----------
export const fetchPublicStats = () => get('/public/stats', null, { silent: true })
export const fetchSiteConfig = () => get('/public/site-config', null, { silent: true })

// ---------- 认证 ----------
export const login = (data) => post('/auth/login', data)
export const register = (data) => post('/auth/register', data)
export const fetchMe = () => get('/auth/me', null, { silent: true })
export const updateProfile = (data) => put('/auth/profile', data)
export const changePassword = (data) => put('/auth/password', data)

// ---------- 项目中心 ----------
export const fetchProjects = (params) => get('/projects', params)
export const fetchProjectDetail = (id) => get(`/projects/${id}`)
export const enrollProject = (id) => post(`/projects/${id}/enroll`)
export const toggleFavorite = (id) => post(`/projects/${id}/favorite`)
export const updateProgress = (id, data) => put(`/projects/${id}/progress`, data)
export const fetchDiscussions = (id) => get(`/projects/${id}/discussions`)
export const postDiscussion = (id, data) => post(`/projects/${id}/discussions`, data)
export const submitWork = (id, data) => post(`/projects/${id}/submissions`, data)
export const fetchMySubmission = (id) => get(`/projects/${id}/submissions/mine`, null, { silent: true })
export const fetchMySubmissions = (id) => get(`/projects/${id}/submissions/mine-all`, null, { silent: true })

// ---------- 设备图书馆 ----------
export const fetchEquipment = (params) => get('/equipment', params)
export const fetchEquipmentDetail = (id) => get(`/equipment/${id}`)
export const fetchLocations = () => get('/equipment/locations', null, { silent: true })
export const fetchEquipmentFavorites = () => get('/equipment/favorites', null, { silent: true })
export const toggleEquipmentFavorite = (id) => post(`/equipment/${id}/favorite`)

// ---------- 借阅 ----------
export const applyBorrow = (data) => post('/borrows', data)
export const fetchMyBorrows = (params) => get('/borrows/mine', params)
export const cancelBorrow = (id) => post(`/borrows/${id}/cancel`)
export const requestReturn = (id) => post(`/borrows/${id}/return`)
export const renewBorrow = (id) => post(`/borrows/${id}/renew`)

// ---------- 个人中心 / 技能 / 通知 ----------
export const fetchDashboard = () => get('/dashboard')
export const fetchSkills = () => get('/skills')
export const submitAssessment = (scores) => post('/skills/assess', { scores })
export const fetchNotifications = () => get('/notifications', null, { silent: true })
export const markNotificationRead = (id) => post(`/notifications/${id}/read`)
export const markAllNotificationsRead = () => post('/notifications/read-all')

// ---------- 教师端 ----------
export const teacherStats = () => get('/teacher/stats')
export const teacherProjects = () => get('/teacher/projects')
export const teacherUpdateResources = (id, resources) =>
  put(`/teacher/projects/${id}/resources`, { resources })
export const teacherUpdateCover = (id, coverUrl) => put(`/teacher/projects/${id}/cover`, { coverUrl })
export const teacherProjectStudents = (id) => get(`/teacher/projects/${id}/students`)

// ---------- 管理端 ----------
export const adminStats = () => get('/admin/stats')
export const adminTrends = () => get('/admin/trends')
export const adminListBorrows = (params) => get('/admin/borrows', params)
export const adminDecideBorrow = (id, data) => post(`/admin/borrows/${id}/decide`, data)
export const adminConfirmReturn = (id) => post(`/admin/borrows/${id}/confirm-return`)
export const adminListSubmissions = (params) => get('/admin/submissions', params)
export const adminGradeSubmission = (id, data) => post(`/admin/submissions/${id}/grade`, data)
export const adminCreateEquipment = (data) => post('/admin/equipment', data)
export const adminUpdateEquipment = (id, data) => put(`/admin/equipment/${id}`, data)
export const adminDeleteEquipment = (id) => request({ url: `/admin/equipment/${id}`, method: 'DELETE' })
export const adminListProjects = () => get('/admin/projects')
export const adminCreateProject = (data) => post('/admin/projects', data)
export const adminUpdateProject = (id, data) => put(`/admin/projects/${id}`, data)
export const adminDeleteProject = (id) => request({ url: `/admin/projects/${id}`, method: 'DELETE' })
export const adminProjectStudents = (id) => get(`/admin/projects/${id}/students`)

// ---------- 管理端:用户 ----------
export const adminListUsers = (params) => get('/admin/users', params)
export const adminCreateUser = (data) => post('/admin/users', data)
export const adminUpdateUser = (id, data) => put(`/admin/users/${id}`, data)
export const adminResetUserPassword = (id, password) =>
  post(`/admin/users/${id}/reset-password`, { password })
export const adminDeleteUser = (id) => request({ url: `/admin/users/${id}`, method: 'DELETE' })

// ---------- 管理端:报名与进度 ----------
export const adminListEnrollments = (params) => get('/admin/enrollments', params)
export const adminCreateEnrollment = (data) => post('/admin/enrollments', data)
export const adminUpdateEnrollment = (id, data) => put(`/admin/enrollments/${id}`, data)
export const adminDeleteEnrollment = (id) => request({ url: `/admin/enrollments/${id}`, method: 'DELETE' })

// ---------- 管理端:通知 ----------
export const adminListNotifications = (params) => get('/admin/notifications', params)
export const adminSendNotification = (data) => post('/admin/notifications', data)
export const adminDeleteNotification = (id) => request({ url: `/admin/notifications/${id}`, method: 'DELETE' })

// ---------- 管理端:讨论 ----------
export const adminListDiscussions = (params) => get('/admin/discussions', params)
export const adminDeleteDiscussion = (id) => request({ url: `/admin/discussions/${id}`, method: 'DELETE' })

// ---------- AI ----------
export const fetchAiPlan = () => get('/ai/learning-plan', null, { silent: true })
export const generateAiPlan = (data) => post('/ai/learning-plan/generate', data)
export const adminAiReview = (id) => post(`/admin/submissions/${id}/ai-review`)
export const adminGetAiSettings = () => get('/admin/ai-settings')
export const adminUpdateAiSettings = (data) => put('/admin/ai-settings', data)
export const adminTestAiSettings = () => post('/admin/ai-settings/test')
