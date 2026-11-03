import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { EndorsePage } from './EndorsePage';
import type { PolicyDetail } from '../api';
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
  postedJson,
  problem,
  renderWithSession,
} from '../test/helpers';
import type { Call } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

const DETAIL_URL = `/api/policies/${POLICY_NUMBER}`;
const ENDORSE_URL = `/api/policies/${POLICY_NUMBER}/endorsements`;
const PREVIEW_URL = `/api/policies/${POLICY_NUMBER}/endorsements/preview`;

const PREVIEW = {
  type: 'CHANGE_SUM_INSURED',
  effectiveDate: '2027-05-01',
  currentAnnualPremium: '6850.00',
  newAnnualPremium: '9490.00',
  adjustment: '1330.85',
  unexpiredDays: 184,
  termDays: 365,
};

interface Responses {
  detail?: PolicyDetail;
  endorse?: () => Response;
  preview?: () => Response;
}

function install(responses: Responses = {}) {
  return installFetch((url, init) => {
    if (url === DETAIL_URL) return jsonResponse(200, responses.detail ?? policyDetail());
    if (url === ENDORSE_URL && init?.method === 'POST') {
      return responses.endorse ? responses.endorse() : jsonResponse(201, { endorsement: endorsement(), policy: policyHeader() });
    }
    if (url === PREVIEW_URL && init?.method === 'POST') return responses.preview ? responses.preview() : jsonResponse(200, PREVIEW);
    return problem(404);
  });
}

function posts(fetchMock: ReturnType<typeof install>, url: string): Call[] {
  return calls(fetchMock).filter((call) => call.url === url && call.method === 'POST');
}

async function open(onEndorsed = vi.fn()) {
  const user = userEvent.setup();
  renderWithSession(<EndorsePage policyNumber={POLICY_NUMBER} onEndorsed={onEndorsed} />, CUSTOMER);
  await screen.findByTestId('endorse-type');
  return { user, onEndorsed };
}

async function chooseType(user: ReturnType<typeof userEvent.setup>, type: string) {
  await user.selectOptions(screen.getByTestId('endorse-type'), type);
}

async function fillAddress(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByTestId('input-line1'), '99 New Road');
  await user.type(screen.getByTestId('input-city'), 'Mumbai');
  await user.type(screen.getByTestId('input-postcode'), '400001');
}

async function fillNominee(user: ReturnType<typeof userEvent.setup>, share: string) {
  await user.type(screen.getByTestId('input-nomineeName'), 'Ravi Nominee');
  await user.selectOptions(screen.getByTestId('input-nomineeRelationship'), 'SPOUSE');
  await user.type(screen.getByTestId('input-nomineeSharePercent'), share);
}

describe('EndorsePage type selector and forms', () => {
  it('AC-06.1: the type selector offers all three types and each has its own form fields', async () => {
    const { user } = await open();

    const options = within(screen.getByTestId('endorse-type')).getAllByRole('option');
    expect(options.map((option) => option.getAttribute('value'))).toEqual(['CHANGE_ADDRESS', 'ADD_NOMINEE', 'CHANGE_SUM_INSURED']);

    for (const id of ['line1', 'city', 'postcode', 'effectiveDate']) {
      expect(screen.getByTestId(`input-${id}`)).toBeInTheDocument();
    }
    await chooseType(user, 'ADD_NOMINEE');
    for (const id of ['nomineeName', 'nomineeRelationship', 'nomineeSharePercent', 'effectiveDate']) {
      expect(screen.getByTestId(`input-${id}`)).toBeInTheDocument();
    }
    expect(screen.queryByTestId('input-line1')).not.toBeInTheDocument();
    await chooseType(user, 'CHANGE_SUM_INSURED');
    for (const id of ['newSumInsured', 'effectiveDate']) {
      expect(screen.getByTestId(`input-${id}`)).toBeInTheDocument();
    }
    expect(screen.queryByTestId('input-newBuildingSumInsured')).not.toBeInTheDocument();
  });

  it('AC-06.5: the relationship select offers exactly the five spec values', async () => {
    const { user } = await open();
    await chooseType(user, 'ADD_NOMINEE');

    const values = within(screen.getByTestId('input-nomineeRelationship'))
      .getAllByRole('option')
      .map((option) => option.getAttribute('value'))
      .filter((value) => value !== '');
    expect(values).toEqual(['SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER']);
  });

  it('AC-06.2: a HOUSEHOLD policy shows the building and contents fields instead of newSumInsured', async () => {
    install({ detail: policyDetail({ policy: policyHeader({ productCode: 'HOUSEHOLD' }) }) });
    const user = userEvent.setup();
    renderWithSession(<EndorsePage policyNumber={POLICY_NUMBER} />, CUSTOMER);
    await screen.findByTestId('endorse-type');

    await chooseType(user, 'CHANGE_SUM_INSURED');

    expect(screen.getByTestId('input-newBuildingSumInsured')).toBeInTheDocument();
    expect(screen.getByTestId('input-newContentsSumInsured')).toBeInTheDocument();
    expect(screen.queryByTestId('input-newSumInsured')).not.toBeInTheDocument();
  });

  it('AC-10.4: shows a fixed not-found message when the policy does not exist', async () => {
    installFetch(() => problem(404, { detail: 'Policy PF-TL-2026-000001 unknown for customer1' }));
    renderWithSession(<EndorsePage policyNumber={POLICY_NUMBER} />, CUSTOMER);

    const message = await screen.findByTestId('endorse-not-found');
    expect(message).toHaveTextContent('We could not find that policy.');
    expect(message).not.toHaveTextContent('customer1');
    expect(screen.queryByTestId('endorse-type')).not.toBeInTheDocument();
  });
});

