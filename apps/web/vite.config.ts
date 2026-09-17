/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Configuración de Vite y Vitest de la app web.
// - En desarrollo, /api e /internal se proxean al servicio local (por ahora `users`;
//   cuando existan los demás servicios el ALB de cloud enruta por prefijo).
// - Los vendors pesados van en chunks separados para que el caché sobreviva entre releases.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: process.env.VITE_DEV_API ?? 'http://localhost:8081', changeOrigin: true },
    },
  },
  build: {
    chunkSizeWarningLimit: 600,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return undefined;
          if (id.includes('oidc-client-ts') || id.includes('react-oidc-context')) return 'vendor-auth';
          if (id.includes('@tanstack')) return 'vendor-query';
          if (id.includes('react-router')) return 'vendor-router';
          if (id.includes('/react/') || id.includes('/react-dom/') || id.includes('/scheduler/')) return 'vendor-react';
          return 'vendor';
        },
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/setupTests.ts',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      exclude: ['node_modules/', 'dist/', 'src/main.tsx', 'src/vite-env.d.ts', '**/*.spec.*'],
    },
  },
});
