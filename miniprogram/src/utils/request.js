import { BASE_URL } from '@/config'
import { getToken, clearAuth } from '@/utils/auth'

let redirectingToLogin = false

function toLogin() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  clearAuth()
  uni.showToast({ title: '登录已过期,请重新登录', icon: 'none' })
  setTimeout(() => {
    uni.reLaunch({ url: '/pages/auth/index' })
    redirectingToLogin = false
  }, 600)
}

function buildQuery(params) {
  if (!params) return ''
  const pairs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

/**
 * 统一请求:自动附带 Bearer token,按业务码 code===0 解包 data,
 * 401 清登录态并跳回登录页,业务错误统一 toast。
 */
export function request({ url, method = 'GET', data, params, silent = false }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api${url}${buildQuery(params)}`,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {})
      },
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 401) {
          toLogin()
          reject(new Error(body.message || '未登录'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        const msg = body.message || `请求失败(${res.statusCode})`
        if (!silent) uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
      fail: (err) => {
        if (!silent) uni.showToast({ title: '网络异常,请确认后端已启动', icon: 'none' })
        reject(err)
      }
    })
  })
}

export const get = (url, params, opt = {}) => request({ url, method: 'GET', params, ...opt })
export const post = (url, data, opt = {}) => request({ url, method: 'POST', data, ...opt })
export const put = (url, data, opt = {}) => request({ url, method: 'PUT', data, ...opt })

/**
 * 上传图片:字段名固定 file,返回 { url, name }
 */
export function uploadImage(filePath) {
  return uploadTo('/api/upload', filePath)
}

/**
 * 上传教学资料(教师/管理员):支持 pdf/doc/zip/mp4 等,返回 { url, name }
 */
export function uploadDocFile(filePath) {
  return uploadTo('/api/upload/file', filePath)
}

function uploadTo(path, filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${path}`,
      filePath,
      name: 'file',
      timeout: 180000,
      header: getToken() ? { Authorization: `Bearer ${getToken()}` } : {},
      success: (res) => {
        let body = {}
        try {
          body = JSON.parse(res.data)
        } catch (e) {
          reject(new Error('上传响应解析失败'))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
        } else {
          uni.showToast({ title: body.message || '上传失败', icon: 'none' })
          reject(new Error(body.message || '上传失败'))
        }
      },
      fail: (err) => {
        uni.showToast({ title: '上传失败,请重试', icon: 'none' })
        reject(err)
      }
    })
  })
}
