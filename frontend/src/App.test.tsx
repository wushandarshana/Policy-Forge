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
