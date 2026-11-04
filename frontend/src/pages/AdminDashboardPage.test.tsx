import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AdminDashboardPage } from './AdminDashboardPage';
import {
  ADMIN,
  basic,
  calls,
  CUSTOMER,
  dashboard,
  header,
  installFetch,
  jsonResponse,
  problem,
  renderWithSession,
} from '../test/helpers';

afterEach(() => {
  vi.unstubAllGlobals();
});

const DASHBOARD_URL = '/api/admin/dashboard';

function show(body = dashboard()) {
  const fetchMock = installFetch(() => jsonResponse(200, body));
  renderWithSession(<AdminDashboardPage />, ADMIN);
  return fetchMock;
}

describe('AdminDashboardPage', () => {
  it('AC-11.1: loads the dashboard without asOf, and shows the active tile as the sum of the three counts and the per-product table', async () => {
    const fetchMock = show();

    expect(await screen.findByTestId('kpi-active')).toHaveTextContent('7');
    expect(screen.getByTestId('product-TERM_LIFE-active')).toHaveTextContent('3');
    expect(screen.getByTestId('product-MOTOR-active')).toHaveTextContent('2');
    expect(screen.getByTestId('product-HOUSEHOLD-active')).toHaveTextContent('2');
    const rows = screen.getAllByTestId(/^product-row-[A-Z_]+$/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([
      'product-row-TERM_LIFE',
      'product-row-MOTOR',
      'product-row-HOUSEHOLD',
    ]);
    const [call, ...others] = calls(fetchMock);
    expect(others).toEqual([]);
    expect(call.url).toBe(DASHBOARD_URL);
    expect(header(call, 'Authorization')).toBe(basic(ADMIN));
  });

  it('AC-11.1: the active tile shows 0 when no product has an in-force policy', async () => {
    show(dashboard({ activePoliciesByProduct: { TERM_LIFE: 0, MOTOR: 0, HOUSEHOLD: 0 } }));

    expect(await screen.findByTestId('kpi-active')).toHaveTextContent('0');
    expect(screen.getByTestId('product-MOTOR-active')).toHaveTextContent('0');
  });

  it('AC-11.1: the optional As-of input sends asOf only when given, and the load button reloads', async () => {
    const user = userEvent.setup();
    const fetchMock = show();
    await screen.findByTestId('kpi-active');
    expect(screen.getByTestId('dashboard-asof')).toHaveValue('');

    await user.type(screen.getByTestId('dashboard-asof'), '2027-10-15');
    await user.click(screen.getByTestId('dashboard-load'));

    await vi.waitFor(() => expect(calls(fetchMock)).toHaveLength(2));
    expect(calls(fetchMock)[1].url).toBe(`${DASHBOARD_URL}?asOf=2027-10-15`);

    await user.clear(screen.getByTestId('dashboard-asof'));
    await user.click(screen.getByTestId('dashboard-load'));
    await vi.waitFor(() => expect(calls(fetchMock)).toHaveLength(3));
    expect(calls(fetchMock)[2].url).toBe(DASHBOARD_URL);
  });

  it('AC-11.1: a 400 on asOf shows the server message at the As-of input and no page error', async () => {
    const user = userEvent.setup();
    let first = true;
    installFetch(() => {
      if (first) {
        first = false;
        return jsonResponse(200, dashboard());
      }
      return problem(400, { errors: [{ field: 'asOf', message: 'asOf must be a date in YYYY-MM-DD form.' }] });
    });
    renderWithSession(<AdminDashboardPage />, ADMIN);
    await screen.findByTestId('kpi-active');

    await user.type(screen.getByTestId('dashboard-asof'), '2027-10-15');
    await user.click(screen.getByTestId('dashboard-load'));

    expect(await screen.findByTestId('dashboard-asof-error')).toHaveTextContent('asOf must be a date in YYYY-MM-DD form.');
    expect(screen.queryByTestId('dashboard-error')).not.toBeInTheDocument();
  });

  it('AC-11.2: premium collected per product and the total are shown verbatim', async () => {
    show();

    expect(await screen.findByTestId('kpi-premium')).toHaveTextContent('73550.00');
    expect(screen.getByTestId('product-TERM_LIFE-premium')).toHaveTextContent('20550.00');
    expect(screen.getByTestId('product-MOTOR-premium')).toHaveTextContent('30300.00');
    expect(screen.getByTestId('product-HOUSEHOLD-premium')).toHaveTextContent('22700.00');
  });

  it('AC-11.2: an exact decimal total is shown as sent, with no rounding drift', async () => {
    show(dashboard({ premiumCollected: { TERM_LIFE: '10557.56', MOTOR: '0.00', HOUSEHOLD: '0.00', total: '10557.56' } }));

    expect(await screen.findByTestId('kpi-premium')).toHaveTextContent('10557.56');
    expect(screen.getByTestId('product-TERM_LIFE-premium')).toHaveTextContent('10557.56');
  });

  it('AC-11.3: the renewal pipeline lists number, term end, renewal premium and paid state, and shows No offer yet for a null premium', async () => {
    show(
      dashboard({
        renewalPipeline: [
          { policyNumber: 'PF-TL-2026-000001', termEnd: '2027-10-31', renewalPremium: '6850.00', paid: false },
          { policyNumber: 'PF-MO-2026-000002', termEnd: '2027-11-05', renewalPremium: null, paid: false },
          { policyNumber: 'PF-HO-2026-000003', termEnd: '2027-11-20', renewalPremium: '11350.00', paid: true },
        ],
      }),
    );

    await screen.findByTestId('pipeline-row-PF-TL-2026-000001');
    const rows = screen.getAllByTestId(/^pipeline-row-PF-/);
    expect(rows.map((row) => row.getAttribute('data-testid'))).toEqual([
      'pipeline-row-PF-TL-2026-000001',
      'pipeline-row-PF-MO-2026-000002',
      'pipeline-row-PF-HO-2026-000003',
    ]);
    expect(within(rows[0]).getByText('2027-10-31')).toBeInTheDocument();
    expect(screen.getByTestId('pipeline-PF-TL-2026-000001-premium')).toHaveTextContent('6850.00');
    expect(screen.getByTestId('pipeline-PF-TL-2026-000001-paid')).toHaveTextContent('Unpaid');
    expect(screen.getByTestId('pipeline-PF-MO-2026-000002-premium')).toHaveTextContent('No offer yet');
    expect(screen.getByTestId('pipeline-PF-MO-2026-000002-paid')).toHaveTextContent('Unpaid');
    expect(screen.getByTestId('pipeline-PF-HO-2026-000003-premium')).toHaveTextContent('11350.00');
    expect(screen.getByTestId('pipeline-PF-HO-2026-000003-paid')).toHaveTextContent('Paid');
  });

  it('AC-11.3: an empty pipeline shows an empty state', async () => {
    show(dashboard({ renewalPipeline: [] }));

    expect(await screen.findByTestId('pipeline-empty')).toBeInTheDocument();
    expect(screen.queryAllByTestId(/^pipeline-row-/)).toEqual([]);
  });

  it('AC-11.4: the lapse forecast shows its count and every policy number', async () => {
    show(dashboard({ lapseForecast: { count: 2, policies: ['PF-MO-2026-000002', 'PF-HO-2026-000004'] } }));

    expect(await screen.findByTestId('forecast-count')).toHaveTextContent('2');
    expect(screen.getByTestId('forecast-PF-MO-2026-000002')).toHaveTextContent('PF-MO-2026-000002');
    expect(screen.getByTestId('forecast-PF-HO-2026-000004')).toHaveTextContent('PF-HO-2026-000004');
  });

  it('AC-11.4: an empty forecast shows count 0 and an empty state', async () => {
    show(dashboard({ lapseForecast: { count: 0, policies: [] } }));

    expect(await screen.findByTestId('forecast-count')).toHaveTextContent('0');
    expect(screen.getByTestId('forecast-empty')).toBeInTheDocument();
  });

  it('AC-11.5: a 403 shows the fixed not-authorised message and none of the dashboard', async () => {
    const fetchMock = installFetch(() => problem(403, { detail: 'Server text for customer1' }));
    renderWithSession(<AdminDashboardPage />, CUSTOMER);

    const error = await screen.findByTestId('dashboard-error');
    expect(error).toHaveTextContent('You are not authorised to view the portfolio dashboard.');
    expect(error).not.toHaveTextContent('Server text');
    expect(screen.queryByTestId('kpi-active')).not.toBeInTheDocument();
    expect(screen.queryByTestId('kpi-premium')).not.toBeInTheDocument();
    expect(header(calls(fetchMock)[0], 'Authorization')).toBe(basic(CUSTOMER));
  });
});
