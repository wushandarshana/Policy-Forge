import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { AC_04_2_PROFILE, AC_04_3_PROFILE, createApplication, signIn } from './support';
import type { User } from './support';

// Every test makes its own application and finds it by ID (P-8). The seed and other tests' rows may be in the queue.

async function openWorkbench(page: Page, user: User, applicationId: string) {
  await page.goto('/#/workbench');
  await signIn(page, user);
  await expect(page.getByTestId(`queue-row-${applicationId}`)).toBeVisible();
}

async function offeredCodes(page: Page, testId: string): Promise<string[]> {
  const values = await page
    .getByTestId(testId)
    .locator('option')
    .evaluateAll((options) => options.map((option) => (option as HTMLOptionElement).value));
  return values.filter((value) => value !== '');
}

test('AC-09.2: underwriter1 approves their own REFERRED case with UW-MAN-001 and it shows APPROVED', async ({
  page,
  request,
}) => {
  const applicationId = await createApplication(request, AC_04_2_PROFILE);
  await openWorkbench(page, 'underwriter1', applicationId);

  await page.getByTestId(`select-${applicationId}`).click();
  const detail = page.getByTestId('case-detail');
  await expect(detail).toBeVisible();
  await expect(page.getByTestId('case-aadhaar')).toHaveText('**********1234');
  await expect(page.getByTestId('case-pan')).toHaveText('******234F');
  await expect(detail).toHaveScreenshot('workbench-case-detail.png', {
    mask: [detail.locator('h2')],
    animations: 'disabled',
  });

  await page.getByTestId('decision-select').selectOption('APPROVE');
  await page.getByTestId('reason-select').selectOption('UW-MAN-001');
  await page.getByTestId('comment-input').fill('Acceptable after review');
  await page.getByTestId('submit-decision').click();

  const result = page.getByTestId('decision-result');
  await expect(result).toHaveAttribute('data-status', 'APPROVED');
  await expect(result).toContainText(applicationId);
  await expect(page.getByTestId(`queue-row-${applicationId}`)).toHaveCount(0);
});

test('AC-09.3: admin1 overrides their own DECLINED case with UW-OVR-001 and it shows APPROVED', async ({
  page,
  request,
}) => {
  const applicationId = await createApplication(request, AC_04_3_PROFILE);
  await openWorkbench(page, 'admin1', applicationId);
  await expect(page.getByTestId(`queue-row-${applicationId}`)).toHaveAttribute('data-status', 'DECLINED');

  await page.getByTestId(`select-${applicationId}`).click();
  await expect(page.getByTestId('override-form')).toBeVisible();
  await page.getByTestId('override-reason-select').selectOption('UW-OVR-001');
  await page.getByTestId('override-comment-input').fill('Commercial exception signed off');
  await page.getByTestId('override-action').click();

  const result = page.getByTestId('decision-result');
  await expect(result).toHaveAttribute('data-status', 'APPROVED');
  await expect(result).toContainText(applicationId);
});

test('AC-09.6: DECLINE offers only UW-MAN-010 and APPROVE offers only UW-MAN-001 and UW-MAN-002', async ({
  page,
  request,
}) => {
  const applicationId = await createApplication(request, AC_04_2_PROFILE);
  await openWorkbench(page, 'underwriter1', applicationId);
  await page.getByTestId(`select-${applicationId}`).click();
  await expect(page.getByTestId('decision-form')).toBeVisible();

  await page.getByTestId('decision-select').selectOption('DECLINE');
  expect(await offeredCodes(page, 'reason-select')).toEqual(['UW-MAN-010']);

  await page.getByTestId('decision-select').selectOption('APPROVE');
  expect(await offeredCodes(page, 'reason-select')).toEqual(['UW-MAN-001', 'UW-MAN-002']);
  await expect(page.getByTestId('reason-select')).toHaveValue('');
});

test('AC-09.7: the underwriter Workbench shows no Override action and no DECLINED rows', async ({ page, request }) => {
  const referredId = await createApplication(request, AC_04_2_PROFILE);
  const declinedId = await createApplication(request, AC_04_3_PROFILE);
  await openWorkbench(page, 'underwriter1', referredId);

  await expect(page.getByTestId(`queue-row-${referredId}`)).toHaveAttribute('data-status', 'REFERRED');
  await expect(page.getByTestId(`queue-row-${declinedId}`)).toHaveCount(0);
  await expect(page.locator('[data-testid^="queue-row-"][data-status="DECLINED"]')).toHaveCount(0);

  await page.getByTestId(`select-${referredId}`).click();
  await expect(page.getByTestId('decision-form')).toBeVisible();
  await expect(page.getByTestId('override-action')).toHaveCount(0);
  await expect(page.getByTestId('override-form')).toHaveCount(0);
});
