import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: parseInt(process.env.VITE_FRONTEND_PORT),
    proxy: {
      '/api': {
        target: process.env.VITE_API_BASE_URL,
        changeOrigin: true,
        credentials: 'include',
      },
      '/oauth2': {
        target: process.env.VITE_API_BASE_URL,
        changeOrigin: true,
        credentials: 'include',
      },
      '/login': {
        target: process.env.VITE_API_BASE_URL,
        changeOrigin: true,
        credentials: 'include',
      },
    },
  },
})
