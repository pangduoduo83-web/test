import { BASE_URL } from '@/config'
import { getToken, clearAuth } from '@/utils/auth'

let redirectingToLogin = false

function toLogin() {
  if (redirectingToLogin) return
  redirectingToLogin = true
  // 游客访问需登录的功能时提示「请先登录」;有令牌说明是会话过期
  const hadToken = !!getToken()
  clearAuth()
  uni.showToast({ title: hadToken ? '登录已过期,请重新登录' : '请先登录', icon: 'none' })
  setTimeout(() => {
    uni.navigateTo({
      url: '/pages/auth/index',
      fail: () => uni.reLaunch({ url: '/pages/auth/index' })
    })
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
export function request({ url, method = 'GET', data, params, silent = false, timeout = 15000 }) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/api${url}${buildQuery(params)}`,
      method,
      data,
      timeout,
      header: {
        'Content-Type': 'application/json',
        ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {})
      },
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 401 || body.code === 401) {
          toLogin()
          reject(responseError(body.message || '未登录', 401))
          return
        }
        if (body.code === 0) {
          resolve(body.data)
          return
        }
        const msg = body.message || `请求失败(${res.statusCode})`
        if (!silent) uni.showToast({ title: msg, icon: 'none' })
        reject(responseError(msg, body.code || res.statusCode))
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

function responseError(message, status) {
  const error = new Error(message)
  error.status = Number(status)
  return error
}

/**
 * 上传图片:字段名固定 file,返回 { url, name }
 */
export function uploadImage(filePath, metadata = {}) {
  return uploadAsset(filePath, 'image', metadata)
}

/**
 * 上传教学资料(教师/管理员):支持 pdf/doc/zip/mp4 等,返回 { url, name }
 */
export function uploadDocFile(filePath, metadata = {}) {
  return uploadAsset(filePath, 'file', metadata)
}

export function uploadSubmissionFile(filePath, metadata = {}) { return uploadAsset(filePath, 'submission', metadata) }

async function uploadAsset(filePath, kind, metadata) {
  try {
    let name = metadata.name || filePath.split('/').pop().split(/[?#]/)[0]
    let size = Number(metadata.size)
    if (!Number.isFinite(size) || size <= 0) {
      const info = await new Promise((resolve, reject) => uni.getFileInfo({ filePath, success: resolve, fail: reject }))
      size = info.size
    }
    if (!/\.[a-z0-9]{1,10}$/i.test(name)) {
      if (kind === 'image' || metadata.mediaType === 'image') {
        const info = await new Promise((resolve, reject) => uni.getImageInfo({ src: filePath, success: resolve, fail: reject }))
        name = `图片.${info.type === 'jpeg' ? 'jpg' : info.type || 'jpg'}`
      } else if (metadata.mediaType === 'video') name = '演示视频.mp4'
      else throw new Error('无法识别文件类型，请重新选择文件')
    }
    const grant = await post('/upload/direct/initiate', { kind, name, size }, { silent: true })
    if (grant.mode === 'server') return await uploadTo(`/api/upload${kind === 'image' ? '' : '/' + kind}`, filePath)
    if (grant.mode !== 'oss' || !/^https:\/\/[a-z0-9-]+\.oss-[a-z0-9-]+\.aliyuncs\.com$/.test(grant.host)) {
      throw new Error('上传配置无效，请联系管理员')
    }
    let uploadError
    try {
      await new Promise((resolve, reject) => {
        uni.uploadFile({
          url: grant.host, filePath, name: 'file', formData: grant.fields,
          timeout: 15 * 60 * 1000,
          // OSS only receives its short-lived POST policy, never our application's Bearer token.
          success: res => res.statusCode >= 200 && res.statusCode < 300 ? resolve() : reject(new Error('文件直传失败')),
          fail: reject
        })
      })
    } catch (error) { uploadError = error }
    // Confirm even after a lost upload response; retries never resend the large file.
    let failure
    for (let attempt = 0; attempt < 3; attempt++) {
      try { return await post(`/upload/direct/${grant.id}/complete`, null, { silent: true, timeout: 60000 }) }
      catch (error) {
        failure = error
        if (error.status && error.status < 500) break
        if (attempt < 2) await new Promise(resolve => setTimeout(resolve, (attempt + 1) * 1000))
      }
    }
    throw uploadError ? new Error('文件直传未完成，请检查网络后重试') : failure
  } catch (error) {
    uni.showToast({ title: error.message || '上传失败，请重试', icon: 'none' })
    throw error
  }
}

function uploadTo(path, filePath) {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${path}`,
      filePath,
      name: 'file',
      timeout: 15 * 60 * 1000,
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
