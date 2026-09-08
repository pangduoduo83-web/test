import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发代理:/api、/uploads 转发到本地 Spring Boot(8080);/hub-api、/hub-assets 转发到项目商店 hub(8081)
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
      },
      '/hub-api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/hub-api/, '/api')
      },
      '/hub-assets': {
        target: 'http://localhost:8081',
        changeOrigin: true
      }
    }
  }
})
