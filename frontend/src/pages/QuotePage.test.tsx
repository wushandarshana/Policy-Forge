import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { QuotePage } from './QuotePage';

// Synthetic fixtures only. Money is always a decimal string, exactly as the API sends it (contract C2).
const PRODUCTS = [
  { productCode: 'TERM_LIFE', productName: 'Term Life', ratingModel: 'TERM_LIFE', activeVersion: 1 },
  { productCode: 'MOTOR', productName: 'Motor', ratingModel: 'MOTOR', activeVersion: 1 },
  { productCode: 'HOUSEHOLD', productName: 'Household', ratingModel: 'HOUSEHOLD', activeVersion: 1 },
];

const TERM_LIFE_QUOTE = {
  quoteId: 'Q-123e4567-e89b-42d3-a456-426614174000',
  productCode: 'TERM_LIFE',
  ruleVersion: 1,
  sumInsured: '5000000.00',
  annualPremium: '6850.00',
  breakdown: [
    { factor: 'baseRatePerThousand', value: '1.20' },
    { factor: 'smokerFactor', value: '1.00' },
    { factor: 'termFactor', value: '1.10' },
    { factor: 'policyFee', value: '250.00' },
  ],
  validUntil: '2026-11-27',
};

function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

type FetchMock = ReturnType<typeof vi.fn>;

function installFetch(quoteResponse: () => Response): FetchMock {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input);
    if (url.endsWith('/api/products')) return jsonResponse(200, PRODUCTS);
    if (url.endsWith('/api/quotes') && init?.method === 'POST') return quoteResponse();
    return jsonResponse(404, { title: 'Not Found', status: 404 }, 'application/problem+json');
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function postedBody(fetchMock: FetchMock): Record<string, unknown> {
  const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST');
  expect(call).toBeDefined();
  return JSON.parse(String((call![1] as RequestInit).body)) as Record<string, unknown>;
}

async function chooseProduct(user: ReturnType<typeof userEvent.setup>, code: string) {
  const select = await screen.findByTestId('product-select');
  await waitFor(() => expect(within(select).getAllByRole('option')).toHaveLength(3));
  await user.selectOptions(select, code);
}

async function fillTermLife(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByTestId('input-age'), '35');
  await user.type(screen.getByTestId('input-sumInsured'), '5000000.00');
  await user.selectOptions(screen.getByTestId('input-termYears'), '20');
  await user.type(screen.getByTestId('input-bmi'), '24.5');
}

