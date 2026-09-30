import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'
import { clearAuth, getToken } from '../utils/authStorage'

// 统一 HTTP 客户端:自动附带令牌,统一解包 {code, message, data}
const http = axios.create({ baseURL: '/api', timeout: 15000 })

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) return body.data
      if (!resp.config.silentError) ElMessage.error(body.message || '请求失败')
      const error = new Error(body.message)
      error.status = Number(body.code)
      return Promise.reject(error)
    }
    return body
  },
  (err) => {
    const status = err.response?.status
    const message = err.response?.data?.message
    if (status === 401) {
      clearAuth()
      if (router.currentRoute.value.path !== '/auth') router.push('/auth')
      ElMessage.warning(message || '登录已过期,请重新登录')
    } else if (!err.config?.silentError) {
      ElMessage.error(message || '网络请求失败')
    }
    if (message) err.message = message
    return Promise.reject(err)
  }
)

export default http
