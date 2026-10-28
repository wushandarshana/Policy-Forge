import { expect, test } from '@playwright/test';

// Synthetic input only. AC-01.1: TERM_LIFE v1, age 35, non-smoker, sum insured 5,000,000.00, 20-year term.
test('AC-01.1: term life quote shows 6850.00', async ({ page }) => {
  await page.goto('/');

  const productSelect = page.getByTestId('product-select');
  await expect(productSelect.locator('option')).toHaveCount(3);
  await productSelect.selectOption('TERM_LIFE');

  await page.getByTestId('input-age').fill('35');
  await page.getByTestId('input-sumInsured').fill('5000000.00');
  await page.getByTestId('input-termYears').selectOption('20');
  await page.getByTestId('input-bmi').fill('24.5');
  await page.getByTestId('submit-quote').click();

  await expect(page.getByTestId('quote-premium')).toHaveText('6850.00');
  await expect(page.getByTestId('quote-rule-version')).toHaveText('1');
  await expect(page.getByTestId('quote-id')).toHaveText(
    /^Q-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
  await expect(page.getByTestId('quote-apply')).toBeVisible();
  await expect(page.getByTestId('breakdown-row-policyFee')).toBeVisible();
});