describe('QuotePage', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('AC-01.1: shows the premium 6850.00 verbatim with rule version 1', async () => {
    const fetchMock = installFetch(() => jsonResponse(201, TERM_LIFE_QUOTE));
    const user = userEvent.setup();
    render(<QuotePage />);

    await chooseProduct(user, 'TERM_LIFE');
    await fillTermLife(user);
    await user.click(screen.getByTestId('submit-quote'));

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent(/^6850\.00$/);
    expect(screen.getByTestId('quote-rule-version')).toHaveTextContent(/^1$/);
    expect(screen.getByTestId('quote-sum-insured')).toHaveTextContent(/^5000000\.00$/);
    expect(screen.getByTestId('quote-valid-until')).toHaveTextContent(/^2026-11-27$/);
    expect(screen.getByTestId('quote-id')).toHaveTextContent(TERM_LIFE_QUOTE.quoteId);

    // The Apply call to action carries the quote ID.
    const apply = screen.getByTestId('quote-apply');
    expect(apply.getAttribute('href')).toContain(TERM_LIFE_QUOTE.quoteId);

    // Money inputs go out as the strings typed; integers as JSON numbers.
    expect(postedBody(fetchMock)).toEqual({
      productCode: 'TERM_LIFE',
      riskInputs: {
        age: 35,
        smoker: false,
        sumInsured: '5000000.00',
        termYears: 20,
        bmi: '24.5',
        healthDeclarations: [],
      },
    });
  });

  it('AC-01.11: health declarations are normalised to codes', async () => {
    const fetchMock = installFetch(() => jsonResponse(201, TERM_LIFE_QUOTE));
    const user = userEvent.setup();
    render(<QuotePage />);

    await chooseProduct(user, 'TERM_LIFE');
    await fillTermLife(user);
    const field = screen.getByTestId('field-healthDeclarations');
    expect(field).toHaveTextContent(/upper-case/i);
    expect(field).toHaveTextContent(/max 10/i);
    await user.type(screen.getByTestId('input-healthDeclarations'), 'diabetes, high blood pressure,  asthma-mild , ');
    await user.click(screen.getByTestId('submit-quote'));

    await screen.findByTestId('quote-premium');
    const risk = postedBody(fetchMock).riskInputs as Record<string, unknown>;
    expect(risk.healthDeclarations).toEqual(['DIABETES', 'HIGH_BLOOD_PRESSURE', 'ASTHMA_MILD']);
  });

  it('AC-01.11: an empty health declarations field sends an empty array', async () => {
    const fetchMock = installFetch(() => jsonResponse(201, TERM_LIFE_QUOTE));
    const user = userEvent.setup();
    render(<QuotePage />);

    await chooseProduct(user, 'TERM_LIFE');
    await fillTermLife(user);
    await user.type(screen.getByTestId('input-healthDeclarations'), ' , ,');
    await user.click(screen.getByTestId('submit-quote'));

    await screen.findByTestId('quote-premium');
    const risk = postedBody(fetchMock).riskInputs as Record<string, unknown>;
    expect(risk.healthDeclarations).toEqual([]);
  });

  it('AC-01.7: shows each per-field error next to the input it names', async () => {
    installFetch(() =>
      jsonResponse(
        400,
        {
          type: 'about:blank',
          title: 'Validation failed',
          status: 400,
          errors: [
            { field: 'sumInsured', message: 'must be between 500000.00 and 50000000.00' },
            { field: 'age', message: 'is required' },
          ],
        },
        'application/problem+json',
      ),
    );
    const user = userEvent.setup();
    render(<QuotePage />);

    await chooseProduct(user, 'TERM_LIFE');
    await user.type(screen.getByTestId('input-sumInsured'), '100.00');
    await user.click(screen.getByTestId('submit-quote'));

    const sumErr = await screen.findByTestId('error-sumInsured');
    expect(sumErr).toHaveTextContent('must be between 500000.00 and 50000000.00');
    expect(screen.getByTestId('error-age')).toHaveTextContent('is required');

    // The error sits in the same field wrapper as its input.
    const sumField = screen.getByTestId('input-sumInsured').closest('[data-testid="field-sumInsured"]');
    expect(sumField).not.toBeNull();
    expect(within(sumField as HTMLElement).getByTestId('error-sumInsured')).toBeInTheDocument();
    expect(screen.getByTestId('input-sumInsured')).toHaveAttribute('aria-invalid', 'true');

    // No result is shown for a rejected quote.
    expect(screen.queryByTestId('quote-premium')).not.toBeInTheDocument();
  });

  it('AC-01.7: lists an error whose field matches no input in the form summary', async () => {
    installFetch(() =>
      jsonResponse(
        400,
        { title: 'Validation failed', status: 400, errors: [{ field: 'sumInsured', message: 'out of range' }] },
        'application/problem+json',
      ),
    );
    const user = userEvent.setup();
    render(<QuotePage />);

    // HOUSEHOLD's sum insured is derived, so no input is named sumInsured.
    await chooseProduct(user, 'HOUSEHOLD');
    await user.click(screen.getByTestId('submit-quote'));

    expect(await screen.findByTestId('form-errors')).toHaveTextContent('sumInsured');
    expect(screen.getByTestId('form-errors')).toHaveTextContent('out of range');
  });

  it('AC-01.8: renders every breakdown factor returned, in order', async () => {
    installFetch(() => jsonResponse(201, TERM_LIFE_QUOTE));
    const user = userEvent.setup();
    render(<QuotePage />);

    await chooseProduct(user, 'TERM_LIFE');
    await fillTermLife(user);
    await user.click(screen.getByTestId('submit-quote'));

    await screen.findByTestId('quote-premium');
    const rows = screen.getAllByTestId(/^breakdown-row-/);
    expect(rows).toHaveLength(TERM_LIFE_QUOTE.breakdown.length);
    TERM_LIFE_QUOTE.breakdown.forEach((item, index) => {
      expect(rows[index]).toHaveAttribute('data-testid', `breakdown-row-${item.factor}`);
      expect(rows[index]).toHaveTextContent(item.factor);
      expect(within(rows[index]).getByTestId(`breakdown-value-${item.factor}`).textContent).toBe(item.value);
    });
  });
});
