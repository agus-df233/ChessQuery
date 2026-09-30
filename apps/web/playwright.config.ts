import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra el stack local completo (ver scripts/e2e.sh): web en :5173, users/tournament/game, LocalStack,
 * IdP simulado en :8090, Federación falsa en :8099 y el worker del ETL. Nada apunta a servicios reales.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  reporter: [['list']],
  outputDir: './e2e/.resultados',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    locale: 'es-CL',
    timezoneId: 'America/Santiago',
    // Un clic que no encuentra su botón falla en ese paso (con traza) en vez de agotar el timeout de la prueba
    actionTimeout: 20_000,
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
