import { screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApplicationStatusPage } from './ApplicationStatusPage';
import { application, basic, calls, CUSTOMER, header, installFetch, jsonResponse, renderWithSession } from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('ApplicationStatusPage', () => {
  it('AC-03.8: renders the matching application from the caller list, with no KYC fields', async () => {
    const mine = application({ applicationId: 'A-aaaaaaaa-0000-4000-8000-000000000001' });
    const other = application({
      applicationId: 'A-bbbbbbbb-0000-4000-8000-000000000002',
      status: 'DECLINED',
      decision: 'DECLINE',
      reasonCodes: ['UW-TL-001'],
      reasons: [{ code: 'UW-TL-001', description: 'Age above 60' }],
    });
    const fetchMock = installFetch(() => jsonResponse(200, [other, mine]));

    renderWithSession(<ApplicationStatusPage applicationId={mine.applicationId} />, CUSTOMER);

    expect(await screen.findByTestId('application-id')).toHaveTextContent(mine.applicationId);
    expect(screen.getByTestId('status-badge')).toHaveTextContent('APPROVED');
    expect(screen.getByTestId('application-rule-version')).toHaveTextContent('1');
    for (const kyc of ['fullName', 'dateOfBirth', 'aadhaar', 'pan', 'address']) {
      expect(screen.queryByTestId(`kyc-${kyc}`)).not.toBeInTheDocument();
    }
    expect(document.body.textContent).not.toMatch(/Asha|XXXX-XXXX|ABCDE1234F|\*\*\*\*/);
    const [call] = calls(fetchMock);
    expect(call.url).toBe('/api/applications/mine');
    expect(header(call, 'Authorization')).toBe(basic(CUSTOMER));
  });

  it('AC-03.8: shows a fixed not-found message for an ID that is not in the list', async () => {
    installFetch(() => jsonResponse(200, [application()]));

    renderWithSession(<ApplicationStatusPage applicationId="A-99999999-0000-4000-8000-000000000009" />, CUSTOMER);

    expect(await screen.findByTestId('application-not-found')).toHaveTextContent('We could not find that application.');
    expect(screen.queryByTestId('status-badge')).not.toBeInTheDocument();
  });

  it('AC-04.1: shows APPROVED, AUTO_BIND and UW-OK with its description', async () => {
    installFetch(() => jsonResponse(200, [application()]));

    renderWithSession(<ApplicationStatusPage applicationId={application().applicationId} />, CUSTOMER);

    const badge = await screen.findByTestId('status-badge');
    expect(badge).toHaveTextContent('APPROVED');
    expect(badge).toHaveAttribute('data-status', 'APPROVED');
    expect(screen.getByTestId('application-decision')).toHaveTextContent('AUTO_BIND');
    const item = screen.getByTestId('reason-item-UW-OK');
    expect(item).toHaveTextContent('UW-OK');
    expect(item).toHaveTextContent('No underwriting rule fired');
  });

  it('AC-04.3: lists DECLINE reasons UW-TL-001 then UW-TL-004 in API order', async () => {
    const declined = application({
      status: 'DECLINED',
      decision: 'DECLINE',
      reasonCodes: ['UW-TL-001', 'UW-TL-004'],
      reasons: [
        { code: 'UW-TL-001', description: 'Age above 60' },
        { code: 'UW-TL-004', description: 'BMI above 35' },
      ],
    });
    installFetch(() => jsonResponse(200, [declined]));

    renderWithSession(<ApplicationStatusPage applicationId={declined.applicationId} />, CUSTOMER);

    expect(await screen.findByTestId('status-badge')).toHaveTextContent('DECLINED');
    expect(screen.getByTestId('application-decision')).toHaveTextContent('DECLINE');
    const items = within(screen.getByTestId('reason-list')).getAllByTestId(/^reason-item-/);
    expect(items.map((item) => item.getAttribute('data-testid'))).toEqual(['reason-item-UW-TL-001', 'reason-item-UW-TL-004']);
    expect(items[0]).toHaveTextContent('Age above 60');
    expect(items[1]).toHaveTextContent('BMI above 35');
  });
});
