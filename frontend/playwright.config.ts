import { defineConfig, devices } from '@playwright/test';

// E2E runs against the packaged app (SPA + /api in one Spring Boot process on 8080), see e2e/CLAUDE.md.
// `webServer` starts the backend through the Maven wrapper; `./mvnw spring-boot:run` also builds the SPA into
// target/classes/static (frontend-maven-plugin, docs/architecture.md section 6).
const isWindows = process.platform === 'win32';

// Pixel/iPhone descriptors default to WebKit. The mobile project must use Chromium (only Chromium is installed).
const { defaultBrowserType: _ignored, ...iphone13 } = devices['iPhone 13'];

export default defineConfig({
  testDir: './e2e',
  // Visual baselines are committed under e2e/__snapshots__/ (flat files, so scripts/ac_audit.py never sees a directory named *.spec.ts) and are platform-suffixed (spec UI section): Windows
  // baselines now, Linux CI baselines in Sprint 5.
  snapshotPathTemplate: '{testDir}/__snapshots__/{testFileName}-{arg}-{projectName}-{platform}{ext}',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  expect: {
    // Text anti-aliasing differs by a fraction of a pixel with the scroll offset and the page above the element (the
    // nav bar wraps on mobile), which moves about 1% of the pixels of an unchanged element. Layout changes move more.
    toHaveScreenshot: { maxDiffPixelRatio: 0.02 },
  },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://localhost:8080',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...iphone13, browserName: 'chromium' } },
  ],
  webServer: {
    command: isWindows ? '.\\mvnw.cmd spring-boot:run' : './mvnw spring-boot:run',
    cwd: '../backend',
    url: 'http://localhost:8080/actuator/health',
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
