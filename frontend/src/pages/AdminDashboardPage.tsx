import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, getDashboard } from '../api';
import type { Dashboard, ProductCode } from '../api';
import { useSession } from '../auth/SessionContext';
import { Field } from '../components/Field';
import { DASHBOARD_FORBIDDEN_MESSAGE, genericMessage } from '../messages';

/** The three products, in the order the table shows them. The server always sends all three (C6). */
const PRODUCTS: readonly ProductCode[] = ['TERM_LIFE', 'MOTOR', 'HOUSEHOLD'];

const NO_OFFER = 'No offer yet';

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'loaded'; data: Dashboard };

/**
 * C6: the admin portfolio dashboard. Every figure is the server's string or integer, shown as received. The only
 * arithmetic is the "Active policies" tile, which adds three integer counts (counts are not money).
 */
export function AdminDashboardPage() {
  const { credentials } = useSession();
  const [asOf, setAsOf] = useState('');
  const [asOfError, setAsOfError] = useState<string | null>(null);
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  /** Bumped on every request, so an older response never replaces a newer one. */
  const generation = useRef(0);

  const fetchDashboard = useCallback(
    async (date: string) => {
      generation.current += 1;
      const mine = generation.current;
      setAsOfError(null);
      try {
        const data = await getDashboard(credentials, date.trim() === '' ? undefined : date.trim());
        if (generation.current === mine) setLoad({ state: 'loaded', data });
      } catch (error) {
        if (generation.current !== mine) return;
        if (error instanceof ApiError && error.status === 400) {
          const entry = error.fieldErrors.find((candidate) => candidate.field === 'asOf');
          if (entry) {
            // A bad date leaves what is already on screen in place.
            setAsOfError(entry.message);
            return;
          }
        }
        if (error instanceof ApiError && error.status === 403) {
          setLoad({ state: 'error', message: DASHBOARD_FORBIDDEN_MESSAGE });
        } else {
          setLoad({ state: 'error', message: genericMessage(error, 'The dashboard could not be loaded.') });
        }
      }
    },
    [credentials],
  );

  useEffect(() => {
    setLoad({ state: 'loading' });
    void fetchDashboard('');
    return () => {
      generation.current += 1;
    };
    // The first load is for today. A later credentials change reloads it the same way.
  }, [fetchDashboard]);

  return (
    <main className="page">
      <h1>Admin dashboard</h1>

      <form
        className="panel dashboard-controls"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          void fetchDashboard(asOf);
        }}
      >
        <Field
          id="dashboard-input-asof"
          label="As of (optional)"
          fieldTestId="field-dashboard-asof"
          errorTestId="dashboard-asof-error"
          error={asOfError ?? undefined}
        >
          <input
            id="dashboard-input-asof"
            type="date"
            data-testid="dashboard-asof"
            value={asOf}
            aria-invalid={asOfError ? true : undefined}
            aria-describedby={asOfError ? 'dashboard-input-asof-error' : undefined}
            onChange={(event) => {
              setAsOf(event.target.value);
              setAsOfError(null);
            }}
          />
        </Field>
        <button type="submit" className="button" data-testid="dashboard-load">
          Load dashboard
        </button>
      </form>

      {load.state === 'loading' ? (
        <p className="muted" data-testid="dashboard-loading">
          Loading...
        </p>
      ) : null}

      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid="dashboard-error">
          {load.message}
        </p>
      ) : null}

      {load.state === 'loaded' ? <Loaded data={load.data} /> : null}
    </main>
  );
}

function Loaded({ data }: { data: Dashboard }) {
  const active = PRODUCTS.reduce((sum, code) => sum + data.activePoliciesByProduct[code], 0);
  return (
    <>
      <p className="muted" data-testid="dashboard-as-of">
        As of {data.asOf}
      </p>

      <div className="kpi-grid" data-testid="kpi-tiles">
        <section className="kpi-tile" data-testid="kpi-tile-active" aria-labelledby="kpi-active-label">
          <h2 id="kpi-active-label" className="kpi-label">
            Active policies
          </h2>
          <p className="kpi-value" data-testid="kpi-active">
            {active}
          </p>
        </section>
        <section className="kpi-tile" data-testid="kpi-tile-premium" aria-labelledby="kpi-premium-label">
          <h2 id="kpi-premium-label" className="kpi-label">
            Premium collected
          </h2>
          <p className="kpi-value" data-testid="kpi-premium">
            {data.premiumCollected.total}
          </p>
        </section>
      </div>

      <section className="panel" aria-labelledby="products-heading">
        <h2 id="products-heading">By product</h2>
        <table className="queue-table" data-testid="product-table">
          <thead>
            <tr>
              <th scope="col">Product</th>
              <th scope="col">Active policies</th>
              <th scope="col">Premium collected</th>
            </tr>
          </thead>
          <tbody>
            {PRODUCTS.map((code) => (
              <tr key={code} data-testid={`product-row-${code}`}>
                <td data-label="Product">{code}</td>
                <td data-label="Active policies" data-testid={`product-${code}-active`}>
                  {data.activePoliciesByProduct[code]}
                </td>
                <td data-label="Premium collected" data-testid={`product-${code}-premium`}>
                  {data.premiumCollected[code]}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="panel" aria-labelledby="pipeline-heading">
        <h2 id="pipeline-heading">Renewal pipeline</h2>
        {data.renewalPipeline.length === 0 ? (
          <p className="muted" data-testid="pipeline-empty">
            No policies are due for renewal in the next 60 days.
          </p>
        ) : (
          <table className="queue-table" data-testid="pipeline-table">
            <thead>
              <tr>
                <th scope="col">Policy</th>
                <th scope="col">Term end</th>
                <th scope="col">Renewal premium</th>
                <th scope="col">Payment</th>
              </tr>
            </thead>
            <tbody>
              {data.renewalPipeline.map((entry) => (
                <tr key={entry.policyNumber} data-testid={`pipeline-row-${entry.policyNumber}`}>
                  <td data-label="Policy" className="mono">
                    {entry.policyNumber}
                  </td>
                  <td data-label="Term end">{entry.termEnd}</td>
                  <td data-label="Renewal premium" data-testid={`pipeline-${entry.policyNumber}-premium`}>
                    {entry.renewalPremium ?? NO_OFFER}
                  </td>
                  <td data-label="Payment" data-testid={`pipeline-${entry.policyNumber}-paid`}>
                    {entry.paid ? 'Paid' : 'Unpaid'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="panel" aria-labelledby="forecast-heading">
        <h2 id="forecast-heading">Lapse forecast</h2>
        <p className="muted">
          In grace now, and will lapse if unpaid: <span data-testid="forecast-count">{data.lapseForecast.count}</span>
        </p>
        {data.lapseForecast.policies.length === 0 ? (
          <p className="muted" data-testid="forecast-empty">
            No policies are in their grace period.
          </p>
        ) : (
          <ul className="plain-list" data-testid="forecast-list">
            {data.lapseForecast.policies.map((policyNumber) => (
              <li key={policyNumber} className="mono" data-testid={`forecast-${policyNumber}`}>
                {policyNumber}
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  );
}
