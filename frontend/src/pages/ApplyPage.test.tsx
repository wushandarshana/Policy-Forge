import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApplyPage } from './ApplyPage';
import { application, basic, calls, CUSTOMER, header, installFetch, jsonResponse, postedJson, problem, QUOTE_ID, renderWithSession } from '../test/helpers';
import type { Handler } from '../test/helpers';

type User = ReturnType<typeof userEvent.setup>;

const VALID_KYC = {
  fullName: 'Asha Test',
  dateOfBirth: '1991-04-02',
  aadhaar: 'XXXX-XXXX-1234',
  pan: 'ABCDE1234F',
  line1: '1 Demo Street',
  city: 'Pune',
  postcode: '411001',
};

afterEach(() => {
  vi.unstubAllGlobals();
});

async function fillKyc(user: User, overrides: Partial<typeof VALID_KYC> = {}) {
  const values = { ...VALID_KYC, ...overrides };
  await user.type(screen.getByTestId('input-fullName'), values.fullName);
  await user.type(screen.getByTestId('input-dateOfBirth'), values.dateOfBirth);
  await user.type(screen.getByTestId('input-aadhaar'), values.aadhaar);
  await user.type(screen.getByTestId('input-pan'), values.pan);
  await user.type(screen.getByTestId('input-line1'), values.line1);
  await user.type(screen.getByTestId('input-city'), values.city);
  await user.type(screen.getByTestId('input-postcode'), values.postcode);
}

function setup(handler: Handler, onSubmitted?: (applicationId: string) => void) {
  const fetchMock = installFetch(handler);
  const user = userEvent.setup();
  renderWithSession(<ApplyPage quoteId={QUOTE_ID} onSubmitted={onSubmitted} />, CUSTOMER);
  return { fetchMock, user };
}

