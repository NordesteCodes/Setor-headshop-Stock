import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests/e2e', workers: 1, timeout: 60000,
  use: { baseURL: process.env.E2E_BASE_URL || 'http://localhost:8080', headless: true, screenshot: 'only-on-failure', trace: 'off' },
  reporter: 'list'
});
