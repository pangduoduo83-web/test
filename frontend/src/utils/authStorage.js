// 认证凭据存取:勾选"记住我"存 localStorage(关浏览器仍有效),
// 否则存 sessionStorage(关闭标签页即失效)。读取时两处都查。
const TOKEN_KEY = 'ioedu_token'
const USER_KEY = 'ioedu_user'

export const getToken = () =>
  localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || ''

export const getUser = () => {
  const raw = localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY)
  try {
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export const saveAuth = (token, user, remember) => {
  clearAuth()
  const store = remember ? localStorage : sessionStorage
  store.setItem(TOKEN_KEY, token)
  store.setItem(USER_KEY, JSON.stringify(user))
}

export const saveUser = (user) => {
  const store = localStorage.getItem(TOKEN_KEY) ? localStorage : sessionStorage
  store.setItem(USER_KEY, JSON.stringify(user))
}

export const clearAuth = () => {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}
