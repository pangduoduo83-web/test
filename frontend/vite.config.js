import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理:/api 转发到本地 Spring Boot(8080)
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
