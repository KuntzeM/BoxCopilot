import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')

  const apiBaseUrl = env.VITE_API_BASE_URL || `http://localhost:${env.BACKEND_PORT || 8080}`
  const port = Number(env.VITE_FRONTEND_PORT || env.FRONTEND_PORT || 5173)

  return {
    plugins: [react()],
    server: {
      port,
      proxy: {
        '/api': {
          target: apiBaseUrl,
          changeOrigin: true,
          credentials: 'include',
        },
        '/oauth2': {
          target: apiBaseUrl,
          changeOrigin: true,
          credentials: 'include',
        },
        '/login': {
          target: apiBaseUrl,
          changeOrigin: true,
          credentials: 'include',
        },
      },
    },
  }
})
