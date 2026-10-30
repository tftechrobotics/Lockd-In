import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: 'e2e',
  timeout: 60_000,
  retries: 0,
  reporter: [['list']],
  // Tests pin the clock to a Chicago time, so pin the zone too (CI runners use UTC).
  use: { baseURL: 'http://localhost:4173', trace: 'off', timezoneId: 'America/Chicago' },
  webServer: { command: 'npx vite preview --port 4173 --strictPort', port: 4173, reuseExistingServer: true },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
