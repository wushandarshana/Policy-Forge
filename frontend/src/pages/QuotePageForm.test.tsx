import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { QuotePage } from './QuotePage';

const PRODUCTS = [
  { productCode: 'TERM_LIFE', productName: 'Term Life', ratingModel: 'TERM_LIFE', activeVersion: 1 },
  { productCode: 'MOTOR', productName: 'Motor', ratingModel: 'MOTOR', activeVersion: 1 },
  { productCode: 'HOUSEHOLD', productName: 'Household', ratingModel: 'HOUSEHOLD', activeVersion: 1 },
];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

async function openProduct(code: string) {
  const user = userEvent.setup();
  render(<QuotePage />);
  const select = await screen.findByTestId('product-select');
  await waitFor(() => expect(within(select).getAllByRole('option')).toHaveLength(3));
  await user.selectOptions(select, code);
  return user;
}

function inputIds(): string[] {
  return screen
    .getAllByTestId(/^input-/)
    .map((el) => el.getAttribute('data-testid') as string)
    .sort();
}

describe('QuotePage dynamic form', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('AC-01.1: TERM_LIFE form shows exactly the six quote-spec risk inputs', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(200, PRODUCTS)));
    await openProduct('TERM_LIFE');
    expect(inputIds()).toEqual(
      ['input-age', 'input-bmi', 'input-healthDeclarations', 'input-smoker', 'input-sumInsured', 'input-termYears'].sort(),
    );
  });

  it('AC-01.2: MOTOR form shows exactly the six quote-spec risk inputs', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(200, PRODUCTS)));
    await openProduct('MOTOR');
    expect(inputIds()).toEqual(
      [
        'input-claimsLast3Years',
        'input-driverAge',
        'input-noClaimBonusYears',
        'input-vehicleAgeYears',
        'input-vehicleUse',
        'input-vehicleValue',
      ].sort(),
    );
  });

  it('AC-01.3: HOUSEHOLD form shows exactly the seven quote-spec risk inputs', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => json(200, PRODUCTS)));
    await openProduct('HOUSEHOLD');
    expect(inputIds()).toEqual(
      [
        'input-buildingSumInsured',
        'input-constructionType',
        'input-contentsSumInsured',
        'input-floodZone',
        'input-hasFloodDefences',
        'input-hasSecuritySystem',
        'input-propertyAgeYears',
      ].sort(),
    );
  });

  it('AC-01.2: MOTOR submit sends vehicleValue as a string and enumerations as selected', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL, _init?: RequestInit) => {
      if (String(input).endsWith('/api/products')) return json(200, PRODUCTS);
      return json(201, {
        quoteId: 'Q-123e4567-e89b-42d3-a456-426614174001',
        productCode: 'MOTOR',
        ruleVersion: 1,
        sumInsured: '800000.00',
        annualPremium: '15150.00',
        breakdown: [{ factor: 'policyFee', value: '150.00' }],
        validUntil: '2026-11-27',
      });
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = await openProduct('MOTOR');
    await user.type(screen.getByTestId('input-vehicleValue'), '800000.00');
    await user.type(screen.getByTestId('input-vehicleAgeYears'), '2');
    await user.type(screen.getByTestId('input-driverAge'), '30');
    await user.type(screen.getByTestId('input-claimsLast3Years'), '0');
    await user.selectOptions(screen.getByTestId('input-noClaimBonusYears'), '2');
    await user.selectOptions(screen.getByTestId('input-vehicleUse'), 'PRIVATE');
    await user.click(screen.getByTestId('submit-quote'));

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent(/^15150\.00$/);
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')!;
    expect(JSON.parse(String(post[1]!.body))).toEqual({
      productCode: 'MOTOR',
      riskInputs: {
        vehicleValue: '800000.00',
        vehicleAgeYears: 2,
        driverAge: 30,
        claimsLast3Years: 0,
        noClaimBonusYears: 2,
        vehicleUse: 'PRIVATE',
      },
    });
  });
});
