import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// En desarrollo (npm run dev) /api se redirige al backend local. Se conserva el Host del navegador
// (changeOrigin: false) para que los enlaces HATEOAS apunten a http://localhost:5173/api/...
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_API_TARGET || 'http://localhost:8080', changeOrigin: false },
    },
  },
});
