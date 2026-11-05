import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PolicyDetailPage } from './PolicyDetailPage';
import type { PolicyDetail, PolicyStatus } from '../api';
import {
  basic,
  calls,
  CUSTOMER,
  endorsement,
  header,
  installFetch,
  jsonResponse,
  POLICY_NUMBER,
  policyDetail,
  policyHeader,
  policyVersion,
  problem,
  renderWithSession,
  transition,
} from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

function show(detail: PolicyDetail) {
  const fetchMock = installFetch(() => jsonResponse(200, detail));
  renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);
  return fetchMock;
}

/** A policy with the AC-06.6 history: three endorsements, versions 1 to 4, status ENDORSED. */
function endorsedDetail(): PolicyDetail {
  const nominee = { name: 'Ravi Nominee', relationship: 'SPOUSE', sharePercent: 60 };
  return policyDetail({
    policy: policyHeader({ status: 'ENDORSED', currentVersionNo: 4, endorsementCount: 3, sumInsured: '7000000.00', annualPremium: '9490.00' }),
    versions: [
      policyVersion(),
      policyVersion({
        versionNo: 2,
        address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' },
        createdAt: '2026-11-02T10:00:00Z',
      }),
      policyVersion({
        versionNo: 3,
        address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' },
        nominees: [nominee],
        createdAt: '2026-11-03T10:00:00Z',
      }),
      policyVersion({
        versionNo: 4,
        sumInsured: '7000000.00',
        annualPremium: '9490.00',
        address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' },
        nominees: [nominee],
        createdAt: '2026-11-04T10:00:00Z',
      }),
    ],
    endorsements: [
      endorsement({ sequenceNo: 1, beforeVersionNo: 1, afterVersionNo: 2 }),
      endorsement({
        sequenceNo: 2,
        type: 'ADD_NOMINEE',
        changes: { nominee },
        beforeVersionNo: 2,
        afterVersionNo: 3,
        effectiveDate: '2026-11-03',
      }),
      endorsement({
        sequenceNo: 3,
        type: 'CHANGE_SUM_INSURED',
        changes: { newSumInsured: '7000000.00' },
        beforeVersionNo: 3,
        afterVersionNo: 4,
        newAnnualPremium: '9490.00',
        adjustment: '2640.00',
        effectiveDate: '2026-11-04',
      }),
    ],
    transitions: [
      transition(),
      transition({ fromStatus: 'ACTIVE', toStatus: 'ENDORSED', reason: 'CHANGE_ADDRESS', at: '2026-11-02T10:00:00Z' }),
      transition({ fromStatus: 'ENDORSED', toStatus: 'ENDORSED', reason: 'ADD_NOMINEE', at: '2026-11-03T10:00:00Z' }),
      transition({ fromStatus: 'ENDORSED', toStatus: 'ENDORSED', reason: 'CHANGE_SUM_INSURED', at: '2026-11-04T10:00:00Z' }),
    ],
  });
}

