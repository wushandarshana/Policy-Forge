import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { WorkbenchPage } from './WorkbenchPage';
import {
  ADMIN,
  basic,
  calls,
  header,
  installFetch,
  jsonResponse,
  postedJson,
  problem,
  queueItem,
  renderWithSession,
  UNDERWRITER,
} from '../test/helpers';
import type { Handler } from '../test/helpers';
import type { Credentials, QueueItem } from '../api/types';

const OLDER = queueItem({ applicationId: 'A-00000000-0000-4000-8000-000000000001', submittedAt: '2026-10-28T08:00:00Z' });
const NEWER = queueItem({
  applicationId: 'A-00000000-0000-4000-8000-000000000002',
  submittedAt: '2026-10-28T09:00:00Z',
  kyc: { fullName: 'Ravi Demo', aadhaar: '**********5678', pan: '******678G' },
});
const DECLINED = queueItem({
  applicationId: 'A-00000000-0000-4000-8000-000000000003',
  status: 'DECLINED',
  decision: 'DECLINE',
  reasonCodes: ['UW-TL-001', 'UW-TL-004'],
  reasons: [
    { code: 'UW-TL-001', description: 'Age above 60' },
    { code: 'UW-TL-004', description: 'BMI above 35' },
  ],
});

const DECISION_URL = (id: string) => `/api/underwriting/${id}/decision`;
const OVERRIDE_URL = (id: string) => `/api/underwriting/${id}/override`;

afterEach(() => {
  vi.unstubAllGlobals();
});

/** An underwriter: includeDeclined=true is refused with 403, the plain queue returns `plain`. */
function underwriterApi(plain: QueueItem[], extra?: Handler): Handler {
  return (url, init) => {
    if (url.endsWith('/queue?includeDeclined=true')) return problem(403, { detail: 'Forbidden' });
    if (url.endsWith('/queue')) return jsonResponse(200, plain);
    return extra ? extra(url, init) : problem(404);
  };
}

/** An admin: includeDeclined=true succeeds. */
function adminApi(all: QueueItem[], extra?: Handler): Handler {
  return (url, init) => {
    if (url.endsWith('/queue?includeDeclined=true')) return jsonResponse(200, all);
    return extra ? extra(url, init) : problem(404);
  };
}

function setup(handler: Handler, credentials: Credentials = UNDERWRITER) {
  const fetchMock = installFetch(handler);
  const user = userEvent.setup();
  renderWithSession(<WorkbenchPage />, credentials);
  return { fetchMock, user };
}

async function selectCase(user: ReturnType<typeof userEvent.setup>, applicationId: string) {
  await user.click(await screen.findByTestId(`select-${applicationId}`));
  return screen.findByTestId('case-detail');
}

