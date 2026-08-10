import { defineStore } from 'pinia'
import { clearAuth, getToken, getUser, saveAuth, saveUser } from '../utils/authStorage'

// 认证状态:按"记住我"决定持久化位置(localStorage / sessionStorage)
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getUser()
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    isAdmin: (s) => s.user?.role === 'ADMIN'
  },
  actions: {
    setAuth({ token, user }, remember = true) {
      this.token = token
      this.user = user
      saveAuth(token, user, remember)
    },
    updateUser(user) {
      this.user = user
      saveUser(user)
    },
    logout() {
      this.token = ''
      this.user = null
      clearAuth()
    }
  }
})
