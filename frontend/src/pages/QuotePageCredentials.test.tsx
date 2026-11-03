import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createQuote, getQuote, listMyApplications, listMyPolicies, listProducts } from '../api';
import { RequireSession } from '../auth/RequireSession';
import { useSession } from '../auth/SessionContext';
import { MyPoliciesPage } from './MyPoliciesPage';
import { QuotePage } from './QuotePage';
import { basic, calls, CUSTOMER, header, installFetch, jsonResponse, problem, renderWithSession } from '../test/helpers';
import type { Call } from '../test/helpers';

// Synthetic fixtures only. Money is a decimal string, exactly as the API sends it.
const PRODUCTS = [
  { productCode: 'TERM_LIFE', productName: 'Term Life', ratingModel: 'TERM_LIFE', activeVersion: 1 },
  { productCode: 'MOTOR', productName: 'Motor', ratingModel: 'MOTOR', activeVersion: 1 },
];

const QUOTE = {
  quoteId: 'Q-123e4567-e89b-42d3-a456-426614174000',
  productCode: 'TERM_LIFE',
  ruleVersion: 1,
  sumInsured: '5000000.00',
  annualPremium: '6850.00',
  breakdown: [{ factor: 'policyFee', value: '250.00' }],
  validUntil: '2026-11-27',
};

afterEach(() => {
  vi.unstubAllGlobals();
});

function install(quoteResponse: () => Response) {
  return installFetch((url, init) => {
    if (url.endsWith('/api/products')) return jsonResponse(200, PRODUCTS);
    if (url.endsWith('/api/quotes') && init?.method === 'POST') return quoteResponse();
    return problem(404);
  });
}

async function submitQuote(user: ReturnType<typeof userEvent.setup>) {
  const select = await screen.findByTestId('product-select');
  await waitFor(() => expect(within(select).getAllByRole('option')).toHaveLength(2));
  await user.selectOptions(select, 'TERM_LIFE');
  await user.type(screen.getByTestId('input-age'), '35');
  await user.type(screen.getByTestId('input-sumInsured'), '5000000.00');
  await user.selectOptions(screen.getByTestId('input-termYears'), '20');
  await user.type(screen.getByTestId('input-bmi'), '24.5');
  await user.click(screen.getByTestId('submit-quote'));
}

function quotePosts(fetchMock: ReturnType<typeof install>): Call[] {
  return calls(fetchMock).filter((call) => call.url.endsWith('/api/quotes') && call.method === 'POST');
}

describe('QuotePage credentials', () => {
  it('AC-01.13: a signed-in quote request carries the Basic Authorization header', async () => {
    const fetchMock = install(() => jsonResponse(201, QUOTE));
    const user = userEvent.setup();
    renderWithSession(<QuotePage />, CUSTOMER);

    await submitQuote(user);

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent('6850.00');
    const [post] = quotePosts(fetchMock);
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-01.13: a signed-out quote request carries no Authorization header', async () => {
    const fetchMock = install(() => jsonResponse(201, QUOTE));
    const user = userEvent.setup();
    renderWithSession(<QuotePage />, null);

    await submitQuote(user);

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent('6850.00');
    const [post] = quotePosts(fetchMock);
    expect(header(post, 'Authorization')).toBeNull();
  });

  it('AC-01.13: product calls and GET /api/quotes/{id} stay credential-free even when signed in', async () => {
    const fetchMock = installFetch((url) => (url.includes('/api/quotes/') ? jsonResponse(200, QUOTE) : jsonResponse(200, PRODUCTS)));

    await listProducts();
    await getQuote(QUOTE.quoteId);

    const [products, quote] = calls(fetchMock);
    expect(products.url).toBe('/api/products');
    expect(header(products, 'Authorization')).toBeNull();
    expect(quote.url).toBe(`/api/quotes/${QUOTE.quoteId}`);
    expect(header(quote, 'Authorization')).toBeNull();
  });

  it('AC-01.13: createQuote attaches the credentials only when it is given them', async () => {
    const fetchMock = install(() => jsonResponse(201, QUOTE));
    const request = { productCode: 'TERM_LIFE', riskInputs: { age: 35 } };

    await createQuote(request, CUSTOMER);
    await createQuote(request);

    const [signedIn, signedOut] = quotePosts(fetchMock);
    expect(header(signedIn, 'Authorization')).toBe(basic(CUSTOMER));
    expect(header(signedOut, 'Authorization')).toBeNull();
    expect(JSON.parse(String(signedIn.init?.body))).toEqual(request);
  });

  it('AC-01.13: a 401 on quote creation shows a fixed sign-in-again message and is never retried anonymously', async () => {
    const fetchMock = install(() => problem(401, { detail: 'Bad credentials for customer1' }));
    const user = userEvent.setup();
    renderWithSession(<QuotePage />, CUSTOMER);

    await submitQuote(user);

    const message = await screen.findByTestId('request-error');
    expect(message).toHaveTextContent(/sign in again/i);
    expect(message).not.toHaveTextContent('customer1');
    expect(screen.queryByTestId('quote-result')).not.toBeInTheDocument();
    const posts = quotePosts(fetchMock);
    expect(posts).toHaveLength(1);
    expect(header(posts[0], 'Authorization')).toBe(basic(CUSTOMER));
  });
});

