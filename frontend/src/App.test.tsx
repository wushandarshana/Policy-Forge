import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import {
  application,
  basic,
  calls,
  CUSTOMER,
  header,
  installFetch,
  jsonResponse,
  endorsement,
  policyDetail,
  policyHeader,
  policySummary,
  POLICY_NUMBER,
  postedJson,
  queueItem,
  QUOTE_ID,
  UNDERWRITER,
} from './test/helpers';
import type { Handler } from './test/helpers';
import type { Credentials } from './api/types';

const PRODUCTS = [
  { productCode: 'TERM_LIFE', productName: 'Term Life', activeVersion: 1 },
  { productCode: 'MOTOR', productName: 'Motor', activeVersion: 1 },
  { productCode: 'HOUSEHOLD', productName: 'Household', activeVersion: 1 },
];

beforeEach(() => {
  window.location.hash = '';
});

afterEach(() => {
  vi.unstubAllGlobals();
  window.location.hash = '';
});

async function signInAs(user: ReturnType<typeof userEvent.setup>, credentials: Credentials) {
  await user.type(await screen.findByTestId('signin-username'), credentials.username);
  await user.type(screen.getByTestId('signin-password'), credentials.password);
  await user.click(screen.getByTestId('signin-submit'));
}

function renderAt(hash: string, handler: Handler) {
  window.location.hash = hash;
  const fetchMock = installFetch(handler);
  const user = userEvent.setup();
  render(<App />);
  return { fetchMock, user };
}

describe('App shell routing', () => {
  it('AC-03.1: #/apply?quoteId=Q-... renders the Apply page for that quote behind the sign-in gate', async () => {
    const created = application();
    const { fetchMock, user } = renderAt(`#/apply?quoteId=${QUOTE_ID}`, (url) =>
      url.endsWith('/api/products') ? jsonResponse(200, PRODUCTS) : jsonResponse(201, created),
    );

    expect(await screen.findByTestId('signin-form')).toBeInTheDocument();
    expect(screen.queryByTestId('apply-form')).not.toBeInTheDocument();

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('apply-form')).toBeInTheDocument();
    expect(screen.getByTestId('apply-quote-id')).toHaveTextContent(QUOTE_ID);

    await user.type(screen.getByTestId('input-fullName'), 'Asha Test');
    await user.type(screen.getByTestId('input-dateOfBirth'), '1991-04-02');
    await user.type(screen.getByTestId('input-aadhaar'), 'XXXX-XXXX-1234');
    await user.type(screen.getByTestId('input-pan'), 'ABCDE1234F');
    await user.type(screen.getByTestId('input-line1'), '1 Demo Street');
    await user.type(screen.getByTestId('input-city'), 'Pune');
    await user.type(screen.getByTestId('input-postcode'), '411001');
    await user.click(screen.getByTestId('submit-application'));

    await waitFor(() => expect(window.location.hash).toBe(`#/applications/${created.applicationId}`));
    const [post] = calls(fetchMock);
    expect(post.url).toBe('/api/applications');
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect((postedJson(post) as { quoteId: string }).quoteId).toBe(QUOTE_ID);
  });

  it('AC-03.8: #/applications/<id> renders the Application Status page for that ID after sign-in', async () => {
    const mine = application({ applicationId: 'A-aaaaaaaa-0000-4000-8000-000000000001' });
    const { user } = renderAt(`#/applications/${mine.applicationId}`, (url) =>
      url.endsWith('/api/products') ? jsonResponse(200, PRODUCTS) : jsonResponse(200, [mine]),
    );

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('application-id')).toHaveTextContent(mine.applicationId);
    expect(screen.getByTestId('status-badge')).toHaveTextContent('APPROVED');
  });

  it('AC-09.1: #/workbench renders the Workbench behind the sign-in gate and the nav link reaches it', async () => {
    const row = queueItem();
    const { user } = renderAt('', (url) => {
      if (url.endsWith('/api/products')) return jsonResponse(200, PRODUCTS);
      if (url.includes('/queue?includeDeclined=true')) return jsonResponse(403, { title: 'Forbidden', status: 403 });
      return jsonResponse(200, [row]);
    });

    await user.click(await screen.findByTestId('nav-workbench'));
    expect(window.location.hash).toBe('#/workbench');
    await signInAs(user, UNDERWRITER);

    expect(await screen.findByTestId(`queue-row-${row.applicationId}`)).toBeInTheDocument();
  });

  it('AC-01.1: the default route is the public Quote page and needs no sign-in', async () => {
    renderAt('', () => jsonResponse(200, PRODUCTS));

    expect(await screen.findByTestId('quote-form')).toBeInTheDocument();
    expect(screen.queryByTestId('signin-form')).not.toBeInTheDocument();
  });
});

/** Handler for the policy pages: C2 list, C3 detail, plus whatever a test adds on top. */
function policyHandler(extra: Handler = () => jsonResponse(404, {})): Handler {
  return (url, init) => {
    if (url.endsWith('/api/products')) return jsonResponse(200, PRODUCTS);
    if (url.endsWith('/api/policies/mine')) return jsonResponse(200, [policySummary()]);
    if (url.endsWith(`/api/policies/${POLICY_NUMBER}`)) return jsonResponse(200, policyDetail());
    return extra(url, init);
  };
}

