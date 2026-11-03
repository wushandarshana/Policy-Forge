import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import {
  AC_04_1_PROFILE,
  createApplication,
  createPolicy,
  expectNoHorizontalScroll,
  openPolicy,
  signIn,
} from './support';

/** Fills and confirms a CHANGE_ADDRESS endorsement from the detail page, then waits for the detail page again. */
async function endorseAddress(page: Page, line1: string, city: string, postcode: string): Promise<void> {
  await page.getByTestId('policy-endorse-link').click();
  await expect(page).toHaveURL(/#\/policies\/PF-[A-Z]{2}-\d{4}-\d{6}\/endorse$/);
  await page.getByTestId('input-line1').fill(line1);
  await page.getByTestId('input-city').fill(city);
  await page.getByTestId('input-postcode').fill(postcode);
  await page.getByTestId('endorse-confirm').click();
  await expect(page).toHaveURL(/#\/policies\/PF-[A-Z]{2}-\d{4}-\d{6}$/);
  await expect(page.getByTestId('policy-number')).toBeVisible();
}

/** Fills the ADD_NOMINEE form on the Endorse page and submits it. The caller checks the outcome. */
async function submitNominee(page: Page, name: string, sharePercent: string): Promise<void> {
  await page.getByTestId('endorse-type').selectOption('ADD_NOMINEE');
  await page.getByTestId('input-nomineeName').fill(name);
  await page.getByTestId('input-nomineeRelationship').selectOption('SPOUSE');
  await page.getByTestId('input-nomineeSharePercent').fill(sharePercent);
  await page.getByTestId('endorse-confirm').click();
}

test('AC-05.1: customer1 issues an approved application and sees an ACTIVE policy in My Policies and on its detail page', async ({
  page,
  request,
}) => {
  const applicationId = await createApplication(request, AC_04_1_PROFILE);

  await page.goto(`/#/applications/${applicationId}`);
  await signIn(page, 'customer1');
  await expect(page.getByTestId('status-badge')).toHaveText('APPROVED');
  await page.getByTestId('issue-submit').click();

  await expect(page).toHaveURL(/#\/policies\/PF-TL-\d{4}-\d{6}$/);
  const policyNumber = await page.getByTestId('policy-number').innerText();
  expect(policyNumber).toMatch(/^PF-TL-\d{4}-\d{6}$/);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('ACTIVE');
  await expect(page.getByTestId('policy-premium')).toHaveText('6850.00');
  await expect(page.getByTestId('policy-sum-insured')).toHaveText('5000000.00');
  await expectNoHorizontalScroll(page);

  await page.getByTestId('nav-policies').click();
  await expect(page).toHaveURL(/#\/policies$/);
  const card = page.getByTestId(`policy-card-${policyNumber}`);
  await expect(card).toBeVisible();
  await expect(page.getByTestId(`policy-card-${policyNumber}-status`)).toHaveText('ACTIVE');
  await expect(page.getByTestId(`policy-card-${policyNumber}-premium`)).toHaveText('6850.00');
  await expect(page.getByTestId(`policy-card-${policyNumber}-sum-insured`)).toHaveText('5000000.00');
  await expect(page.getByTestId(`policy-card-${policyNumber}-endorsements`)).toHaveText('0');
  await expectNoHorizontalScroll(page);
  await expect(card).toHaveScreenshot('my-policies-card.png', {
    mask: [card.locator('.card-title'), page.getByTestId(`policy-card-${policyNumber}-next-due`)],
    animations: 'disabled',
  });
});

test('AC-06.1: an address change shows ENDORSED and the Versions tab keeps v1 with the old address and v2 with the new one', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  await openPolicy(page, policyNumber);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('ACTIVE');

  await endorseAddress(page, '99 New Road', 'Mumbai', '400001');

  await expect(page.getByTestId('policy-status-badge')).toHaveText('ENDORSED');
  await expect(page.getByTestId('version-1-address')).toHaveText('1 Demo Street, Pune, 411001');
  await expect(page.getByTestId('version-2-address')).toHaveText('99 New Road, Mumbai, 400001');
  await expectNoHorizontalScroll(page);
  await expect(page.getByTestId('tabpanel-versions')).toHaveScreenshot('policy-versions-tab.png', {
    mask: [page.getByTestId('version-1-effective-date'), page.getByTestId('version-2-effective-date')],
    animations: 'disabled',
  });
});

test('AC-06.2: a sum insured change to 7000000.00 effective on the term start previews 9490.00 and 2640.00 and stores that adjustment', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  await openPolicy(page, policyNumber);
  // P-12: the term start read from the page makes the adjustment the full 9490.00 - 6850.00, whatever the clock says.
  const termStart = await page.getByTestId('policy-effective-date').innerText();

  await page.getByTestId('policy-endorse-link').click();
  await page.getByTestId('endorse-type').selectOption('CHANGE_SUM_INSURED');
  await expect(page.getByTestId('endorse-confirm')).toBeDisabled();
  await page.getByTestId('input-newSumInsured').fill('7000000.00');
  await page.getByTestId('input-effectiveDate').fill(termStart);
  await page.getByTestId('endorse-preview').click();

  await expect(page.getByTestId('preview-current-premium')).toHaveText('6850.00');
  await expect(page.getByTestId('preview-new-premium')).toHaveText('9490.00');
  await expect(page.getByTestId('preview-adjustment')).toHaveText('2640.00');
  await expectNoHorizontalScroll(page);
  await expect(page.getByTestId('endorse-form')).toHaveScreenshot('endorse-sum-insured-preview.png', {
    mask: [page.getByTestId('input-effectiveDate'), page.getByTestId('preview-effective-date')],
    animations: 'disabled',
  });

  await page.getByTestId('endorse-confirm').click();
  await expect(page.getByTestId('policy-number')).toHaveText(policyNumber);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('ENDORSED');
  await expect(page.getByTestId('policy-premium')).toHaveText('9490.00');
  await expect(page.getByTestId('policy-sum-insured')).toHaveText('7000000.00');
  await page.getByTestId('tab-endorsements').click();
  await expect(page.getByTestId('endorsement-1-adjustment')).toHaveText('2640.00');
  await expect(page.getByTestId('endorsement-1-new-premium')).toHaveText('9490.00');
});

test('AC-06.5: nominees of 80% then 30% show the share-exceeded message and no second endorsement is added', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  await openPolicy(page, policyNumber);

  await page.getByTestId('policy-endorse-link').click();
  await submitNominee(page, 'Meera Test', '80');
  await expect(page).toHaveURL(/#\/policies\/PF-TL-\d{4}-\d{6}$/);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('ENDORSED');

  await page.getByTestId('policy-endorse-link').click();
  await submitNominee(page, 'Ravi Test', '30');

  await expect(page.getByTestId('endorse-error')).toHaveAttribute('data-code', 'NOMINEE_SHARE_EXCEEDED');
  await expect(page.getByTestId('endorse-error')).toContainText('100%');
  await expect(page).toHaveURL(/\/endorse$/);

  await page.getByTestId('endorse-back-link').click();
  await expect(page.getByTestId('policy-number')).toHaveText(policyNumber);
  await page.getByTestId('tab-endorsements').click();
  await expect(page.getByTestId('endorsement-row-1')).toBeVisible();
  await expect(page.getByTestId('endorsement-row-2')).toHaveCount(0);
});

test('AC-06.6: three endorsements show sequence 1, 2 and 3 linked to versions 2, 3 and 4', async ({ page, request }) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  await openPolicy(page, policyNumber);

  await endorseAddress(page, '1 First Road', 'Mumbai', '400001');
  await endorseAddress(page, '2 Second Road', 'Delhi', '110001');
  await endorseAddress(page, '3 Third Road', 'Chennai', '600001');

  await page.getByTestId('tab-endorsements').click();
  await expect(page.getByTestId('endorsement-1-versions')).toHaveText('v1 → v2');
  await expect(page.getByTestId('endorsement-2-versions')).toHaveText('v2 → v3');
  await expect(page.getByTestId('endorsement-3-versions')).toHaveText('v3 → v4');
  await expect(page.getByTestId('endorsement-row-4')).toHaveCount(0);

  await page.getByTestId('tab-versions').click();
  await expect(page.getByTestId('version-row-4')).toBeVisible();
  await expect(page.getByTestId('version-4-address')).toHaveText('3 Third Road, Chennai, 600001');
});

test('AC-01.13: a signed-in customer quote request carries an Authorization header and a signed-out one does not', async ({
  page,
}) => {
  const isQuoteCreate = (url: string, method: string) => new URL(url).pathname === '/api/quotes' && method === 'POST';
  const requestQuote = async () => {
    await page.getByTestId('product-select').selectOption('TERM_LIFE');
    await page.getByTestId('input-age').fill(String(AC_04_1_PROFILE.age));
    await page.getByTestId('input-sumInsured').fill(AC_04_1_PROFILE.sumInsured);
    await page.getByTestId('input-termYears').selectOption('20');
    await page.getByTestId('input-bmi').fill(AC_04_1_PROFILE.bmi);
    const sent = page.waitForRequest((r) => isQuoteCreate(r.url(), r.method()));
    await page.getByTestId('submit-quote').click();
    const headers = await (await sent).allHeaders();
    await expect(page.getByTestId('quote-apply')).toBeVisible();
    return headers;
  };

  await page.goto('/');
  const anonymous = await requestQuote();
  expect(anonymous['authorization']).toBeUndefined();

  // Hash navigation keeps the in-memory session, so the same page can sign in and quote again.
  await page.getByTestId('nav-policies').click();
  await signIn(page, 'customer1');
  await expect(page.getByTestId('nav-user')).toHaveText('customer1');
  await page.getByTestId('nav-quote').click();
  const signedIn = await requestQuote();
  expect(signedIn['authorization']).toMatch(/^Basic /);
});
