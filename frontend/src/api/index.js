import http from './http'

// ---------- 公开 ----------
export const fetchPublicStats = () => http.get('/public/stats')
export const fetchSiteConfig = () => http.get('/public/site-config')

// ---------- 图片上传(返回 {url}) ----------
export const uploadImage = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/upload', form, { timeout: 180000 })
}

// ---------- 教学资料上传(教师/管理员,返回 {url, name}) ----------
export const uploadDocFile = (file) => {
  const form = new FormData()
  form.append('file', file)
  return http.post('/upload/file', form, { timeout: 180000 })
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
export const fetchMySubmissions = (id) => http.get(`/projects/${id}/submissions/mine-all`)

// ---------- 设备图书馆 ----------
export const fetchEquipment = (params) => http.get('/equipment', { params })
export const fetchEquipmentDetail = (id) => http.get(`/equipment/${id}`)
export const fetchLocations = () => http.get('/equipment/locations')
export const fetchEquipmentFavorites = () => http.get('/equipment/favorites')
export const toggleEquipmentFavorite = (id) => http.post(`/equipment/${id}/favorite`)

// ---------- 借阅 ----------
export const applyBorrow = (data) => http.post('/borrows', data)
export const fetchMyBorrows = (params) => http.get('/borrows/mine', { params })
export const fetchMyBorrowStats = () => http.get('/borrows/mine/stats')
export const cancelBorrow = (id) => http.post(`/borrows/${id}/cancel`)
export const requestReturn = (id) => http.post(`/borrows/${id}/return`)
export const renewBorrow = (id) => http.post(`/borrows/${id}/renew`)

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
export const teacherCreateProject = (data) => http.post('/teacher/projects', data)
export const teacherUpdateProject = (id, data) => http.put(`/teacher/projects/${id}`, data)
export const teacherUpdateResources = (id, resources) =>
  http.put(`/teacher/projects/${id}/resources`, { resources })
export const teacherUpdateCover = (id, coverUrl) =>
  http.put(`/teacher/projects/${id}/cover`, { coverUrl })
export const teacherProjectStudents = (id) => http.get(`/teacher/projects/${id}/students`)
export const teacherSkillDimensions = () => http.get('/teacher/skill-dimensions')
export const teacherListSubmissions = (params) => http.get('/teacher/submissions', { params })
export const teacherGradeSubmission = (id, data) => http.post(`/teacher/submissions/${id}/grade`, data)
export const teacherReturnSubmission = (id, feedback) => http.post(`/teacher/submissions/${id}/return`, { feedback })
export const teacherAiReview = (id) => http.post(`/teacher/submissions/${id}/ai-review`, null, { timeout: 120000 })
export const teacherAnnounceProject = (id, data) => http.post(`/teacher/projects/${id}/announce`, data)
export const teacherAtRisk = () => http.get('/teacher/at-risk')
export const teacherRemindStudent = (projectId, studentId, message) => http.post(`/teacher/projects/${projectId}/remind/${studentId}`, { message })
// 课程班(教师与管理员共用;管理员可指定授课教师)
export const teacherClasses = () => http.get('/teacher/classes')
export const teacherCreateClass = (data) => http.post('/teacher/classes', data)
export const teacherClassDetail = (id) => http.get(`/teacher/classes/${id}`)
export const teacherUpdateClass = (id, data) => http.put(`/teacher/classes/${id}`, data)
export const teacherDeleteClass = (id) => http.delete(`/teacher/classes/${id}`)
export const teacherAddClassMembers = (id, identifiers) => http.post(`/teacher/classes/${id}/members`, { identifiers })
export const teacherRemoveClassMember = (id, userId) => http.delete(`/teacher/classes/${id}/members/${userId}`)
export const teacherAssignClass = (id, data) => http.post(`/teacher/classes/${id}/assignments`, data)
export const teacherUpdateAssignment = (id, assignmentId, data) => http.put(`/teacher/classes/${id}/assignments/${assignmentId}`, data)
export const teacherRemoveAssignment = (id, assignmentId) => http.delete(`/teacher/classes/${id}/assignments/${assignmentId}`)
export const teacherClassAnnounce = (id, data) => http.post(`/teacher/classes/${id}/announcements`, data)
// 学生端班级
export const myClasses = () => http.get('/classes/mine')
export const joinClass = (code) => http.post('/classes/join', { code })

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
export const adminAiReview = (id) => http.post(`/admin/submissions/${id}/ai-review`, null, { timeout: 120000 })
export const adminReturnSubmission = (id, feedback) => http.post(`/admin/submissions/${id}/return`, { feedback })
export const adminAuditLogs = (params) => http.get('/admin/audit-logs', { params })
export const adminScreen = () => http.get('/admin/screen', { timeout: 60000 })
export const skillQuizStart = (skillName) => http.post('/skills/quiz/start', { skillName }, { timeout: 120000 })
export const skillQuizSubmit = (quizId, answers) => http.post('/skills/quiz/submit', { quizId, answers })
export const adminScreenSettings = () => http.get('/admin/screen-settings')
export const adminUpdateScreenSettings = (data) => http.put('/admin/screen-settings', data)

// ---------- 技能维度(管理端) ----------
export const adminListSkillDimensions = () => http.get('/admin/skill-dimensions')
export const adminCreateSkillDimension = (data) => http.post('/admin/skill-dimensions', data)
export const adminUpdateSkillDimension = (id, data) => http.put(`/admin/skill-dimensions/${id}`, data)
export const adminDeleteSkillDimension = (id) => http.delete(`/admin/skill-dimensions/${id}`)

// ---------- AI 学习规划师 ----------
export const fetchAiPlan = () => http.get('/ai/learning-plan')
export const generateAiPlan = (data) => http.post('/ai/learning-plan/generate', data)

// ---------- AI 助手 / SKILL ----------
export const aiSkills = () => http.get('/ai/skills')
export const aiSkillDetail = (key) => http.get(`/ai/skills/${key}`)
export const aiCreateSkill = (data) => http.post('/ai/skills', data)
export const aiUpdateSkill = (id, data) => http.put(`/ai/skills/${id}`, data)
export const aiDeleteSkill = (id) => http.delete(`/ai/skills/${id}`)
export const aiDuplicateSkill = (key, data) => http.post(`/ai/skills/${key}/duplicate`, data)
export const aiTools = () => http.get('/ai/tools')
export const aiChat = (data) => http.post('/ai/chat', data, { timeout: 180000 })
export const aiConversations = () => http.get('/ai/conversations')
export const aiConversationMessages = (id) => http.get(`/ai/conversations/${id}/messages`)
export const aiDeleteConversation = (id) => http.delete(`/ai/conversations/${id}`)

// ---------- AI 中心(管理端) ----------
export const adminAiSkills = () => http.get('/admin/ai/skills')
export const adminAiPromoteSkill = (id) => http.post(`/admin/ai/skills/${id}/promote`)
export const adminAiTools = () => http.get('/admin/ai/tools')
export const adminAiUpdateTool = (name, data) => http.put(`/admin/ai/tools/${name}`, data)
export const adminAiRuns = (params) => http.get('/admin/ai/runs', { params })
export const adminAiRunTools = (id) => http.get(`/admin/ai/runs/${id}/tools`)
export const adminAiUsage = (days) => http.get('/admin/ai/usage', { params: { days } })

// ---------- AI 设置(管理端) ----------
export const adminGetAiSettings = () => http.get('/admin/ai-settings')
export const adminUpdateAiSettings = (data) => http.put('/admin/ai-settings', data)
export const adminTestAiSettings = () => http.post('/admin/ai-settings/test')

// ---------- 站点设置(管理端) ----------
export const adminGetSiteSettings = () => http.get('/admin/site-settings')
export const adminUpdateSiteSettings = (data) => http.put('/admin/site-settings', data)

// ---------- 项目商店(教师/管理员) ----------
export const storeStatus = () => http.get('/store/status')
export const storeItems = (params) => http.get('/store/items', { params })
export const storeItem = (id) => http.get(`/store/items/${id}`)
export const storeInstall = (id, data) => http.post(`/store/items/${id}/install`, data, { timeout: 180000 })
export const storePublish = (projectId, data) => http.post(`/store/publish/${projectId}`, data, { timeout: 180000 })
export const storeMine = () => http.get('/store/mine')
export const storeLocalProjects = () => http.get('/store/local-projects')
export const adminGetStoreSettings = () => http.get('/admin/store-settings')
export const adminUpdateStoreSettings = (data) => http.put('/admin/store-settings', data)
export const adminTestStoreSettings = () => http.post('/admin/store-settings/test')
/** 商店返回的封面 /hub-assets/xxx 经本站后端代理展示 */
export const storeAssetUrl = (url) =>
  url && url.startsWith('/hub-assets/') ? url.replace('/hub-assets/', '/api/public/store-assets/') : url
