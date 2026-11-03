import { screen, waitFor, within } from '@testing-library/react';
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
  refund,
  refundQuote,
  renderWithSession,
  transition,
} from '../test/helpers';
import type { Call } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

const DETAIL_URL = `/api/policies/${POLICY_NUMBER}`;
const PREVIEW_PATH = `/api/policies/${POLICY_NUMBER}/cancel/preview`;
const CANCEL_URL = `/api/policies/${POLICY_NUMBER}/cancel`;

/** The detail after the AC-08.1 cancellation: CANCELLED with one refund record. */
function cancelled(): PolicyDetail {
  return policyDetail({
    policy: policyHeader({ status: 'CANCELLED' }),
    transitions: [
      transition(),
      transition({ fromStatus: 'ACTIVE', toStatus: 'CANCELLED', reason: 'NO_LONGER_NEEDED', at: '2027-02-09T10:00:00Z' }),
    ],
    refunds: [refund()],
  });
}

interface Responses {
  before?: PolicyDetail;
  after?: PolicyDetail;
  preview?: () => Response;
  cancel?: () => Response;
}

function install(responses: Responses = {}) {
  let done = false;
  return installFetch((url, init) => {
    if (url === DETAIL_URL) {
      return jsonResponse(200, done ? (responses.after ?? cancelled()) : (responses.before ?? policyDetail()));
    }
    if (url.startsWith(PREVIEW_PATH) && (init?.method ?? 'GET') === 'GET') {
      return responses.preview ? responses.preview() : jsonResponse(200, refundQuote());
    }
    if (url === CANCEL_URL && init?.method === 'POST') {
      const response = responses.cancel
        ? responses.cancel()
        : jsonResponse(201, { refund: refund(), policy: policyHeader({ status: 'CANCELLED' }) });
      if (response.ok) done = true;
      return response;
    }
    return problem(404);
  });
}

function previews(fetchMock: ReturnType<typeof install>): Call[] {
  return calls(fetchMock).filter((call) => call.url.startsWith(PREVIEW_PATH));
}

function cancels(fetchMock: ReturnType<typeof install>): Call[] {
  return calls(fetchMock).filter((call) => call.url === CANCEL_URL);
}

async function open(responses: Responses = {}) {
  const user = userEvent.setup();
  const fetchMock = install(responses);
  renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);
  await screen.findByTestId('cancel-panel');
  return { user, fetchMock };
}

/** Choose a reason, preview, and wait for the preview to show. */
async function previewWith(user: ReturnType<typeof userEvent.setup>, reason = 'NO_LONGER_NEEDED') {
  await user.selectOptions(screen.getByTestId('cancel-reason'), reason);
  await user.click(screen.getByTestId('cancel-preview'));
  await screen.findByTestId('refund-preview-amount');
}

