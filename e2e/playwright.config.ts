import { defineConfig, devices } from '@playwright/test';

// Starts the Spring Boot app for the tests, or reuses one already running on :5000.
// Windows uses the .cmd wrapper; macOS/Linux use the shell script.
const mvnw = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw';

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
    command: `${mvnw} -q install -DskipTests && ${mvnw} -q -pl invoice-api spring-boot:run`,
    cwd: '../backend',
    url: 'http://localhost:5000',
    reuseExistingServer: true,
    timeout: 120_000,
  },
});