describe('EndorsePage confirm', () => {
  it('AC-06.1: an address change posts exactly the C4 body, with no effectiveDate when none was given, then navigates', async () => {
    const fetchMock = install();
    const { user, onEndorsed } = await open();

    await fillAddress(user);
    await user.click(screen.getByTestId('endorse-confirm'));

    await waitFor(() => expect(onEndorsed).toHaveBeenCalledWith(POLICY_NUMBER));
    const [post] = posts(fetchMock, ENDORSE_URL);
    expect(postedJson(post)).toEqual({
      type: 'CHANGE_ADDRESS',
      address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' },
    });
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(header(post, 'Content-Type')).toBe('application/json');
  });

  it('AC-06.1: an optional effective date is sent as effectiveDate', async () => {
    const fetchMock = install();
    const { user, onEndorsed } = await open();

    await fillAddress(user);
    await user.type(screen.getByTestId('input-effectiveDate'), '2026-11-20');
    await user.click(screen.getByTestId('endorse-confirm'));

    await waitFor(() => expect(onEndorsed).toHaveBeenCalledTimes(1));
    expect(postedJson(posts(fetchMock, ENDORSE_URL)[0])).toEqual({
      type: 'CHANGE_ADDRESS',
      address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' },
      effectiveDate: '2026-11-20',
    });
  });

  it('AC-06.1: a blank required field is flagged at the input and nothing is posted', async () => {
    const fetchMock = install();
    const { user, onEndorsed } = await open();

    await user.type(screen.getByTestId('input-line1'), '99 New Road');
    await user.click(screen.getByTestId('endorse-confirm'));

    expect(await screen.findByTestId('error-city')).toHaveTextContent('This field is required.');
    expect(screen.getByTestId('error-postcode')).toHaveTextContent('This field is required.');
    expect(screen.queryByTestId('error-line1')).not.toBeInTheDocument();
    expect(posts(fetchMock, ENDORSE_URL)).toHaveLength(0);
    expect(onEndorsed).not.toHaveBeenCalled();
  });

  it('AC-06.1: a 400 errors[] entry shows next to the input whose dotted path matches its field', async () => {
    install({
      endorse: () =>
        problem(400, {
          code: 'POSTCODE_INVALID',
          errors: [{ field: 'address.postcode', message: 'Postcode must be 6 digits.' }],
        }),
    });
    const { user, onEndorsed } = await open();

    await fillAddress(user);
    await user.click(screen.getByTestId('endorse-confirm'));

    const field = await screen.findByTestId('error-postcode');
    expect(field).toHaveTextContent('Postcode must be 6 digits.');
    expect(screen.getByTestId('field-postcode')).toContainElement(field);
    expect(screen.getByTestId('endorse-error')).toHaveTextContent('The postcode must be exactly 6 digits.');
    expect(onEndorsed).not.toHaveBeenCalled();
  });

  it('AC-06.4: NOMINEE_NOT_SUPPORTED is shown as a fixed message', async () => {
    install({
      detail: policyDetail({ policy: policyHeader({ productCode: 'MOTOR' }) }),
      endorse: () =>
        problem(400, {
          code: 'NOMINEE_NOT_SUPPORTED',
          errors: [{ field: 'nominee', message: 'Nominees are not supported for this product.' }],
        }),
    });
    const { user, onEndorsed } = await open();
    await chooseType(user, 'ADD_NOMINEE');
    await fillNominee(user, '30');

    await user.click(screen.getByTestId('endorse-confirm'));

    const banner = await screen.findByTestId('endorse-error');
    expect(banner).toHaveTextContent('Nominees can only be added to Term Life policies.');
    expect(banner).toHaveAttribute('data-code', 'NOMINEE_NOT_SUPPORTED');
    expect(onEndorsed).not.toHaveBeenCalled();
  });

  it('AC-06.5: NOMINEE_SHARE_EXCEEDED is shown, with the share error at the share input, and the body carries an integer share', async () => {
    const fetchMock = install({
      endorse: () =>
        problem(400, {
          code: 'NOMINEE_SHARE_EXCEEDED',
          errors: [{ field: 'nominee.sharePercent', message: 'Total nominee share would exceed 100%.' }],
        }),
    });
    const { user, onEndorsed } = await open();
    await chooseType(user, 'ADD_NOMINEE');
    await fillNominee(user, '30');

    await user.click(screen.getByTestId('endorse-confirm'));

    expect(await screen.findByTestId('endorse-error')).toHaveTextContent('The total nominee share cannot exceed 100%.');
    expect(screen.getByTestId('error-nomineeSharePercent')).toHaveTextContent('Total nominee share would exceed 100%.');
    expect(postedJson(posts(fetchMock, ENDORSE_URL)[0])).toEqual({
      type: 'ADD_NOMINEE',
      nominee: { name: 'Ravi Nominee', relationship: 'SPOUSE', sharePercent: 30 },
    });
    expect(onEndorsed).not.toHaveBeenCalled();
  });

  it.each([
    ['NOMINEE_LIMIT_REACHED', 'A policy can have at most 3 nominees.'],
    ['NOMINEE_RELATIONSHIP_INVALID', 'Choose one of the listed relationships.'],
  ])('AC-06.5: %s is shown as a fixed message', async (code, text) => {
    install({ endorse: () => problem(400, { code, errors: [{ field: 'nominee', message: 'Server message.' }] }) });
    const { user } = await open();
    await chooseType(user, 'ADD_NOMINEE');
    await fillNominee(user, '10');

    await user.click(screen.getByTestId('endorse-confirm'));

    expect(await screen.findByTestId('endorse-error')).toHaveTextContent(text);
  });

  it.each([
    ['EFFECTIVE_DATE_OUT_OF_TERM', 'The effective date must fall within the current policy term.'],
    ['EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT', 'The effective date cannot be before the latest endorsement.'],
  ])('AC-06.6: %s is shown as a fixed message and the date error sits at the date input', async (code, text) => {
    install({ endorse: () => problem(400, { code, errors: [{ field: 'effectiveDate', message: 'Server date message.' }] }) });
    const { user } = await open();
    await fillAddress(user);
    await user.type(screen.getByTestId('input-effectiveDate'), '2030-01-01');

    await user.click(screen.getByTestId('endorse-confirm'));

    expect(await screen.findByTestId('endorse-error')).toHaveTextContent(text);
    expect(screen.getByTestId('error-effectiveDate')).toHaveTextContent('Server date message.');
  });

  it('AC-06.6: a lost race (409 POLICY_UPDATE_CONFLICT) shows a fixed message and does not navigate', async () => {
    install({ endorse: () => problem(409, { code: 'POLICY_UPDATE_CONFLICT', detail: 'row 42 version 3' }) });
    const { user, onEndorsed } = await open();
    await fillAddress(user);

    await user.click(screen.getByTestId('endorse-confirm'));

    const banner = await screen.findByTestId('endorse-error');
    expect(banner).toHaveTextContent('The policy was changed by another request. Reload the policy and try again.');
    expect(banner).not.toHaveTextContent('row 42');
    expect(onEndorsed).not.toHaveBeenCalled();
  });

  it('AC-10.2: a 409 invalid-policy-state is shown as a fixed message and does not navigate', async () => {
    install({
      endorse: () =>
        problem(409, {
          type: '/problems/invalid-policy-state',
          code: 'INVALID_POLICY_STATE',
          detail: 'Policy is CANCELLED',
        }),
    });
    const { user, onEndorsed } = await open();
    await fillAddress(user);

    await user.click(screen.getByTestId('endorse-confirm'));

    const banner = await screen.findByTestId('endorse-error');
    expect(banner).toHaveTextContent('This policy is not in force, so it cannot be endorsed.');
    expect(banner).not.toHaveTextContent('CANCELLED');
    expect(onEndorsed).not.toHaveBeenCalled();
  });
});

