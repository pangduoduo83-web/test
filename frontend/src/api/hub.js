import axios from 'axios'
import { ElMessage } from 'element-plus'

// 平台管理端直连项目商店(hub)的客户端:/hub-api 由 Nginx / Vite 代理到 hub 的 /api
// 平台管理员令牌与客户站点的用户令牌完全独立,存在单独的键下
const TOKEN_KEY = 'hub_admin_token'
const NAME_KEY = 'hub_admin_name'

export const getHubToken = () => localStorage.getItem(TOKEN_KEY)
export const getHubAdminName = () => localStorage.getItem(NAME_KEY)
export const setHubAuth = (token, name) => {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(NAME_KEY, name)
}
export const clearHubAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NAME_KEY)
}

const hub = axios.create({ baseURL: '/hub-api', timeout: 20000 })

hub.interceptors.request.use((config) => {
  const token = getHubToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

hub.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const message = err.response?.data?.message
    if (status === 401) {
      clearHubAuth()
      if (window.location.pathname !== '/platform/login') window.location.href = '/platform/login'
      ElMessage.warning(message || '登录已过期,请重新登录')
    } else {
      ElMessage.error(message || '网络请求失败')
    }
    return Promise.reject(err)
  }
)

export const hubLogin = (data) => hub.post('/hub-admin/login', data)
export const hubChangePassword = (data) => hub.post('/hub-admin/password', data)
export const hubStats = () => hub.get('/hub-admin/stats')
export const hubItems = (status) => hub.get('/hub-admin/items', { params: { status } })
export const hubItem = (id) => hub.get(`/hub-admin/items/${id}`)
export const hubItemPayload = (id) => hub.get(`/hub-admin/items/${id}/payload`)
export const hubReview = (id, data) => hub.post(`/hub-admin/items/${id}/review`, data)
export const hubVisibility = (id, visibility) => hub.put(`/hub-admin/items/${id}/visibility`, { visibility })
export const hubGrants = (id, tenantIds) => hub.put(`/hub-admin/items/${id}/grants`, { tenantIds })
export const hubTenants = () => hub.get('/hub-admin/tenants')
export const hubCreateTenant = (data) => hub.post('/hub-admin/tenants', data)
export const hubRotateKey = (id) => hub.post(`/hub-admin/tenants/${id}/rotate-key`)
export const hubTenantStatus = (id, status) => hub.put(`/hub-admin/tenants/${id}/status`, { status })
