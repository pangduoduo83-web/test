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
