import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  // 后端地址，可通过环境变量 VITE_API_TARGET 覆盖
  const apiTarget = env.VITE_API_TARGET || 'http://localhost:8088'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5173,
      open: false,
      proxy: {
        // 前端统一请求相对路径 /api/...，开发时转发给 Java 后端
        '/api': {
          target: apiTarget,
          changeOrigin: true
        },
        // 模拟统一身份认证中心页面（P2 B.1，由后端输出），开发时与前端同源访问
        '/mock-cas': {
          target: apiTarget,
          changeOrigin: true
        }
      }
    }
  }
})
