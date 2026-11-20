import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development the API and the WebSocket are proxied to the Spring Boot backend.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
});