/** Shows who the app thinks is signed in, so a test can see the session being cleared. */
function SessionProbe() {
  const { credentials } = useSession();
  return <p data-testid="probe-user">{credentials ? credentials.username : 'signed-out'}</p>;
}

describe('QuotePage credentials: browser-held credentials are never sent (security review S3 MINOR-1)', () => {
  it("AC-01.13: every API request uses credentials: 'omit', so a browser-cached Basic credential is never attached", async () => {
    const fetchMock = installFetch((url) => (url.includes('/api/quotes/') ? jsonResponse(200, QUOTE) : jsonResponse(200, [])));
    const request = { productCode: 'TERM_LIFE', riskInputs: { age: 35 } };

    await listProducts();
    await getQuote(QUOTE.quoteId);
    await createQuote(request);
    await createQuote(request, CUSTOMER);
    await listMyApplications(CUSTOMER);
    await listMyPolicies(CUSTOMER);

    const all = calls(fetchMock);
    expect(all).toHaveLength(6);
    for (const call of all) {
      expect(call.init?.credentials).toBe('omit');
    }
    expect(header(all[2], 'Authorization')).toBeNull();
    expect(header(all[3], 'Authorization')).toBe(basic(CUSTOMER));
  });

  it("AC-01.13: a signed-out quote request from the page sends credentials: 'omit' and no Authorization header", async () => {
    const fetchMock = install(() => jsonResponse(201, QUOTE));
    const user = userEvent.setup();
    renderWithSession(<QuotePage />, null);

    await submitQuote(user);

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent('6850.00');
    const [post] = quotePosts(fetchMock);
    expect(post.init?.credentials).toBe('omit');
    expect(header(post, 'Authorization')).toBeNull();
  });

  it("AC-01.13: a signed-in quote request from the page sends credentials: 'omit' and only the app's Basic header", async () => {
    const fetchMock = install(() => jsonResponse(201, QUOTE));
    const user = userEvent.setup();
    renderWithSession(<QuotePage />, CUSTOMER);

    await submitQuote(user);

    expect(await screen.findByTestId('quote-premium')).toHaveTextContent('6850.00');
    const [post] = quotePosts(fetchMock);
    expect(post.init?.credentials).toBe('omit');
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-01.13: a 401 on a signed-in quote request clears the session and keeps the fixed sign-in-again message', async () => {
    const fetchMock = install(() => problem(401, { detail: 'Bad credentials for customer1' }));
    const user = userEvent.setup();
    renderWithSession(
      <>
        <SessionProbe />
        <QuotePage />
      </>,
      CUSTOMER,
    );
    expect(screen.getByTestId('probe-user')).toHaveTextContent('customer1');

    await submitQuote(user);

    const message = await screen.findByTestId('request-error');
    expect(message).toHaveTextContent(/sign in again/i);
    expect(message).not.toHaveTextContent('customer1');
    await waitFor(() => expect(screen.getByTestId('probe-user')).toHaveTextContent('signed-out'));
    expect(quotePosts(fetchMock)).toHaveLength(1);
  });

  it('AC-01.13: a 401 on an authenticated protected request signs the user out and shows the sign-in form', async () => {
    installFetch(() => problem(401));
    renderWithSession(
      <RequireSession>
        <MyPoliciesPage />
      </RequireSession>,
      CUSTOMER,
    );

    expect(await screen.findByTestId('signin-username')).toBeInTheDocument();
  });

  it('AC-01.13: a 401 on a request without the app Authorization header leaves the session alone', async () => {
    installFetch(() => problem(401));
    renderWithSession(<SessionProbe />, CUSTOMER);

    await expect(getQuote(QUOTE.quoteId)).rejects.toMatchObject({ status: 401 });

    expect(screen.getByTestId('probe-user')).toHaveTextContent('customer1');
  });
});
