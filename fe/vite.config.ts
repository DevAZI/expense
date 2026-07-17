import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 画面(5173)からバックエンド(8080)を同一オリジンで叩くためのプロキシ。
    // これが無いとCORS設定を別途入れることになる。開発中だけの仕組み。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
