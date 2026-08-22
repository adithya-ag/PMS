import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // Anything starting with /api gets forwarded to Spring Boot.
      // The browser only ever talks to localhost:5173, so it's not a
      // cross-origin request and CORS never comes up.
      '/api': 'http://localhost:8080'
    }
  }
})
