import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:5000',
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'cd ../backend && ./mvnw -q install -DskipTests && ./mvnw -q -pl invoice-api spring-boot:run',
    url: 'http://localhost:5000',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
