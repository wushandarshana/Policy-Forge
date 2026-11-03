import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MyPoliciesPage } from './MyPoliciesPage';
import { basic, calls, CUSTOMER, header, installFetch, jsonResponse, policySummary, problem, renderWithSession } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('MyPoliciesPage', () => {
  it('AC-05.1: renders every C2 field of every policy verbatim, in API order, with a link to the detail page', async () => {
    const first = policySummary({
      policyNumber: 'PF-MO-2026-000007',
      productCode: 'MOTOR',
      status: 'ENDORSED',
      sumInsured: '900000.00',
      annualPremium: '17025.00',
      nextPremiumDueDate: '2027-11-02',
      endorsementCount: 2,
    });
    const second = policySummary();
    const fetchMock = installFetch(() => jsonResponse(200, [first, second]));

    renderWithSession(<MyPoliciesPage />, CUSTOMER);

    const list = await screen.findByTestId('policies-list');
    const cards = within(list).getAllByTestId(/^policy-card-PF-[A-Z]{2}-\d{4}-\d{6}$/);
    expect(cards.map((card) => card.getAttribute('data-testid'))).toEqual([
      'policy-card-PF-MO-2026-000007',
      'policy-card-PF-TL-2026-000001',
    ]);

    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-product')).toHaveTextContent('MOTOR');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-status')).toHaveTextContent('ENDORSED');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-sum-insured')).toHaveTextContent('900000.00');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-premium')).toHaveTextContent('17025.00');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-next-due')).toHaveTextContent('2027-11-02');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-endorsements')).toHaveTextContent('2');
    expect(screen.getByTestId('policy-card-PF-MO-2026-000007-link')).toHaveAttribute('href', '#/policies/PF-MO-2026-000007');

    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-product')).toHaveTextContent('TERM_LIFE');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-status')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-sum-insured')).toHaveTextContent('5000000.00');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-next-due')).toHaveTextContent('2027-11-01');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-endorsements')).toHaveTextContent('0');
    expect(screen.getByTestId('policy-card-PF-TL-2026-000001-link')).toHaveAttribute('href', '#/policies/PF-TL-2026-000001');

    const [call] = calls(fetchMock);
    expect(call.url).toBe('/api/policies/mine');
    expect(header(call, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-05.1: shows an empty state when the caller has no policies', async () => {
    installFetch(() => jsonResponse(200, []));

    renderWithSession(<MyPoliciesPage />, CUSTOMER);

    expect(await screen.findByTestId('policies-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('policies-list')).not.toBeInTheDocument();
  });

  it('AC-05.1: shows a fixed error message when the list cannot be loaded', async () => {
    installFetch(() => problem(500, { detail: 'stack trace with PF-TL-2026-000001' }));

    renderWithSession(<MyPoliciesPage />, CUSTOMER);

    const error = await screen.findByTestId('policies-error');
    expect(error).toHaveTextContent('Please try again.');
    expect(error).not.toHaveTextContent('stack trace');
  });
});
