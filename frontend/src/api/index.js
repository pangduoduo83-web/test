import http from './http'

// ---------- 公开 ----------
export const fetchPublicStats = () => http.get('/public/stats')

// ---------- 图片上传(返回 {url}) ----------
export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/upload', form)
}

// ---------- 教学资料上传(教师/管理员,返回 {url, name}) ----------
export const uploadDocFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/upload/file', form)
}

// ---------- 认证 ----------
export const login = (data) => http.post('/auth/login', data)
export const register = (data) => http.post('/auth/register', data)
export const fetchMe = () => http.get('/auth/me')
export const updateProfile = (data) => http.put('/auth/profile', data)
export const changePassword = (data) => http.put('/auth/password', data)

// ---------- 项目中心 ----------
export const fetchProjects = (params) => http.get('/projects', { params })
export const fetchProjectDetail = (id) => http.get(`/projects/${id}`)
export const enrollProject = (id) => http.post(`/projects/${id}/enroll`)
export const toggleFavorite = (id) => http.post(`/projects/${id}/favorite`)
export const updateProgress = (id, data) => http.put(`/projects/${id}/progress`, data)
export const fetchDiscussions = (id) => http.get(`/projects/${id}/discussions`)
export const postDiscussion = (id, data) => http.post(`/projects/${id}/discussions`, data)
export const submitWork = (id, data) => http.post(`/projects/${id}/submissions`, data)
export const fetchMySubmission = (id) => http.get(`/projects/${id}/submissions/mine`)

// ---------- 设备图书馆 ----------
export const fetchEquipment = (params) => http.get('/equipment', { params })
export const fetchEquipmentDetail = (id) => http.get(`/equipment/${id}`)
export const fetchLocations = () => http.get('/equipment/locations')

// ---------- 借阅 ----------
export const applyBorrow = (data) => http.post('/borrows', data)
export const fetchMyBorrows = (params) => http.get('/borrows/mine', { params })
export const fetchMyBorrowStats = () => http.get('/borrows/mine/stats')
export const cancelBorrow = (id) => http.post(`/borrows/${id}/cancel`)
export const requestReturn = (id) => http.post(`/borrows/${id}/return`)

// ---------- 个人中心 / 技能 / 通知 ----------
export const fetchDashboard = () => http.get('/dashboard')
export const fetchSkills = () => http.get('/skills')
export const submitAssessment = (scores) => http.post('/skills/assess', { scores })
export const fetchNotifications = () => http.get('/notifications')
export const markNotificationRead = (id) => http.post(`/notifications/${id}/read`)
export const markAllNotificationsRead = () => http.post('/notifications/read-all')

// ---------- 教师端 ----------
export const teacherStats = () => http.get('/teacher/stats')
export const teacherProjects = () => http.get('/teacher/projects')
export const teacherUpdateResources = (id, resources) =>
  http.put(`/teacher/projects/${id}/resources`, { resources })
export const teacherUpdateCover = (id, coverUrl) =>
  http.put(`/teacher/projects/${id}/cover`, { coverUrl })
export const teacherProjectStudents = (id) => http.get(`/teacher/projects/${id}/students`)

// ---------- 管理端 ----------
export const adminStats = () => http.get('/admin/stats')
export const adminTrends = () => http.get('/admin/trends')
export const adminListSubmissions = (params) => http.get('/admin/submissions', { params })
export const adminGradeSubmission = (id, data) => http.post(`/admin/submissions/${id}/grade`, data)
export const adminListBorrows = (params) => http.get('/admin/borrows', { params })
export const adminDecideBorrow = (id, data) => http.post(`/admin/borrows/${id}/decide`, data)
export const adminConfirmReturn = (id) => http.post(`/admin/borrows/${id}/confirm-return`)
export const adminCreateEquipment = (data) => http.post('/admin/equipment', data)
export const adminUpdateEquipment = (id, data) => http.put(`/admin/equipment/${id}`, data)
export const adminDeleteEquipment = (id) => http.delete(`/admin/equipment/${id}`)
export const adminListProjects = () => http.get('/admin/projects')
export const adminCreateProject = (data) => http.post('/admin/projects', data)
export const adminUpdateProject = (id, data) => http.put(`/admin/projects/${id}`, data)
export const adminDeleteProject = (id) => http.delete(`/admin/projects/${id}`)
export const adminProjectStudents = (id) => http.get(`/admin/projects/${id}/students`)
export const adminListEnrollments = (params) => http.get('/admin/enrollments', { params })
export const adminCreateEnrollment = (data) => http.post('/admin/enrollments', data)
export const adminUpdateEnrollment = (id, data) => http.put(`/admin/enrollments/${id}`, data)
export const adminDeleteEnrollment = (id) => http.delete(`/admin/enrollments/${id}`)
export const adminListUsers = (params) => http.get('/admin/users', { params })
export const adminGetUser = (id) => http.get(`/admin/users/${id}`)
export const adminCreateUser = (data) => http.post('/admin/users', data)
export const adminUpdateUser = (id, data) => http.put(`/admin/users/${id}`, data)
export const adminResetUserPassword = (id, password) =>
  http.post(`/admin/users/${id}/reset-password`, { password })
export const adminDeleteUser = (id) => http.delete(`/admin/users/${id}`)
export const adminListNotifications = (params) => http.get('/admin/notifications', { params })
export const adminSendNotification = (data) => http.post('/admin/notifications', data)
export const adminDeleteNotification = (id) => http.delete(`/admin/notifications/${id}`)
export const adminListDiscussions = (params) => http.get('/admin/discussions', { params })
export const adminDeleteDiscussion = (id) => http.delete(`/admin/discussions/${id}`)
export const adminAiReview = (id) => http.post(`/admin/submissions/${id}/ai-review`)

// ---------- AI 学习规划师 ----------
export const fetchAiPlan = () => http.get('/ai/learning-plan')
export const generateAiPlan = (data) => http.post('/ai/learning-plan/generate', data)

// ---------- AI 设置(管理端) ----------
export const adminGetAiSettings = () => http.get('/admin/ai-settings')
export const adminUpdateAiSettings = (data) => http.put('/admin/ai-settings', data)
export const adminTestAiSettings = () => http.post('/admin/ai-settings/test')