describe('PolicyDetailPage cancel panel', () => {
  it('AC-08.1: the Cancel panel offers an optional date input and a required reason select with the four reasons', async () => {
    await open();

    expect(screen.getByTestId('cancel-date')).toHaveValue('');
    const options = within(screen.getByTestId('cancel-reason')).getAllByRole('option');
    expect(options.map((option) => option.getAttribute('value'))).toEqual([
      '',
      'NO_LONGER_NEEDED',
      'REPLACED_BY_OTHER_COVER',
      'AFFORDABILITY',
      'OTHER',
    ]);
    expect(screen.getByTestId('cancel-confirm')).toBeDisabled();
    expect(screen.queryByTestId('refund-preview-amount')).not.toBeInTheDocument();
  });

  it('AC-08.1: Preview shows amount 4473.29, fee, days, cooling-off flag and formula verbatim, from the C3 call with the date', async () => {
    const { user, fetchMock } = await open();

    await user.type(screen.getByTestId('cancel-date'), '2027-02-09');
    await previewWith(user);

    expect(screen.getByTestId('refund-preview-amount')).toHaveTextContent('4473.29');
    expect(screen.getByTestId('refund-preview-fee')).toHaveTextContent('500.00');
    expect(screen.getByTestId('refund-preview-days')).toHaveTextContent('265 / 365');
    expect(screen.getByTestId('refund-preview-cooling-off')).toHaveTextContent('false');
    expect(screen.getByTestId('refund-preview-formula')).toHaveTextContent('max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29');
    const [call, ...others] = previews(fetchMock);
    expect(others).toEqual([]);
    expect(call.url).toBe(`${PREVIEW_PATH}?date=2027-02-09`);
    expect(call.method).toBe('GET');
    expect(header(call, 'Authorization')).toBe(basic(CUSTOMER));
    expect(cancels(fetchMock)).toEqual([]);
  });

  it('AC-08.1: with no date, Preview sends no date parameter', async () => {
    const { user, fetchMock } = await open();

    await previewWith(user, 'AFFORDABILITY');

    expect(previews(fetchMock)[0].url).toBe(PREVIEW_PATH);
  });

  it('AC-08.1: Confirm is enabled only after a preview, and editing the date or the reason clears the preview', async () => {
    const { user } = await open();
    const confirm = screen.getByTestId('cancel-confirm');
    expect(confirm).toBeDisabled();

    await user.type(screen.getByTestId('cancel-date'), '2027-02-09');
    await user.selectOptions(screen.getByTestId('cancel-reason'), 'NO_LONGER_NEEDED');
    expect(confirm).toBeDisabled();
    await user.click(screen.getByTestId('cancel-preview'));
    await screen.findByTestId('refund-preview-amount');
    expect(confirm).toBeEnabled();

    await user.clear(screen.getByTestId('cancel-date'));
    expect(screen.queryByTestId('refund-preview-amount')).not.toBeInTheDocument();
    expect(confirm).toBeDisabled();

    await user.type(screen.getByTestId('cancel-date'), '2027-02-09');
    await user.click(screen.getByTestId('cancel-preview'));
    await screen.findByTestId('refund-preview-amount');
    expect(confirm).toBeEnabled();

    await user.selectOptions(screen.getByTestId('cancel-reason'), 'OTHER');
    expect(screen.queryByTestId('refund-preview-amount')).not.toBeInTheDocument();
    expect(confirm).toBeDisabled();
  });

  it('AC-08.1: Preview without a reason shows a required message at the reason and calls nothing', async () => {
    const { user, fetchMock } = await open();

    await user.click(screen.getByTestId('cancel-preview'));

    expect(await screen.findByTestId('cancel-reason-error')).toHaveTextContent('This field is required.');
    expect(previews(fetchMock)).toEqual([]);
  });

  it('AC-08.1: Confirm posts exactly {reason, cancellationDate}, then reloads the detail, which shows CANCELLED and no panel', async () => {
    const { user, fetchMock } = await open();

    await user.type(screen.getByTestId('cancel-date'), '2027-02-09');
    await previewWith(user);
    await user.click(screen.getByTestId('cancel-confirm'));

    await waitFor(() => expect(screen.getByTestId('policy-status-badge')).toHaveTextContent('CANCELLED'));
    const [post, ...others] = cancels(fetchMock);
    expect(others).toEqual([]);
    expect(postedJson(post)).toEqual({ reason: 'NO_LONGER_NEEDED', cancellationDate: '2027-02-09' });
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(calls(fetchMock).filter((call) => call.url === DETAIL_URL)).toHaveLength(2);
    expect(screen.queryByTestId('cancel-panel')).not.toBeInTheDocument();
  });

  it('AC-08.1: with no date, Confirm posts exactly {reason}', async () => {
    const { user, fetchMock } = await open();

    await previewWith(user, 'REPLACED_BY_OTHER_COVER');
    await user.click(screen.getByTestId('cancel-confirm'));

    await waitFor(() => expect(cancels(fetchMock)).toHaveLength(1));
    expect(postedJson(cancels(fetchMock)[0])).toEqual({ reason: 'REPLACED_BY_OTHER_COVER' });
  });

  it('AC-08.1: a 400 on the preview date shows the server message at the date input and keeps Confirm disabled', async () => {
    const { user } = await open({
      preview: () => problem(400, { errors: [{ field: 'date', message: 'Preview date message.' }] }),
    });

    await user.type(screen.getByTestId('cancel-date'), '2027-02-09');
    await user.selectOptions(screen.getByTestId('cancel-reason'), 'OTHER');
    await user.click(screen.getByTestId('cancel-preview'));

    expect(await screen.findByTestId('cancel-date-error')).toHaveTextContent('Preview date message.');
    expect(screen.getByTestId('cancel-confirm')).toBeDisabled();
  });

  it('AC-08.1: a 400 on cancellationDate at confirm shows the server message at the date input and keeps the panel', async () => {
    const { user } = await open({
      cancel: () =>
        problem(400, {
          code: 'CANCELLATION_DATE_OUT_OF_TERM',
          errors: [{ field: 'cancellationDate', message: 'Server date message.' }],
        }),
    });

    await user.type(screen.getByTestId('cancel-date'), '2030-01-01');
    await previewWith(user, 'OTHER');
    await user.click(screen.getByTestId('cancel-confirm'));

    expect(await screen.findByTestId('cancel-date-error')).toHaveTextContent('Server date message.');
    expect(screen.getByTestId('cancel-panel')).toBeInTheDocument();
  });

  it('AC-08.1: a CANCELLATION_DATE_OUT_OF_TERM with no field entry shows a fixed message at the date input', async () => {
    const { user } = await open({ cancel: () => problem(400, { code: 'CANCELLATION_DATE_OUT_OF_TERM' }) });

    await user.type(screen.getByTestId('cancel-date'), '2030-01-01');
    await previewWith(user, 'OTHER');
    await user.click(screen.getByTestId('cancel-confirm'));

    expect(await screen.findByTestId('cancel-date-error')).toHaveTextContent(
      'The cancellation date must fall within the current paid term.',
    );
  });

  it('AC-08.2: a cooling-off preview shows 6850.00, fee 0.00 and the cooling-off flag', async () => {
    const { user } = await open({
      preview: () =>
        jsonResponse(
          200,
          refundQuote({
            cancellationDate: '2026-11-11',
            unexpiredDays: 355,
            coolingOff: true,
            fee: '0.00',
            amount: '6850.00',
            formula: 'cooling-off: full premium 6850.00 = 6850.00',
          }),
        ),
    });

    await user.type(screen.getByTestId('cancel-date'), '2026-11-11');
    await previewWith(user);

    expect(screen.getByTestId('refund-preview-amount')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('refund-preview-fee')).toHaveTextContent('0.00');
    expect(screen.getByTestId('refund-preview-cooling-off')).toHaveTextContent('true');
    expect(screen.getByTestId('refund-preview-formula')).toHaveTextContent('cooling-off: full premium 6850.00 = 6850.00');
  });

  it('AC-08.3: a last-day preview whose fee exceeds the pro-rata amount shows 0.00', async () => {
    const { user } = await open({
      preview: () =>
        jsonResponse(
          200,
          refundQuote({
            cancellationDate: '2027-10-31',
            unexpiredDays: 1,
            amount: '0.00',
            formula: 'max(0, 6850.00 * 1 / 365 - 500.00) = 0.00',
          }),
        ),
    });

    await user.type(screen.getByTestId('cancel-date'), '2027-10-31');
    await previewWith(user, 'OTHER');

    expect(screen.getByTestId('refund-preview-amount')).toHaveTextContent('0.00');
    expect(screen.getByTestId('refund-preview-amount')).not.toHaveTextContent('-');
    expect(screen.getByTestId('refund-preview-days')).toHaveTextContent('1 / 365');
  });

  it('AC-08.4: after a cancellation the Refunds tab shows one row with every refund field verbatim, and no Cancel panel', async () => {
    const user = userEvent.setup();
    installFetch(() => jsonResponse(200, cancelled()));
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    expect(await screen.findByTestId('policy-status-badge')).toHaveTextContent('CANCELLED');
    expect(screen.queryByTestId('cancel-panel')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('tab-refunds'));

    const rows = within(screen.getByTestId('tabpanel-refunds')).getAllByTestId(/^refund-row-\d+$/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual(['refund-row-1']);
    expect(screen.getByTestId('refund-1-amount')).toHaveTextContent('4473.29');
    expect(screen.getByTestId('refund-1-date')).toHaveTextContent('2027-02-09');
    expect(screen.getByTestId('refund-1-fee')).toHaveTextContent('500.00');
    expect(screen.getByTestId('refund-1-days')).toHaveTextContent('265 / 365');
    expect(screen.getByTestId('refund-1-cooling-off')).toHaveTextContent('false');
    expect(screen.getByTestId('refund-1-formula')).toHaveTextContent('max(0, 6850.00 * 265 / 365 - 500.00) = 4473.29');
    expect(screen.queryByTestId('refunds-empty')).not.toBeInTheDocument();
  });

  it('AC-08.4: the Refunds tab keeps its empty state when there are no refunds', async () => {
    const user = userEvent.setup();
    installFetch(() => jsonResponse(200, policyDetail()));
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    await user.click(await screen.findByTestId('tab-refunds'));

    expect(screen.getByTestId('refunds-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('refund-row-1')).not.toBeInTheDocument();
  });

  it('AC-08.4: a 409 on confirm (a second cancel) shows the fixed message and requests no second cancel', async () => {
    const { user, fetchMock } = await open({
      cancel: () => problem(409, { type: '/problems/invalid-policy-state', detail: 'Server text for customer1' }),
    });

    await previewWith(user, 'OTHER');
    await user.click(screen.getByTestId('cancel-confirm'));

    const error = await screen.findByTestId('cancel-error');
    expect(error).toHaveTextContent('This policy is not in force, so it cannot be cancelled.');
    expect(error).not.toHaveTextContent('Server text');
    expect(cancels(fetchMock)).toHaveLength(1);
  });

  it.each([
    [
      '409 POLICY_UPDATE_CONFLICT',
      () => problem(409, { code: 'POLICY_UPDATE_CONFLICT' }),
      'The policy was changed by another request. Reload the policy and try again.',
    ],
    ['409 with no code', () => problem(409, { detail: 'Server text' }), 'The policy could not be cancelled in its current state.'],
    ['403', () => problem(403, { detail: 'Server text' }), 'Your account is not allowed to do that.'],
  ])('AC-08.4: %s on confirm shows a fixed message', async (_name, response, message) => {
    const { user } = await open({ cancel: response });

    await previewWith(user, 'OTHER');
    await user.click(screen.getByTestId('cancel-confirm'));

    const error = await screen.findByTestId('cancel-error');
    expect(error).toHaveTextContent(message);
    expect(error).not.toHaveTextContent('Server text');
  });

  it.each<PolicyStatus>(['ACTIVE', 'ENDORSED', 'RENEWED'])(
    'AC-08.1: the Cancel panel is shown for an in-force %s policy',
    async (status) => {
      installFetch(() => jsonResponse(200, policyDetail({ policy: policyHeader({ status }) })));
      renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

      expect(await screen.findByTestId('cancel-panel')).toBeInTheDocument();
    },
  );

  it('AC-10.3: a LAPSED policy shows neither a Renew nor a Cancel panel, even with an offer on record', async () => {
    installFetch(() =>
      jsonResponse(200, policyDetail({ policy: policyHeader({ status: 'LAPSED' }), pendingRenewal: pendingRenewal() })),
    );
    renderWithSession(<PolicyDetailPage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    expect(await screen.findByTestId('policy-status-badge')).toHaveTextContent('LAPSED');
    expect(screen.queryByTestId('renew-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('cancel-panel')).not.toBeInTheDocument();
  });

  it('AC-10.3: a cancel refused with the invalid-policy-state problem shows the 409 message and keeps the panel', async () => {
    const { user } = await open({
      cancel: () => problem(409, { type: '/problems/invalid-policy-state', code: 'INVALID_POLICY_STATE' }),
    });

    await previewWith(user);
    await user.click(screen.getByTestId('cancel-confirm'));

    expect(await screen.findByTestId('cancel-error')).toHaveTextContent('This policy is not in force, so it cannot be cancelled.');
    expect(screen.getByTestId('cancel-panel')).toBeInTheDocument();
  });
});