describe('EndorsePage sum-insured preview', () => {
  async function openSumInsured() {
    const context = await open();
    await chooseType(context.user, 'CHANGE_SUM_INSURED');
    return context;
  }

  it('AC-06.2: Confirm stays disabled until a preview succeeds, then shows 9490.00 and 1330.85 exactly as received', async () => {
    const fetchMock = install();
    const { user } = await openSumInsured();

    expect(screen.getByTestId('endorse-confirm')).toBeDisabled();
    expect(screen.queryByTestId('preview-result')).not.toBeInTheDocument();

    await user.type(screen.getByTestId('input-newSumInsured'), '7000000.00');
    await user.type(screen.getByTestId('input-effectiveDate'), '2027-05-01');
    expect(screen.getByTestId('endorse-confirm')).toBeDisabled();
    await user.click(screen.getByTestId('endorse-preview'));

    expect(await screen.findByTestId('preview-new-premium')).toHaveTextContent('9490.00');
    expect(screen.getByTestId('preview-current-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('preview-adjustment')).toHaveTextContent('1330.85');
    expect(screen.getByTestId('preview-unexpired-days')).toHaveTextContent('184');
    expect(screen.getByTestId('preview-term-days')).toHaveTextContent('365');
    expect(screen.getByTestId('endorse-confirm')).toBeEnabled();

    const [post] = posts(fetchMock, PREVIEW_URL);
    expect(postedJson(post)).toEqual({ type: 'CHANGE_SUM_INSURED', newSumInsured: '7000000.00', effectiveDate: '2027-05-01' });
    expect(header(post, 'Authorization')).toBe(basic(CUSTOMER));
    expect(posts(fetchMock, ENDORSE_URL)).toHaveLength(0);
  });

  it('AC-06.2: confirming after a preview posts exactly the C4 body and navigates', async () => {
    const fetchMock = install();
    const { user, onEndorsed } = await openSumInsured();

    await user.type(screen.getByTestId('input-newSumInsured'), '7000000.00');
    await user.type(screen.getByTestId('input-effectiveDate'), '2027-05-01');
    await user.click(screen.getByTestId('endorse-preview'));
    await screen.findByTestId('preview-result');
    await user.click(screen.getByTestId('endorse-confirm'));

    await waitFor(() => expect(onEndorsed).toHaveBeenCalledWith(POLICY_NUMBER));
    expect(postedJson(posts(fetchMock, ENDORSE_URL)[0])).toEqual({
      type: 'CHANGE_SUM_INSURED',
      newSumInsured: '7000000.00',
      effectiveDate: '2027-05-01',
    });
  });

  it('AC-06.2: editing any input clears the preview and disables Confirm again', async () => {
    install();
    const { user } = await openSumInsured();
    await user.type(screen.getByTestId('input-newSumInsured'), '7000000.00');
    await user.click(screen.getByTestId('endorse-preview'));
    await screen.findByTestId('preview-result');
    expect(screen.getByTestId('endorse-confirm')).toBeEnabled();

    await user.type(screen.getByTestId('input-newSumInsured'), '1');

    expect(screen.queryByTestId('preview-result')).not.toBeInTheDocument();
    expect(screen.getByTestId('endorse-confirm')).toBeDisabled();

    await user.click(screen.getByTestId('endorse-preview'));
    await screen.findByTestId('preview-result');
    await user.type(screen.getByTestId('input-effectiveDate'), '2027-05-01');
    expect(screen.queryByTestId('preview-result')).not.toBeInTheDocument();
    expect(screen.getByTestId('endorse-confirm')).toBeDisabled();
  });

  it('AC-06.2: a HOUSEHOLD preview and confirm send only the components that were filled in', async () => {
    const fetchMock = install({ detail: policyDetail({ policy: policyHeader({ productCode: 'HOUSEHOLD' }) }) });
    const { user, onEndorsed } = await open();
    await chooseType(user, 'CHANGE_SUM_INSURED');

    await user.type(screen.getByTestId('input-newBuildingSumInsured'), '4500000.00');
    await user.click(screen.getByTestId('endorse-preview'));
    await screen.findByTestId('preview-result');
    await user.click(screen.getByTestId('endorse-confirm'));

    await waitFor(() => expect(onEndorsed).toHaveBeenCalledTimes(1));
    expect(postedJson(posts(fetchMock, PREVIEW_URL)[0])).toEqual({ type: 'CHANGE_SUM_INSURED', newBuildingSumInsured: '4500000.00' });
    expect(postedJson(posts(fetchMock, ENDORSE_URL)[0])).toEqual({ type: 'CHANGE_SUM_INSURED', newBuildingSumInsured: '4500000.00' });
  });

  it('AC-06.2: a blank sum-insured is flagged at the input and no preview is requested', async () => {
    const fetchMock = install();
    const { user } = await openSumInsured();

    await user.click(screen.getByTestId('endorse-preview'));

    expect(await screen.findByTestId('error-newSumInsured')).toHaveTextContent('This field is required.');
    expect(posts(fetchMock, PREVIEW_URL)).toHaveLength(0);
  });

  it('AC-06.2: a 400 SUM_INSURED_OUT_OF_RANGE on preview shows at the input and leaves Confirm disabled', async () => {
    install({
      preview: () =>
        problem(400, {
          code: 'SUM_INSURED_OUT_OF_RANGE',
          errors: [{ field: 'newSumInsured', message: 'Sum insured is outside the allowed range.' }],
        }),
    });
    const { user } = await openSumInsured();
    await user.type(screen.getByTestId('input-newSumInsured'), '99999999999.00');

    await user.click(screen.getByTestId('endorse-preview'));

    expect(await screen.findByTestId('error-newSumInsured')).toHaveTextContent('Sum insured is outside the allowed range.');
    expect(screen.getByTestId('endorse-error')).toHaveTextContent('The new sum insured is outside the allowed range for this policy.');
    expect(screen.queryByTestId('preview-result')).not.toBeInTheDocument();
    expect(screen.getByTestId('endorse-confirm')).toBeDisabled();
  });

  it('AC-06.2: SUM_INSURED_UNCHANGED is shown as a fixed message', async () => {
    install({ preview: () => problem(400, { code: 'SUM_INSURED_UNCHANGED', errors: [{ field: 'newSumInsured', message: 'Same value.' }] }) });
    const { user } = await openSumInsured();
    await user.type(screen.getByTestId('input-newSumInsured'), '5000000.00');

    await user.click(screen.getByTestId('endorse-preview'));

    expect(await screen.findByTestId('endorse-error')).toHaveTextContent('The new sum insured must differ from the current one.');
  });

  it('AC-10.2: a 409 invalid-policy-state on preview is shown as a fixed message', async () => {
    install({
      preview: () => problem(409, { type: '/problems/invalid-policy-state', code: 'INVALID_POLICY_STATE', detail: 'Policy is LAPSED' }),
    });
    const { user } = await openSumInsured();
    await user.type(screen.getByTestId('input-newSumInsured'), '7000000.00');

    await user.click(screen.getByTestId('endorse-preview'));

    const banner = await screen.findByTestId('endorse-error');
    expect(banner).toHaveTextContent('This policy is not in force, so it cannot be endorsed.');
    expect(banner).not.toHaveTextContent('LAPSED');
    expect(screen.queryByTestId('preview-result')).not.toBeInTheDocument();
  });
});
