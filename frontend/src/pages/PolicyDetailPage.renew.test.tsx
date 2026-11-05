import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PolicyDetailPage } from './PolicyDetailPage';
import type { PolicyDetail, PolicyStatus } from '../api';
import {
  basic,
  calls,
  CUSTOMER,
  header,
  installFetch,
  jsonResponse,
  pendingRenewal,
  POLICY_NUMBER,
  policyDetail,
  policyHeader,
  postedJson,
  problem,
  renderWithSession,
} from '../test/helpers';
import type { Call } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

const DETAIL_URL = `/api/policies/${POLICY_NUMBER}`;
const PAY_URL = `/api/policies/${POLICY_NUMBER}/renewal/pay`;

/** The AC-07.1 offer on an ACTIVE policy: renewal term 2027-11-01..2028-10-31, premium 6850.00, rule version 1. */
function offered(overrides: Partial<PolicyDetail> = {}): PolicyDetail {
  return policyDetail({ pendingRenewal: pendingRenewal(), ...overrides });
}

/** The same policy after the owner paid the offer: RENEWED, no pending offer, term 2 paid. */
function renewed(): PolicyDetail {
  return policyDetail({
    policy: policyHeader({ status: 'RENEWED', effectiveDate: '2027-11-01', expiryDate: '2028-10-31', currentVersionNo: 2 }),
    terms: [
      { termNo: 1, startDate: '2026-11-01', endDate: '2027-10-31', premium: '6850.00', paid: true, ruleVersion: 1 },
      { termNo: 2, startDate: '2027-11-01', endDate: '2028-10-31', premium: '6850.00', paid: true, ruleVersion: 1 },
    ],
    pendingRenewal: null,
  });
}

function payCalls(fetchMock: ReturnType<typeof installFetch>): Call[] {
  return calls(fetchMock).filter((call) => call.url === PAY_URL);
}

/** Serves `first` until a pay succeeded, then `after`. `pay` decides the pay response. */
function install(first: PolicyDetail, pay: () => Response, after: PolicyDetail = renewed()) {
  let paid = false;
  return installFetch((url, init) => {
    if (url === DETAIL_URL) return jsonResponse(200, paid ? after : first);
    if (url === PAY_URL && init?.method === 'POST') {
      const response = pay();
      if (response.ok) paid = true;
      return response;
    }
    return problem(404);
  });
}

describe('PolicyDetailPage renewal panel', () => {
  it('AC-07.1: the Renew panel shows the offered term dates, premium, rule version and payable-until date verbatim', async () => {
    installFetch(() => jsonResponse(200, offered()));
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    expect(await screen.findByTestId('renew-panel')).toBeInTheDocument();
    expect(screen.getByTestId('renew-start')).toHaveTextContent('2027-11-01');
    expect(screen.getByTestId('renew-end')).toHaveTextContent('2028-10-31');
    expect(screen.getByTestId('renew-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('renew-rule-version')).toHaveTextContent('1');
    expect(screen.getByTestId('renew-payable-until')).toHaveTextContent('2027-11-30');
    expect(screen.getByTestId('renew-pay')).toBeEnabled();
  });

  it('AC-07.1: the Renew panel shows a rule version 2 offer at its own premium while the header keeps the current premium', async () => {
    installFetch(() =>
      jsonResponse(200, offered({ pendingRenewal: pendingRenewal({ premium: '7400.00', ruleVersion: 2 }) })),
    );
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    expect(await screen.findByTestId('renew-premium')).toHaveTextContent('7400.00');
    expect(screen.getByTestId('renew-rule-version')).toHaveTextContent('2');
    expect(screen.getByTestId('policy-premium')).toHaveTextContent('6850.00');
  });

  it('AC-07.1: no Renew panel when there is no offer', async () => {
    installFetch(() => jsonResponse(200, policyDetail()));
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    expect(await screen.findByTestId('policy-number')).toBeInTheDocument();
    expect(screen.queryByTestId('renew-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('renew-pay')).not.toBeInTheDocument();
  });

  it.each<PolicyStatus>(['CANCELLED', 'LAPSED'])(
    'AC-07.1: no Renew panel on a %s policy even with an offer on record',
    async (status) => {
      installFetch(() => jsonResponse(200, offered({ policy: policyHeader({ status }) })));
      renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

      expect(await screen.findByTestId('policy-status-badge')).toHaveTextContent(status);
      expect(screen.queryByTestId('renew-panel')).not.toBeInTheDocument();
    },
  );

  it('AC-07.5: Pay posts exactly {} with the caller credentials, then reloads the detail, which shows RENEWED and no panel', async () => {
    const user = userEvent.setup();
    const fetchMock = install(offered(), () =>
      jsonResponse(200, { policy: policyHeader({ status: 'RENEWED' }), term: renewed().terms[1] }),
    );
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    await user.click(await screen.findByTestId('renew-pay'));

    await waitFor(() => expect(screen.getByTestId('policy-status-badge')).toHaveTextContent('RENEWED'));
    const [post, ...others] = payCalls(fetchMock);
    expect(others).toEqual([]);
    expect(post.method).toBe('POST');
    expect(postedJson(post)).toEqual({});
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(header(post, 'Content-Type')).toBe('application/json');
    expect(calls(fetchMock).filter((call) => call.url === DETAIL_URL)).toHaveLength(2);
    expect(screen.queryByTestId('renew-panel')).not.toBeInTheDocument();
    expect(screen.getByTestId('policy-expiry-date')).toHaveTextContent('2028-10-31');
  });

  it('AC-07.5: a 409 RENEWAL_PAYMENT_WINDOW_CLOSED shows the fixed window-closed message and keeps the panel', async () => {
    const user = userEvent.setup();
    const fetchMock = install(offered(), () =>
      problem(409, { code: 'RENEWAL_PAYMENT_WINDOW_CLOSED', detail: 'Server text for PF-TL-2026-000001 that must not be echoed' }),
    );
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    await user.click(await screen.findByTestId('renew-pay'));

    const error = await screen.findByTestId('renew-error');
    expect(error).toHaveTextContent('The payment window for this renewal has closed.');
    expect(error).not.toHaveTextContent('Server text');
    expect(screen.getByTestId('renew-panel')).toBeInTheDocument();
    expect(calls(fetchMock).filter((call) => call.url === DETAIL_URL)).toHaveLength(1);
  });

  it.each([
    ['409 NO_PENDING_RENEWAL', () => problem(409, { code: 'NO_PENDING_RENEWAL' }), 'There is no renewal offer to pay.'],
    [
      '409 invalid-policy-state (type only)',
      () => problem(409, { type: '/problems/invalid-policy-state', detail: 'Server text' }),
      'This policy is not in force, so the renewal cannot be paid.',
    ],
    [
      '409 POLICY_UPDATE_CONFLICT',
      () => problem(409, { code: 'POLICY_UPDATE_CONFLICT' }),
      'The policy was changed by another request. Reload the policy and try again.',
    ],
    ['403', () => problem(403, { detail: 'Server text' }), 'Your account is not allowed to do that.'],
  ])('AC-07.5: %s shows a fixed message', async (_name, response, message) => {
    const user = userEvent.setup();
    install(offered(), response);
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    await user.click(await screen.findByTestId('renew-pay'));

    const error = await screen.findByTestId('renew-error');
    expect(error).toHaveTextContent(message);
    expect(error).not.toHaveTextContent('Server text');
  });
});
