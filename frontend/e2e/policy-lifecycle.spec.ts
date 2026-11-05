import { fileURLToPath } from 'node:url';
import { expect, test } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';
import {
  AC_04_1_PROFILE,
  basic,
  createApplication,
  createPolicy,
  expectNoHorizontalScroll,
  openPolicy,
  policyExpiry,
  runRenewalCycle,
  shiftDate,
  signIn,
  withRenewalLock,
  expectVisual,
} from './support';

/** Hides policy numbers and dates in the visual snapshots (see the file for why it is not `mask`). */
const SNAPSHOT_MASK = fileURLToPath(new URL('./snapshot-mask.css', import.meta.url));

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
  await expectVisual(card, 'my-policies-card.png', {
    stylePath: SNAPSHOT_MASK,
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
  await expectVisual(page.getByTestId('tabpanel-versions'), 'policy-versions-tab.png', {
    stylePath: SNAPSHOT_MASK,
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
  await expectVisual(page.getByTestId('endorse-form'), 'endorse-sum-insured-preview.png', {
    stylePath: SNAPSHOT_MASK,
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

/** Creates a policy and runs the cycle on its own expiry minus 30 days, the first day its offer is made (P-12). */
async function policyWithOffer(request: APIRequestContext) {
  return withRenewalLock(async () => {
    const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
    const expiry = await policyExpiry(request, policyNumber);
    const run = await runRenewalCycle(request, shiftDate(expiry, -30));
    return { policyNumber, expiry, run };
  });
}

test('AC-07.1: the cycle on expiry minus 30 days lists the policy in offersCreated and the Renew panel shows the next term from expiry plus 1 at 6850.00', async ({
  page,
  request,
}) => {
  test.setTimeout(120_000);
  const { policyNumber, expiry, run } = await policyWithOffer(request);

  expect(run.runDate).toBe(shiftDate(expiry, -30));
  expect(run.offersCreated).toContain(policyNumber);
  expect(run.lapsed).not.toContain(policyNumber);

  await openPolicy(page, policyNumber);
  await expect(page.getByTestId('renew-panel')).toBeVisible();
  await expect(page.getByTestId('renew-start')).toHaveText(shiftDate(expiry, 1));
  await expect(page.getByTestId('renew-premium')).toHaveText('6850.00');
  await expect(page.getByTestId('policy-status-badge')).toHaveText('ACTIVE');
  await expectNoHorizontalScroll(page);
  await expectVisual(page.getByTestId('renew-panel'), 'renew-panel-offer.png', {
    stylePath: SNAPSHOT_MASK,
    animations: 'disabled',
  });
});

test('AC-07.5: paying the renewal shows RENEWED, hides the Renew panel, ends the Status tab with RENEWAL_PAID and adds version 2', async ({
  page,
  request,
}) => {
  test.setTimeout(120_000);
  const { policyNumber } = await policyWithOffer(request);
  await openPolicy(page, policyNumber);
  await expect(page.getByTestId('renew-panel')).toBeVisible();

  await page.getByTestId('renew-pay').click();

  await expect(page.getByTestId('policy-status-badge')).toHaveText('RENEWED');
  await expect(page.getByTestId('renew-panel')).toHaveCount(0);
  await page.getByTestId('tab-status').click();
  await expect(page.locator('[data-testid^="transition-row-"]')).toHaveCount(2);
  await expect(page.getByTestId('transition-1-to')).toHaveText('RENEWED');
  await expect(page.getByTestId('transition-1-reason')).toHaveText('RENEWAL_PAID');
  await page.getByTestId('tab-versions').click();
  await expect(page.getByTestId('version-row-2')).toBeVisible();
  await expect(page.getByTestId('version-2-premium')).toHaveText('6850.00');
  await expectNoHorizontalScroll(page);
});

test('AC-08.2: a same-day cancel previews 6850.00, fee 0.00 and cooling-off, then confirms to CANCELLED with one 6850.00 row in the Refunds tab', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  await openPolicy(page, policyNumber);

  await page.getByTestId('cancel-reason').selectOption('NO_LONGER_NEEDED');
  await page.getByTestId('cancel-preview').click();

  await expect(page.getByTestId('refund-preview-amount')).toHaveText('6850.00');
  await expect(page.getByTestId('refund-preview-fee')).toHaveText('0.00');
  await expect(page.getByTestId('refund-preview-cooling-off')).toHaveText('true');
  await expectNoHorizontalScroll(page);
  await expectVisual(page.getByTestId('refund-preview'), 'cancel-refund-preview.png', {
    stylePath: SNAPSHOT_MASK,
    animations: 'disabled',
  });

  await page.getByTestId('cancel-confirm').click();
  await expect(page.getByTestId('policy-status-badge')).toHaveText('CANCELLED');
  await page.getByTestId('tab-refunds').click();
  await expect(page.locator('[data-testid^="refund-row-"]')).toHaveCount(1);
  await expect(page.getByTestId('refund-1-amount')).toHaveText('6850.00');
  await expect(page.getByTestId('refund-1-fee')).toHaveText('0.00');
  await expectNoHorizontalScroll(page);
  await expectVisual(page.getByTestId('tabpanel-refunds'), 'refunds-tab.png', {
    stylePath: SNAPSHOT_MASK,
    animations: 'disabled',
  });
});

test('AC-08.4: after cancelling no Cancel, Renew or Endorse action is offered, a second cancel gets 409 and the Refunds tab keeps one row', async ({
  page,
  request,
}) => {
  const { policyNumber } = await createPolicy(request, AC_04_1_PROFILE);
  const cancel = () =>
    request.post(`/api/policies/${policyNumber}/cancel`, {
      headers: { Authorization: basic('customer1') },
      data: { reason: 'NO_LONGER_NEEDED' },
    });
  expect((await cancel()).status()).toBe(201);

  await openPolicy(page, policyNumber);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('CANCELLED');
  await expect(page.getByTestId('cancel-panel')).toHaveCount(0);
  await expect(page.getByTestId('renew-panel')).toHaveCount(0);
  await expect(page.getByTestId('policy-endorse-link')).toHaveCount(0);

  expect((await cancel()).status()).toBe(409);

  // Hash navigation keeps the in-memory session, so going out and back loads the detail again.
  await page.getByTestId('nav-policies').click();
  await page.getByTestId(`policy-card-${policyNumber}-link`).click();
  await expect(page.getByTestId('policy-number')).toHaveText(policyNumber);
  await expect(page.getByTestId('policy-status-badge')).toHaveText('CANCELLED');
  await page.getByTestId('tab-refunds').click();
  await expect(page.locator('[data-testid^="refund-row-"]')).toHaveCount(1);
  await expect(page.getByTestId('refund-1-amount')).toHaveText('6850.00');
});
