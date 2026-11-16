import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Proxy cùng nguồn giúp FE dùng cookie session và CSRF của Spring Security.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      // Spring có thể trả Location tuyệt đối về 8080; giữ redirect qua cùng nguồn Vite.
      '/api': { target: 'http://localhost:8080', changeOrigin: true, autoRewrite: true },
      '/login': { target: 'http://localhost:8080', changeOrigin: true, autoRewrite: true },
      '/logout': { target: 'http://localhost:8080', changeOrigin: true, autoRewrite: true },
    },
  },
})
