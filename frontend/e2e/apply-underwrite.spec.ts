import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';
import { AC_04_1_PROFILE, AC_04_2_PROFILE, createQuote, fillKyc, signIn, expectVisual } from './support';
import type { TermLifeProfile } from './support';

/** Quotes through the Quote page, then follows its Apply link, so the shell routing is on the path. */
async function quoteThroughUi(page: Page, profile: TermLifeProfile): Promise<string> {
  await page.goto('/');
  await page.getByTestId('product-select').selectOption('TERM_LIFE');
  await page.getByTestId('input-age').fill(String(profile.age));
  if (profile.smoker) await page.getByTestId('input-smoker').check();
  await page.getByTestId('input-sumInsured').fill(profile.sumInsured);
  await page.getByTestId('input-termYears').selectOption('20');
  await page.getByTestId('input-bmi').fill(profile.bmi);
  await page.getByTestId('submit-quote').click();
  await expect(page.getByTestId('quote-apply')).toBeVisible();
  const quoteId = await page.getByTestId('quote-id').innerText();
  await page.getByTestId('quote-apply').click();
  await expect(page).toHaveURL(new RegExp(`#/apply\\?quoteId=${quoteId}`));
  return quoteId;
}

test('AC-04.1: customer1 quotes the auto-bind profile, applies and sees APPROVED with UW-OK', async ({ page }) => {
  const quoteId = await quoteThroughUi(page, AC_04_1_PROFILE);

  await signIn(page, 'customer1');
  await expect(page.getByTestId('apply-quote-id')).toHaveText(quoteId);
  await fillKyc(page, AC_04_1_PROFILE.age);
  await expectVisual(page.getByTestId('apply-form'), 'apply-form.png', {
    mask: [page.getByTestId('apply-quote-id'), page.getByTestId('input-dateOfBirth')],
    animations: 'disabled',
  });
  await page.getByTestId('submit-application').click();

  await expect(page).toHaveURL(/#\/applications\/A-/);
  await expect(page.getByTestId('status-badge')).toHaveText('APPROVED');
  await expect(page.getByTestId('application-decision')).toHaveText('AUTO_BIND');
  await expect(page.getByTestId('reason-item-UW-OK')).toContainText('No underwriting rule fired');
  await expectVisual(page.getByTestId('application-details'), 'application-status.png', {
    mask: [
      page.getByTestId('application-id'),
      page.getByTestId('application-quote-id'),
      page.getByTestId('application-submitted-at'),
    ],
    animations: 'disabled',
  });
});

test('AC-04.2: a smoker with sum insured 12,000,000.00 is REFERRED with UW-TL-002', async ({ page }) => {
  await quoteThroughUi(page, AC_04_2_PROFILE);

  await signIn(page, 'customer1');
  await fillKyc(page, AC_04_2_PROFILE.age);
  await page.getByTestId('submit-application').click();

  await expect(page).toHaveURL(/#\/applications\/A-/);
  await expect(page.getByTestId('status-badge')).toHaveText('REFERRED');
  await expect(page.getByTestId('application-decision')).toHaveText('MANUAL_REVIEW');
  await expect(page.getByTestId('reason-item-UW-TL-002')).toBeVisible();
});

test('AC-03.2: an invalid PAN, Aadhaar and postcode show field errors and the page does not navigate', async ({
  page,
  request,
}) => {
  const quoteId = await createQuote(request, AC_04_1_PROFILE);
  await page.goto(`/#/apply?quoteId=${quoteId}`);

  await signIn(page, 'customer1');
  await expect(page.getByTestId('apply-form')).toBeVisible();
  await fillKyc(page, AC_04_1_PROFILE.age, { pan: 'abcde1234f', aadhaar: '1234-5678', postcode: '41100' });
  await page.getByTestId('submit-application').click();

  await expect(page.getByTestId('field-pan').getByTestId('error-pan')).toBeVisible();
  await expect(page.getByTestId('field-aadhaar').getByTestId('error-aadhaar')).toBeVisible();
  await expect(page.getByTestId('field-postcode').getByTestId('error-postcode')).toBeVisible();
  await expect(page.getByTestId('error-fullName')).toHaveCount(0);
  await expect(page).toHaveURL(/#\/apply\?quoteId=/);
});