describe('PolicyDetailPage', () => {
  it('AC-05.1: the header shows the number, status badge, product, dates, sum insured, premium and next due date', async () => {
    const fetchMock = show(policyDetail());

    expect(await screen.findByTestId('policy-number')).toHaveTextContent('PF-TL-2026-000001');
    expect(screen.getByTestId('policy-status-badge')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('policy-product')).toHaveTextContent('TERM_LIFE');
    expect(screen.getByTestId('policy-effective-date')).toHaveTextContent('2026-11-01');
    expect(screen.getByTestId('policy-expiry-date')).toHaveTextContent('2027-10-31');
    expect(screen.getByTestId('policy-sum-insured')).toHaveTextContent('5000000.00');
    expect(screen.getByTestId('policy-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('policy-next-due')).toHaveTextContent('2027-11-01');
    const [call] = calls(fetchMock);
    expect(call.url).toBe(`/api/policies/${POLICY_NUMBER}`);
    expect(header(call, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-05.1: shows a fixed not-found message on a 404 and no policy details', async () => {
    installFetch(() => problem(404, { detail: 'Policy PF-TL-2026-000001 was not found for caller customer1' }));
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    const message = await screen.findByTestId('policy-not-found');
    expect(message).toHaveTextContent('We could not find that policy.');
    expect(message).not.toHaveTextContent('customer1');
    expect(screen.queryByTestId('policy-number')).not.toBeInTheDocument();
  });

  it('AC-06.1: the Versions tab shows v1 with the old address and v2 with the new address', async () => {
    show(endorsedDetail());

    await screen.findByTestId('policy-number');
    expect(screen.getByTestId('tabpanel-versions')).toBeInTheDocument();
    expect(screen.getByTestId('version-1-address')).toHaveTextContent('12 Old Street, Pune, 411001');
    expect(screen.getByTestId('version-2-address')).toHaveTextContent('99 New Road, Mumbai, 400001');
    expect(screen.getByTestId('version-1-sum-insured')).toHaveTextContent('5000000.00');
    expect(screen.getByTestId('version-1-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('version-4-sum-insured')).toHaveTextContent('7000000.00');
    expect(screen.getByTestId('version-4-premium')).toHaveTextContent('9490.00');
    expect(screen.getByTestId('version-1-nominees')).toHaveTextContent('None');
    expect(screen.getByTestId('version-3-nominees')).toHaveTextContent('Ravi Nominee');
    expect(screen.getByTestId('version-3-nominees')).toHaveTextContent('SPOUSE');
    expect(screen.getByTestId('version-3-nominees')).toHaveTextContent('60');
    const rows = within(screen.getByTestId('tabpanel-versions')).getAllByTestId(/^version-row-\d+$/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([
      'version-row-1',
      'version-row-2',
      'version-row-3',
      'version-row-4',
    ]);
  });

  it('AC-06.6: the Endorsements tab lists sequence 1, 2, 3 in API order, linked to versions 2, 3, 4', async () => {
    const user = userEvent.setup();
    show(endorsedDetail());

    await user.click(await screen.findByTestId('tab-endorsements'));

    const rows = within(screen.getByTestId('tabpanel-endorsements')).getAllByTestId(/^endorsement-row-\d+$/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([
      'endorsement-row-1',
      'endorsement-row-2',
      'endorsement-row-3',
    ]);
    expect(screen.getByTestId('endorsement-1-versions')).toHaveTextContent('v1 → v2');
    expect(screen.getByTestId('endorsement-2-versions')).toHaveTextContent('v2 → v3');
    expect(screen.getByTestId('endorsement-3-versions')).toHaveTextContent('v3 → v4');
    expect(screen.getByTestId('endorsement-1-type')).toHaveTextContent('CHANGE_ADDRESS');
    expect(screen.getByTestId('endorsement-2-type')).toHaveTextContent('ADD_NOMINEE');
    expect(screen.getByTestId('endorsement-3-type')).toHaveTextContent('CHANGE_SUM_INSURED');
    expect(screen.getByTestId('endorsement-3-adjustment')).toHaveTextContent('2640.00');
    expect(screen.getByTestId('endorsement-1-adjustment')).toHaveTextContent('0.00');
  });

  it('AC-10.4: the Status tab lists the transitions in API order with a dash for the null from-status, and the badge equals the policy status', async () => {
    const user = userEvent.setup();
    show(endorsedDetail());

    await user.click(await screen.findByTestId('tab-status'));

    const rows = within(screen.getByTestId('tabpanel-status')).getAllByTestId(/^transition-row-\d+$/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([
      'transition-row-0',
      'transition-row-1',
      'transition-row-2',
      'transition-row-3',
    ]);
    expect(screen.getByTestId('transition-0-from')).toHaveTextContent('—');
    expect(screen.getByTestId('transition-0-to')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('transition-0-reason')).toHaveTextContent('ISSUANCE');
    expect(screen.getByTestId('transition-1-from')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('transition-1-to')).toHaveTextContent('ENDORSED');
    expect(screen.getByTestId('transition-3-reason')).toHaveTextContent('CHANGE_SUM_INSURED');
    expect(screen.getByTestId('policy-status-badge')).toHaveTextContent('ENDORSED');
    expect(screen.getByTestId('transition-3-to')).toHaveTextContent('ENDORSED');
  });

  it('AC-10.4: the Refunds tab shows an empty state', async () => {
    const user = userEvent.setup();
    show(policyDetail());

    await user.click(await screen.findByTestId('tab-refunds'));

    expect(screen.getByTestId('refunds-empty')).toBeInTheDocument();
  });

  it.each<PolicyStatus>(['ACTIVE', 'ENDORSED', 'RENEWED'])(
    'AC-10.2: the Endorse link is shown for an in-force %s policy',
    async (status) => {
      show(policyDetail({ policy: policyHeader({ status }) }));

      const link = await screen.findByTestId('policy-endorse-link');
      expect(link).toHaveAttribute('href', `#/policies/${POLICY_NUMBER}/endorse`);
    },
  );

  it.each<PolicyStatus>(['CANCELLED', 'LAPSED'])('AC-10.2: no Endorse link on a %s policy', async (status) => {
    show(policyDetail({ policy: policyHeader({ status }) }));

    expect(await screen.findByTestId('policy-status-badge')).toHaveTextContent(status);
    expect(screen.queryByTestId('policy-endorse-link')).not.toBeInTheDocument();
  });
});