describe('WorkbenchPage', () => {
  it('AC-09.1: lists queue rows in API order and shows the masked values exactly as received', async () => {
    const { fetchMock, user } = setup(underwriterApi([OLDER, NEWER]));

    const rows = await screen.findAllByTestId(/^queue-row-/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([`queue-row-${OLDER.applicationId}`, `queue-row-${NEWER.applicationId}`]);

    const detail = await selectCase(user, OLDER.applicationId);
    expect(within(detail).getByTestId('case-aadhaar')).toHaveTextContent(/^\*{10}1234$/);
    expect(within(detail).getByTestId('case-pan')).toHaveTextContent(/^\*{6}234F$/);
    expect(within(detail).getByTestId('case-fullName')).toHaveTextContent('Asha Test');
    expect(within(detail).getByTestId('case-risk-sumInsured')).toHaveTextContent('12000000.00');
    expect(within(detail).getByTestId('case-risk-smoker')).toHaveTextContent('true');
    expect(within(detail).getByTestId('reason-item-UW-TL-002')).toHaveTextContent('Smoker with sum insured above 10,000,000');
    expect(document.body.textContent).not.toMatch(/XXXX-XXXX-1234|ABCDE1234F/);

    const queueCalls = calls(fetchMock).filter((call) => call.url.includes('/api/underwriting/queue'));
    for (const call of queueCalls) expect(header(call, 'Authorization')).toBe(basic(UNDERWRITER));
  });

  it('AC-09.2: approve posts the exact decision body and shows the new status', async () => {
    const approved = { applicationId: OLDER.applicationId, previousStatus: 'REFERRED', status: 'APPROVED' };
    // After the decision the application leaves the queue.
    let queue: QueueItem[] = [OLDER, NEWER];
    const { fetchMock, user } = setup((url, init) => {
      if (url.endsWith('/queue?includeDeclined=true')) return problem(403, { detail: 'Forbidden' });
      if (url.endsWith('/queue')) return jsonResponse(200, queue);
      if (url === DECISION_URL(OLDER.applicationId) && init?.method === 'POST') {
        queue = [NEWER];
        return jsonResponse(200, approved);
      }
      return problem(404);
    });

    await selectCase(user, OLDER.applicationId);
    await user.selectOptions(screen.getByTestId('decision-select'), 'APPROVE');
    await user.selectOptions(screen.getByTestId('reason-select'), 'UW-MAN-001');
    await user.type(screen.getByTestId('comment-input'), 'Reviewed and acceptable');
    await user.click(screen.getByTestId('submit-decision'));

    expect(await screen.findByTestId('decision-result')).toHaveTextContent('APPROVED');
    const posts = calls(fetchMock).filter((call) => call.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0].url).toBe(DECISION_URL(OLDER.applicationId));
    expect(header(posts[0], 'Authorization')).toBe(basic(UNDERWRITER));
    expect(postedJson(posts[0])).toEqual({ decision: 'APPROVE', reasonCode: 'UW-MAN-001', comment: 'Reviewed and acceptable' });
    await waitFor(() => expect(screen.queryByTestId(`queue-row-${OLDER.applicationId}`)).not.toBeInTheDocument());
    expect(screen.getByTestId(`queue-row-${NEWER.applicationId}`)).toBeInTheDocument();
  });

  it('AC-09.3: the admin override posts the exact override body', async () => {
    const result = { applicationId: DECLINED.applicationId, previousStatus: 'DECLINED', status: 'APPROVED' };
    const fetchMock = installFetch(
      adminApi([OLDER, DECLINED], (url, init) =>
        url === OVERRIDE_URL(DECLINED.applicationId) && init?.method === 'POST' ? jsonResponse(200, result) : problem(404),
      ),
    );
    const user = userEvent.setup();
    renderWithSession(<WorkbenchPage />, ADMIN);

    await selectCase(user, DECLINED.applicationId);
    await user.selectOptions(screen.getByTestId('override-reason-select'), 'UW-OVR-001');
    await user.type(screen.getByTestId('override-comment-input'), 'Commercial exception signed off');
    await user.click(screen.getByTestId('override-action'));

    expect(await screen.findByTestId('decision-result')).toHaveTextContent('APPROVED');
    const posts = calls(fetchMock).filter((call) => call.method === 'POST');
    expect(posts).toHaveLength(1);
    expect(posts[0].url).toBe(OVERRIDE_URL(DECLINED.applicationId));
    expect(header(posts[0], 'Authorization')).toBe(basic(ADMIN));
    expect(postedJson(posts[0])).toEqual({ reasonCode: 'UW-OVR-001', comment: 'Commercial exception signed off' });
  });

  it('AC-09.4: shows a server 400 comment error at the comment field', async () => {
    setup(
      underwriterApi([OLDER], (url, init) =>
        url === DECISION_URL(OLDER.applicationId) && init?.method === 'POST'
          ? problem(400, { detail: 'Validation failed', errors: [{ field: 'comment', message: 'Comment must be 10 to 2000 characters' }] })
          : problem(404),
      ),
    );
    const user = userEvent.setup();

    await selectCase(user, OLDER.applicationId);
    await user.selectOptions(screen.getByTestId('decision-select'), 'APPROVE');
    await user.selectOptions(screen.getByTestId('reason-select'), 'UW-MAN-002');
    await user.type(screen.getByTestId('comment-input'), 'too short');
    await user.click(screen.getByTestId('submit-decision'));

    const error = await screen.findByTestId('error-comment');
    expect(error).toHaveTextContent('Comment must be 10 to 2000 characters');
    expect(within(screen.getByTestId('field-comment')).getByTestId('error-comment')).toBe(error);
  });

  it('AC-09.4: shows a server 400 reasonCode error at the reason code field and checks only required fields locally', async () => {
    const fetchMock = installFetch(
      underwriterApi([OLDER], (url, init) =>
        url === DECISION_URL(OLDER.applicationId) && init?.method === 'POST'
          ? problem(400, { errors: [{ field: 'reasonCode', message: 'Reason code does not match the decision' }] })
          : problem(404),
      ),
    );
    const user = userEvent.setup();
    renderWithSession(<WorkbenchPage />, UNDERWRITER);

    await selectCase(user, OLDER.applicationId);
    await user.click(screen.getByTestId('submit-decision'));
    expect(await screen.findByTestId('error-decision')).toBeInTheDocument();
    expect(screen.getByTestId('error-reasonCode')).toBeInTheDocument();
    expect(screen.getByTestId('error-comment')).toBeInTheDocument();
    expect(calls(fetchMock).filter((call) => call.method === 'POST')).toHaveLength(0);

    await user.selectOptions(screen.getByTestId('decision-select'), 'APPROVE');
    await user.selectOptions(screen.getByTestId('reason-select'), 'UW-MAN-001');
    await user.type(screen.getByTestId('comment-input'), 'short');
    await user.click(screen.getByTestId('submit-decision'));
    expect(await screen.findByTestId('error-reasonCode')).toHaveTextContent('Reason code does not match the decision');
  });

  it('AC-09.5: shows a fixed message after a 409', async () => {
    setup(
      underwriterApi([OLDER], (url, init) =>
        url === DECISION_URL(OLDER.applicationId) && init?.method === 'POST'
          ? problem(409, { code: 'INVALID_APPLICATION_STATE', detail: 'Application is not in a state that allows this' })
          : problem(404),
      ),
    );
    const user = userEvent.setup();

    await selectCase(user, OLDER.applicationId);
    await user.selectOptions(screen.getByTestId('decision-select'), 'DECLINE');
    await user.selectOptions(screen.getByTestId('reason-select'), 'UW-MAN-010');
    await user.type(screen.getByTestId('comment-input'), 'Outside risk appetite');
    await user.click(screen.getByTestId('submit-decision'));

    expect(await screen.findByTestId('decision-error')).toHaveTextContent('This application is no longer in a state that allows that action.');
  });

  it('AC-09.6: offers reason codes as U-10 pairs them and clears the code when the decision changes', async () => {
    const { user } = setup(underwriterApi([OLDER]));

    await selectCase(user, OLDER.applicationId);
    const reasonValues = () =>
      within(screen.getByTestId('reason-select'))
        .getAllByRole('option')
        .map((option) => (option as HTMLOptionElement).value)
        .filter((value) => value !== '');

    expect(reasonValues()).toEqual([]);

    await user.selectOptions(screen.getByTestId('decision-select'), 'APPROVE');
    expect(reasonValues()).toEqual(['UW-MAN-001', 'UW-MAN-002']);
    expect(screen.getByTestId('reason-option-UW-MAN-001')).toHaveTextContent('Acceptable after review');
    expect(screen.getByTestId('reason-option-UW-MAN-002')).toHaveTextContent('Additional evidence satisfactory');

    await user.selectOptions(screen.getByTestId('reason-select'), 'UW-MAN-002');
    expect(screen.getByTestId('reason-select')).toHaveValue('UW-MAN-002');

    await user.selectOptions(screen.getByTestId('decision-select'), 'DECLINE');
    expect(screen.getByTestId('reason-select')).toHaveValue('');
    expect(reasonValues()).toEqual(['UW-MAN-010']);
    expect(screen.getByTestId('reason-option-UW-MAN-010')).toHaveTextContent('Risk outside appetite');
    expect(within(screen.getByTestId('decision-form')).queryByText(/UW-OVR/)).not.toBeInTheDocument();
  });

  it('AC-09.6: the override form offers only UW-OVR-001 and UW-OVR-002', async () => {
    installFetch(adminApi([DECLINED]));
    const user = userEvent.setup();
    renderWithSession(<WorkbenchPage />, ADMIN);

    await selectCase(user, DECLINED.applicationId);
    const values = within(screen.getByTestId('override-reason-select'))
      .getAllByRole('option')
      .map((option) => (option as HTMLOptionElement).value)
      .filter((value) => value !== '');
    expect(values).toEqual(['UW-OVR-001', 'UW-OVR-002']);
    expect(screen.getByTestId('reason-option-UW-OVR-001')).toHaveTextContent('Commercial exception approved');
    expect(screen.getByTestId('reason-option-UW-OVR-002')).toHaveTextContent('Evidence rebuts decline reason');
    expect(screen.queryByTestId('decision-form')).not.toBeInTheDocument();
  });

  it('AC-09.7: after a 403 on includeDeclined=true shows the plain queue and never renders Override', async () => {
    const { fetchMock, user } = setup(underwriterApi([OLDER, NEWER]));

    await selectCase(user, OLDER.applicationId);
    expect(screen.queryByTestId('override-action')).not.toBeInTheDocument();
    expect(screen.queryByTestId(`queue-row-${DECLINED.applicationId}`)).not.toBeInTheDocument();
    expect(screen.getAllByTestId(/^queue-row-/)).toHaveLength(2);

    const urls = calls(fetchMock).map((call) => call.url);
    expect(urls[0]).toMatch(/\/api\/underwriting\/queue\?includeDeclined=true$/);
    expect(urls).toContain('/api/underwriting/queue');
  });

  it('AC-09.7: shows declined cases and the Override action when includeDeclined=true returns 200', async () => {
    installFetch(adminApi([OLDER, DECLINED]));
    const user = userEvent.setup();
    renderWithSession(<WorkbenchPage />, ADMIN);

    expect(await screen.findByTestId(`queue-row-${DECLINED.applicationId}`)).toBeInTheDocument();
    await selectCase(user, DECLINED.applicationId);
    expect(screen.getByTestId('override-action')).toBeInTheDocument();
    expect(screen.getByTestId(`reason-item-UW-TL-001`)).toBeInTheDocument();
  });
});
