import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApplicationStatusPage } from './ApplicationStatusPage';
import type { ApplicationStatus } from '../api';
import {
  application,
  basic,
  calls,
  CUSTOMER,
  header,
  installFetch,
  jsonResponse,
  policyHeader,
  postedJson,
  problem,
  renderWithSession,
} from '../test/helpers';
import type { Call } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

const APPLICATION_ID = application().applicationId;
const ISSUE_URL = `/api/applications/${APPLICATION_ID}/issue`;

function install(issueResponse: () => Response, status: ApplicationStatus = 'APPROVED') {
  return installFetch((url, init) => {
    if (url === '/api/applications/mine') return jsonResponse(200, [application({ status })]);
    if (url === ISSUE_URL && init?.method === 'POST') return issueResponse();
    return problem(404);
  });
}

function issuePosts(fetchMock: ReturnType<typeof install>): Call[] {
  return calls(fetchMock).filter((call) => call.url === ISSUE_URL);
}

describe('ApplicationStatusPage issue panel', () => {
  it('AC-05.1: an APPROVED application posts exactly {} when the date is blank and hands the policy number to the callback', async () => {
    const fetchMock = install(() => jsonResponse(201, policyHeader()));
    const onIssued = vi.fn();
    const user = userEvent.setup();
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} onIssued={onIssued} />, CUSTOMER);

    await user.click(await screen.findByTestId('issue-submit'));

    await waitFor(() => expect(onIssued).toHaveBeenCalledWith('PF-TL-2026-000001'));
    const [post] = issuePosts(fetchMock);
    expect(postedJson(post)).toEqual({});
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(header(post, 'Content-Type')).toBe('application/json');
  });

  it('AC-05.1: a chosen effective date is posted as { effectiveDate }', async () => {
    const fetchMock = install(() => jsonResponse(201, policyHeader()));
    const onIssued = vi.fn();
    const user = userEvent.setup();
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} onIssued={onIssued} />, CUSTOMER);

    await user.type(await screen.findByTestId('issue-effective-date'), '2026-11-15');
    await user.click(screen.getByTestId('issue-submit'));

    await waitFor(() => expect(onIssued).toHaveBeenCalledTimes(1));
    expect(postedJson(issuePosts(fetchMock)[0])).toEqual({ effectiveDate: '2026-11-15' });
  });

  it('AC-05.1: a 400 effectiveDate error shows at the date field and does not navigate', async () => {
    install(() =>
      problem(400, {
        code: 'EFFECTIVE_DATE_OUT_OF_RANGE',
        errors: [{ field: 'effectiveDate', message: 'Effective date must be from today to 60 days ahead.' }],
      }),
    );
    const onIssued = vi.fn();
    const user = userEvent.setup();
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} onIssued={onIssued} />, CUSTOMER);

    await user.type(await screen.findByTestId('issue-effective-date'), '2027-06-01');
    await user.click(screen.getByTestId('issue-submit'));

    expect(await screen.findByTestId('issue-effective-date-error')).toHaveTextContent(
      'Effective date must be from today to 60 days ahead.',
    );
    expect(onIssued).not.toHaveBeenCalled();
  });

  it('AC-05.3: a 409 is shown as a fixed message', async () => {
    install(() => problem(409, { code: 'INVALID_APPLICATION_STATE', detail: 'Application A-secret is REFERRED' }));
    const onIssued = vi.fn();
    const user = userEvent.setup();
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} onIssued={onIssued} />, CUSTOMER);

    await user.click(await screen.findByTestId('issue-submit'));

    const banner = await screen.findByTestId('issue-error');
    expect(banner).toHaveTextContent('This application is no longer in a state that allows that action.');
    expect(banner).not.toHaveTextContent('A-secret');
    expect(onIssued).not.toHaveBeenCalled();
  });

  it('AC-05.3: the issue panel is offered for APPROVED applications only', async () => {
    install(() => problem(404), 'DECLINED');
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} />, CUSTOMER);

    expect(await screen.findByTestId('status-badge')).toHaveTextContent('DECLINED');
    expect(screen.queryByTestId('issue-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('issue-submit')).not.toBeInTheDocument();
  });

  it('AC-05.1: an ISSUED application shows the Issued state with a link to My Policies and no issue form', async () => {
    install(() => problem(404), 'ISSUED');
    renderWithSession(<ApplicationStatusPage applicationId={APPLICATION_ID} />, CUSTOMER);

    expect(await screen.findByTestId('application-issued')).toHaveTextContent('Issued');
    expect(screen.getByTestId('application-policies-link')).toHaveAttribute('href', '#/policies');
    expect(screen.queryByTestId('issue-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('issue-submit')).not.toBeInTheDocument();
  });
});
