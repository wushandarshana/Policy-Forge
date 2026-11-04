import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { AC_04_1_PROFILE, createPolicy, expectNoHorizontalScroll, policyExpiry, shiftDate, signIn } from './support';
import type { User } from './support';

/** Hides the dashboard figures in the visual snapshot (they change with every policy any test creates). */
const SNAPSHOT_MASK = fileURLToPath(new URL('./snapshot-mask.css', import.meta.url));

interface DashboardResponse {
  asOf: string;
  activePoliciesByProduct: Record<'TERM_LIFE' | 'MOTOR' | 'HOUSEHOLD', number>;
  premiumCollected: Record<'TERM_LIFE' | 'MOTOR' | 'HOUSEHOLD' | 'total', string>;
}

const PRODUCTS = ['TERM_LIFE', 'MOTOR', 'HOUSEHOLD'] as const;

/** Opens the dashboard route and signs in as `user`. The caller waits for what it needs. */
async function openDashboard(page: Page, user: User): Promise<void> {
  await page.goto('/#/admin/dashboard');
  await signIn(page, user);
}

/** Loads the dashboard for `asOf` and waits until the page shows that date (the old figures stay up until then). */
async function loadAsOf(page: Page, asOf: string): Promise<void> {
  await page.getByTestId('dashboard-asof').fill(asOf);
  await page.getByTestId('dashboard-load').click();
  await expect(page.getByTestId('dashboard-as-of')).toHaveText(`As of ${asOf}`);
}

test('AC-11.1: admin1 sees tiles and a per-product table equal to the dashboard response, in one column on mobile and side by side on desktop', async ({
  page,
}, testInfo) => {
  const loaded = page.waitForResponse(
    (response) => new URL(response.url()).pathname === '/api/admin/dashboard' && response.request().method() === 'GET',
  );
  await openDashboard(page, 'admin1');
  const response = await loaded;
  expect(response.status()).toBe(200);
  const dashboard = (await response.json()) as DashboardResponse;

  await expect(page.getByTestId('kpi-tiles')).toBeVisible();
  const active = PRODUCTS.reduce((sum, code) => sum + dashboard.activePoliciesByProduct[code], 0);
  await expect(page.getByTestId('kpi-active')).toHaveText(String(active));
  await expect(page.getByTestId('kpi-premium')).toHaveText(dashboard.premiumCollected.total);
  for (const code of PRODUCTS) {
    await expect(page.getByTestId(`product-row-${code}`)).toBeVisible();
    await expect(page.getByTestId(`product-${code}-active`)).toHaveText(String(dashboard.activePoliciesByProduct[code]));
    await expect(page.getByTestId(`product-${code}-premium`)).toHaveText(dashboard.premiumCollected[code]);
  }

  await expect(page.getByTestId('kpi-tiles').locator('[data-testid^="kpi-tile-"]')).toHaveCount(2);
  const first = await page.getByTestId('kpi-tile-active').boundingBox();
  const second = await page.getByTestId('kpi-tile-premium').boundingBox();
  expect(first).not.toBeNull();
  expect(second).not.toBeNull();
  if (testInfo.project.name === 'mobile-chromium') {
    expect(second!.x).toBeCloseTo(first!.x, 0);
    expect(second!.y).toBeGreaterThanOrEqual(first!.y + first!.height - 1);
  } else {
    expect(second!.y).toBeCloseTo(first!.y, 0);
    expect(second!.x).toBeGreaterThanOrEqual(first!.x + first!.width - 1);
  }
  await expectNoHorizontalScroll(page);
  await expect(page.getByTestId('kpi-tiles')).toHaveScreenshot('dashboard-kpi-tiles.png', {
    stylePath: SNAPSHOT_MASK,
    animations: 'disabled',
  });
});

test('AC-11.3: asOf 20 days before the term end lists the policy in the renewal pipeline and 90 days before does not', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  const termEnd = await policyExpiry(request, policyNumber);
  await openDashboard(page, 'admin1');
  await expect(page.getByTestId('kpi-tiles')).toBeVisible();

  await loadAsOf(page, shiftDate(termEnd, -20));
  await expect(page.getByTestId(`pipeline-row-${policyNumber}`)).toBeVisible();
  await expect(page.getByTestId(`pipeline-${policyNumber}-paid`)).toHaveText('Unpaid');
  await expectNoHorizontalScroll(page);

  await loadAsOf(page, shiftDate(termEnd, -90));
  await expect(page.getByTestId(`pipeline-row-${policyNumber}`)).toHaveCount(0);
});

test('AC-11.4: asOf one day after the term end lists the policy in the lapse forecast', async ({ page, request }) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  const termEnd = await policyExpiry(request, policyNumber);
  await openDashboard(page, 'admin1');
  await expect(page.getByTestId('kpi-tiles')).toBeVisible();

  await expect(page.getByTestId(`forecast-${policyNumber}`)).toHaveCount(0);
  await loadAsOf(page, shiftDate(termEnd, 1));

  await expect(page.getByTestId(`forecast-${policyNumber}`)).toHaveText(policyNumber);
  await expect(page.getByTestId('forecast-count')).not.toHaveText('0');
  await expectNoHorizontalScroll(page);
});

test('AC-11.5: customer1 on the dashboard route sees the not-authorised message and no figures', async ({ page }) => {
  await openDashboard(page, 'customer1');

  await expect(page.getByTestId('dashboard-error')).toHaveText('You are not authorised to view the portfolio dashboard.');
  await expect(page.getByTestId('kpi-tiles')).toHaveCount(0);
  await expect(page.getByTestId('product-table')).toHaveCount(0);
});