describe('ApplyPage', () => {
  it('AC-03.1: posts exactly the C1 body with the Basic header and reports the new application ID', async () => {
    const created = application();
    const onSubmitted = vi.fn();
    const { fetchMock, user } = setup(() => jsonResponse(201, created), onSubmitted);

    await fillKyc(user);
    await user.click(screen.getByTestId('submit-application'));

    await waitFor(() => expect(onSubmitted).toHaveBeenCalledWith(created.applicationId));
    const [post] = calls(fetchMock);
    expect(post.url).toBe('/api/applications');
    expect(post.method).toBe('POST');
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(header(post, 'Content-Type')).toBe('application/json');
    expect(postedJson(post)).toEqual({
      quoteId: QUOTE_ID,
      kyc: {
        fullName: 'Asha Test',
        dateOfBirth: '1991-04-02',
        aadhaar: 'XXXX-XXXX-1234',
        pan: 'ABCDE1234F',
        address: { line1: '1 Demo Street', city: 'Pune', postcode: '411001' },
      },
    });
  });

  it('AC-03.2: shows each errors[] entry next to its PAN, Aadhaar and postcode input and does not navigate', async () => {
    const onSubmitted = vi.fn();
    const { fetchMock, user } = setup(
      () =>
        problem(400, {
          detail: 'Validation failed',
          errors: [
            { field: 'kyc.pan', message: 'PAN is not in the expected format' },
            { field: 'kyc.aadhaar', message: 'Aadhaar is not in the expected format' },
            { field: 'kyc.address.postcode', message: 'Postcode must be 6 digits' },
          ],
        }),
      onSubmitted,
    );

    await fillKyc(user, { pan: 'abcde1234f', aadhaar: '1234-5678', postcode: '41100' });
    await user.click(screen.getByTestId('submit-application'));

    expect(await within(screen.getByTestId('field-pan')).findByText('PAN is not in the expected format')).toBeInTheDocument();
    expect(within(screen.getByTestId('field-aadhaar')).getByText('Aadhaar is not in the expected format')).toBeInTheDocument();
    expect(within(screen.getByTestId('field-postcode')).getByText('Postcode must be 6 digits')).toBeInTheDocument();
    expect(screen.getByTestId('error-pan')).toHaveTextContent('PAN is not in the expected format');
    expect(screen.queryByTestId('error-fullName')).not.toBeInTheDocument();
    expect(onSubmitted).not.toHaveBeenCalled();
    // Malformed values reach the server unchanged: format checks are the server's job.
    const body = postedJson(calls(fetchMock)[0]) as { kyc: { pan: string; aadhaar: string; address: { postcode: string } } };
    expect(body.kyc.pan).toBe('abcde1234f');
    expect(body.kyc.aadhaar).toBe('1234-5678');
    expect(body.kyc.address.postcode).toBe('41100');
  });

  it('AC-03.2: client-side checks cover required fields only and block the request', async () => {
    const { fetchMock, user } = setup(() => jsonResponse(201, application()));

    await user.type(screen.getByTestId('input-fullName'), 'Asha Test');
    await user.click(screen.getByTestId('submit-application'));

    expect(await screen.findByTestId('error-pan')).toBeInTheDocument();
    expect(screen.getByTestId('error-aadhaar')).toBeInTheDocument();
    expect(screen.getByTestId('error-dateOfBirth')).toBeInTheDocument();
    expect(screen.getByTestId('error-line1')).toBeInTheDocument();
    expect(screen.getByTestId('error-city')).toBeInTheDocument();
    expect(screen.getByTestId('error-postcode')).toBeInTheDocument();
    expect(screen.queryByTestId('error-fullName')).not.toBeInTheDocument();
    expect(calls(fetchMock)).toHaveLength(0);
  });

  it('AC-03.3: shows the fixed QUOTE_EXPIRED message', async () => {
    const { user } = setup(() =>
      problem(400, { code: 'QUOTE_EXPIRED', detail: 'Quote has expired', errors: [{ field: 'quoteId', message: 'Quote has expired' }] }),
    );

    await fillKyc(user);
    await user.click(screen.getByTestId('submit-application'));

    const banner = await screen.findByTestId('apply-error');
    expect(banner).toHaveAttribute('data-code', 'QUOTE_EXPIRED');
    expect(banner).toHaveTextContent('This quote has expired. Please request a new quote.');
  });

  it('AC-03.4: shows the fixed KYC_AGE_MISMATCH message at the date of birth input', async () => {
    const { user } = setup(() =>
      problem(400, { code: 'KYC_AGE_MISMATCH', detail: 'Age mismatch', errors: [{ field: 'kyc.dateOfBirth', message: 'Age mismatch' }] }),
    );

    await fillKyc(user, { dateOfBirth: '1985-04-02' });
    await user.click(screen.getByTestId('submit-application'));

    const error = await within(screen.getByTestId('field-dateOfBirth')).findByTestId('error-dateOfBirth');
    expect(error).toHaveTextContent('The date of birth does not match the age used for this quote.');
  });

  it('AC-03.6: shows the fixed QUOTE_NOT_FOUND message', async () => {
    const { user } = setup(() =>
      problem(400, { code: 'QUOTE_NOT_FOUND', detail: 'Quote not found', errors: [{ field: 'quoteId', message: 'Quote not found' }] }),
    );

    await fillKyc(user);
    await user.click(screen.getByTestId('submit-application'));

    const banner = await screen.findByTestId('apply-error');
    expect(banner).toHaveAttribute('data-code', 'QUOTE_NOT_FOUND');
    expect(banner).toHaveTextContent('We could not find that quote. Please request a new quote.');
  });

  it('AC-03.7: shows the fixed QUOTE_ALREADY_APPLIED message after a 409', async () => {
    const { user } = setup(() => problem(409, { code: 'QUOTE_ALREADY_APPLIED', detail: 'Quote already has an application' }));

    await fillKyc(user);
    await user.click(screen.getByTestId('submit-application'));

    const banner = await screen.findByTestId('apply-error');
    expect(banner).toHaveAttribute('data-code', 'QUOTE_ALREADY_APPLIED');
    expect(banner).toHaveTextContent('This quote already has an application. Request a new quote to apply again.');
  });
});