describe('App shell routing for the policy pages (C9)', () => {
  it('AC-05.1: #/policies renders My Policies behind the sign-in gate', async () => {
    const { user } = renderAt('#/policies', policyHandler());

    expect(await screen.findByTestId('signin-form')).toBeInTheDocument();
    expect(screen.queryByTestId('policies-list')).not.toBeInTheDocument();

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('policies-list')).toBeInTheDocument();
    expect(screen.getByTestId(`policy-card-${POLICY_NUMBER}`)).toBeInTheDocument();
  });

  it('AC-05.1: #/policies/<number> renders that policy detail', async () => {
    const { fetchMock, user } = renderAt(`#/policies/${POLICY_NUMBER}`, policyHandler());

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('policy-number')).toHaveTextContent(POLICY_NUMBER);
    expect(screen.getByTestId('policy-status-badge')).toHaveTextContent('ACTIVE');
    const detailCall = calls(fetchMock).find((call) => call.url.endsWith(`/api/policies/${POLICY_NUMBER}`));
    expect(detailCall).toBeDefined();
    expect(header(detailCall!, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-05.1: the My Policies nav link reaches the list and a card link opens the detail page', async () => {
    const { user } = renderAt('', policyHandler());

    await user.click(await screen.findByTestId('nav-policies'));
    expect(window.location.hash).toBe('#/policies');
    await signInAs(user, CUSTOMER);

    await user.click(await screen.findByTestId(`policy-card-${POLICY_NUMBER}-link`));
    expect(window.location.hash).toBe(`#/policies/${POLICY_NUMBER}`);
    expect(await screen.findByTestId('policy-number')).toHaveTextContent(POLICY_NUMBER);
  });

  it('AC-05.1: issuing from the Application Status page navigates to the new policy detail page', async () => {
    const approved = application();
    const { fetchMock, user } = renderAt(
      `#/applications/${approved.applicationId}`,
      policyHandler((url, init) => {
        if (url.endsWith('/api/applications/mine')) return jsonResponse(200, [approved]);
        if (url.endsWith(`/api/applications/${approved.applicationId}/issue`) && init?.method === 'POST') {
          return jsonResponse(201, policyHeader());
        }
        return jsonResponse(200, [approved]);
      }),
    );

    await signInAs(user, CUSTOMER);
    await user.click(await screen.findByTestId('issue-submit'));

    await waitFor(() => expect(window.location.hash).toBe(`#/policies/${POLICY_NUMBER}`));
    expect(await screen.findByTestId('policy-number')).toHaveTextContent(POLICY_NUMBER);
    const post = calls(fetchMock).find((call) => call.method === 'POST');
    expect(post?.url).toBe(`/api/applications/${approved.applicationId}/issue`);
    expect(postedJson(post!)).toEqual({});
  });

  it('AC-06.1: #/policies/<number>/endorse renders the Endorse page for that policy', async () => {
    const { user } = renderAt(`#/policies/${POLICY_NUMBER}/endorse`, policyHandler());

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('endorse-form')).toBeInTheDocument();
    expect(screen.getByTestId('endorse-type')).toBeInTheDocument();
    expect(screen.queryByTestId('policy-number')).not.toBeInTheDocument();
  });

  it('AC-06.1: the detail Endorse link opens the Endorse route and a confirmed endorsement returns to the detail page', async () => {
    const after = endorsement();
    const { fetchMock, user } = renderAt(
      `#/policies/${POLICY_NUMBER}`,
      policyHandler((url, init) => {
        if (url.endsWith(`/api/policies/${POLICY_NUMBER}/endorsements`) && init?.method === 'POST') {
          return jsonResponse(201, { endorsement: after, policy: policyHeader({ status: 'ENDORSED', endorsementCount: 1 }) });
        }
        return jsonResponse(404, {});
      }),
    );

    await signInAs(user, CUSTOMER);
    await user.click(await screen.findByTestId('policy-endorse-link'));
    expect(window.location.hash).toBe(`#/policies/${POLICY_NUMBER}/endorse`);

    await user.type(await screen.findByTestId('input-line1'), '99 New Road');
    await user.type(screen.getByTestId('input-city'), 'Mumbai');
    await user.type(screen.getByTestId('input-postcode'), '400001');
    await user.click(screen.getByTestId('endorse-confirm'));

    await waitFor(() => expect(window.location.hash).toBe(`#/policies/${POLICY_NUMBER}`));
    expect(await screen.findByTestId('policy-number')).toHaveTextContent(POLICY_NUMBER);
    const post = calls(fetchMock).find((call) => call.method === 'POST');
    expect(postedJson(post!)).toMatchObject({ type: 'CHANGE_ADDRESS', address: { city: 'Mumbai' } });
  });

  it('AC-06.1: an unknown policy route segment is still a gated detail page, not the Quote page', async () => {
    const { user } = renderAt('#/policies/PF-XX-0000-000000', policyHandler());

    await signInAs(user, CUSTOMER);

    expect(await screen.findByTestId('policy-not-found')).toBeInTheDocument();
    expect(screen.queryByTestId('quote-form')).not.toBeInTheDocument();
  });
});
