import { defineConfig, devices } from '@playwright/test';

/**
 * E2E mặc định chạy trên frontend ở chế độ mock (không cần backend).
 * - Local: dùng trình duyệt có sẵn, vd. `PW_CHANNEL=msedge npm run e2e` (hoặc `chrome`).
 * - CI: `npx playwright install --with-deps chromium` rồi `npm run e2e`.
 * - Với backend thật: chạy backend + `npm start`, rồi `E2E_BASE_URL=http://localhost:4200 npm run e2e`.
 */
const port = Number(process.env['E2E_PORT'] ?? 4300);
const channel = process.env['PW_CHANNEL'] || undefined;
const externalBaseUrl = process.env['E2E_BASE_URL'];

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: externalBaseUrl ?? `http://localhost:${port}`,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], channel } },
    { name: 'mobile', use: { ...devices['Pixel 7'], channel } },
  ],
  webServer: externalBaseUrl
    ? undefined
    : {
        command: `npx ng serve --configuration mock --port ${port}`,
        url: `http://localhost:${port}`,
        reuseExistingServer: !process.env['CI'],
        timeout: 180_000,
        env: { NG_CLI_ANALYTICS: 'false' },
      },
});
