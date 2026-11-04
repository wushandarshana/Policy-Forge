import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

// E2E runs against the packaged app (SPA + /api in one Spring Boot process on 8080), see e2e/CLAUDE.md.
// `webServer` starts the backend through the Maven wrapper; `./mvnw spring-boot:run` also builds the SPA into
// target/classes/static (frontend-maven-plugin, docs/architecture.md section 6).
const isWindows = process.platform === 'win32';

// CI starts the packaged JAR built by the build job instead of running Maven in the Playwright container. PF_E2E_JAR is an
// absolute path, or one relative to this folder (frontend/). Unset, the Maven commands below are unchanged (C8).
const e2eJar = process.env.PF_E2E_JAR
  ? resolve(fileURLToPath(new URL('.', import.meta.url)), process.env.PF_E2E_JAR)
  : undefined;
const renewalAnyDate = '--policyforge.jobs.renewal-cycle.any-date=true';

// Pixel/iPhone descriptors default to WebKit. The mobile project must use Chromium (only Chromium is installed).
const { defaultBrowserType: _ignored, ...iphone13 } = devices['iPhone 13'];

export default defineConfig({
  testDir: './e2e',
  // Visual baselines are committed under e2e/__snapshots__/ (flat files, so scripts/ac_audit.py never sees a directory named *.spec.ts) and are platform-suffixed (spec UI section): -win32 from local runs, -linux from CI.
  // Specs never call toHaveScreenshot directly. They call expectVisual in e2e/support.ts, which applies the NFR-09.5 policy
  // (e2e/visual-baseline-policy.ts). PF_VISUAL_BASELINES is auto (default), require or update. Update mode is for the CI
  // job that generates the -linux baselines: run it as PF_VISUAL_BASELINES=update npx playwright test --update-snapshots=missing,
  // which writes only the missing files of the platform it runs on and never rewrites an existing baseline.
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
    // The browser tests run the renewal job for dates around today, so they lift the job's date bound (AC-07.8) with a
    // command-line argument. Production and the packaged demo never set it.
    command: e2eJar
      ? `java -jar "${e2eJar}" ${renewalAnyDate}`
      : isWindows
        ? `.\\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=${renewalAnyDate}"`
        : `./mvnw spring-boot:run '-Dspring-boot.run.arguments=${renewalAnyDate}'`,
    cwd: '../backend',
    url: 'http://localhost:8080/actuator/health',
    reuseExistingServer: !process.env.CI,
    timeout: 300_000,
    stdout: 'pipe',
    stderr: 'pipe',
  },
});
