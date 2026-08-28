const TOKEN_KEY = 'ioedu_token'
const USER_KEY = 'ioedu_user'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getStoredUser() {
  const raw = uni.getStorageSync(USER_KEY)
  if (!raw) return null
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch (e) {
    return null
  }
}

export function saveAuth(token, user) {
  uni.setStorageSync(TOKEN_KEY, token)
  uni.setStorageSync(USER_KEY, JSON.stringify(user || null))
}

export function saveUser(user) {
  uni.setStorageSync(USER_KEY, JSON.stringify(user || null))
}

export function clearAuth() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

/**
 * 操作级登录守卫:游客可浏览,涉及个人数据的操作前调用。
 * 已登录返回 true;未登录弹窗引导去登录页并返回 false。
 */
export function ensureLogin() {
  if (getToken()) return true
  uni.showModal({
    title: '需要登录',
    content: '该操作需要登录后使用,是否前往登录?',
    confirmText: '去登录',
    success: (res) => {
      if (res.confirm) uni.navigateTo({ url: '/pages/auth/index' })
    }
  })
  return false
}
