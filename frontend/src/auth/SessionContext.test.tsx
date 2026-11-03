import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RequireSession } from './RequireSession';
import { SessionProvider } from './SessionContext';
import { createQuote, getQuote, listProducts, listMyApplications } from '../api';
import { basic, calls, CUSTOMER, header, installFetch, jsonResponse } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
  window.sessionStorage.clear();
  window.localStorage.clear();
});

describe('session', () => {
  it('NFR-04.1: the sign-in form holds credentials in memory only, never in storage, cookies or the URL', async () => {
    const user = userEvent.setup();
    const urlBefore = window.location.href;
    render(
      <SessionProvider>
        <RequireSession>
          <p data-testid="protected-content">inside</p>
        </RequireSession>
      </SessionProvider>,
    );

    expect(screen.queryByTestId('protected-content')).not.toBeInTheDocument();
    await user.type(screen.getByTestId('signin-username'), CUSTOMER.username);
    await user.type(screen.getByTestId('signin-password'), CUSTOMER.password);
    await user.click(screen.getByTestId('signin-submit'));

    expect(await screen.findByTestId('protected-content')).toBeInTheDocument();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).toBe('');
    expect(window.location.href).toBe(urlBefore);
    expect(JSON.stringify(window.localStorage) + JSON.stringify(window.sessionStorage)).not.toContain(CUSTOMER.password);
  });

  it('NFR-04.1: protected calls carry the Basic Authorization header and public calls do not', async () => {
    const fetchMock = installFetch(() => jsonResponse(200, []));

    await listProducts();
    await getQuote('Q-123e4567-e89b-42d3-a456-426614174000');
    await createQuote({ productCode: 'TERM_LIFE', riskInputs: {} });
    await listMyApplications(CUSTOMER);

    const [products, quote, create, mine] = calls(fetchMock);
    expect(header(products, 'Authorization')).toBeNull();
    expect(header(quote, 'Authorization')).toBeNull();
    expect(header(create, 'Authorization')).toBeNull();
    expect(header(mine, 'Authorization')).toBe(basic(CUSTOMER));
  });
});
