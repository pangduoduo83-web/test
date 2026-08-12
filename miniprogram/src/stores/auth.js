import { defineStore } from 'pinia'
import { getToken, getStoredUser, saveAuth, saveUser, clearAuth } from '@/utils/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: getToken(),
    user: getStoredUser()
  }),
  getters: {
    isLoggedIn: (s) => !!s.token,
    level: (s) => Math.floor((s.user?.exp || 0) / 100) + 1,
    levelProgress: (s) => (s.user?.exp || 0) % 100
  },
  actions: {
    setAuth({ token, user }) {
      this.token = token
      this.user = user
      saveAuth(token, user)
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
