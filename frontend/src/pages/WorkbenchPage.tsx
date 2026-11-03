import { useCallback, useEffect, useState } from 'react';
import { ApiError, getUnderwritingQueue } from '../api';
import type { DecisionResult, QueueItem, RiskInputValue } from '../api';
import { useSession } from '../auth/SessionContext';
import { CaseActionForm } from '../components/CaseActionForm';
import { ReasonList } from '../components/ReasonList';
import { StatusBadge } from '../components/StatusBadge';
import { genericMessage } from '../messages';

/** Renders one declared risk input exactly as the API sent it. Nothing is parsed or computed. */
function showValue(value: RiskInputValue): string {
  if (Array.isArray(value)) return value.length === 0 ? '(none)' : value.join(', ');
  return String(value);
}

type Load = { state: 'loading' } | { state: 'error'; message: string } | { state: 'loaded'; items: QueueItem[] };

export function WorkbenchPage() {
  const { credentials } = useSession();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  /** null until known. true only when the includeDeclined=true request returned 200; false after a 403 (U-11). */
  const [isAdmin, setIsAdmin] = useState<boolean | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [result, setResult] = useState<DecisionResult | null>(null);

  /**
   * Loads the queue. The SPA learns whether the user is an ADMIN from the includeDeclined=true request: 200 means
   * ADMIN, 403 means not, and after a 403 it falls back to the plain queue and never shows Override.
   */
  const refresh = useCallback(
    async (adminKnown: boolean | null, isActive: () => boolean = () => true) => {
      try {
        let items: QueueItem[] | null = null;
        let admin = adminKnown;
        if (admin !== false) {
          try {
            items = await getUnderwritingQueue(credentials, true);
            admin = true;
          } catch (error) {
            if (!(error instanceof ApiError && error.status === 403)) throw error;
            admin = false;
          }
        }
        if (items === null) items = await getUnderwritingQueue(credentials, false);
        if (!isActive()) return;
        setIsAdmin(admin);
        setLoad({ state: 'loaded', items });
      } catch (error) {
        if (isActive()) setLoad({ state: 'error', message: genericMessage(error, 'The queue could not be loaded.') });
      }
    },
    [credentials],
  );

  useEffect(() => {
    let active = true;
    setLoad({ state: 'loading' });
    void refresh(null, () => active);
    return () => {
      active = false;
    };
  }, [refresh]);

  function onDone(done: DecisionResult) {
    setResult(done);
    setSelectedId(null);
    void refresh(isAdmin);
  }

  const items = load.state === 'loaded' ? load.items : [];
  const selected = items.find((item) => item.applicationId === selectedId) ?? null;

  return (
    <main className="page">
      <h1>Underwriter workbench</h1>

      <div className="toolbar">
        <button
          type="button"
          className="button"
          data-testid="queue-refresh"
          onClick={() => {
            setLoad({ state: 'loading' });
            void refresh(isAdmin);
          }}
        >
          Refresh
        </button>
      </div>

      {result ? (
        <p className="banner banner-ok" role="status" data-testid="decision-result" data-status={result.status}>
          Application <span className="mono">{result.applicationId}</span> is now {result.status}.
        </p>
      ) : null}

      {load.state === 'loading' ? (
        <p className="muted" data-testid="queue-loading">
          Loading...
        </p>
      ) : null}
      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid="queue-error">
          {load.message}
        </p>
      ) : null}
      {load.state === 'loaded' && items.length === 0 ? (
        <p className="muted" data-testid="queue-empty">
          No applications are waiting.
        </p>
      ) : null}

      {items.length > 0 ? (
        <table className="queue-table" data-testid="queue-table">
          <thead>
            <tr>
              <th scope="col">Application</th>
              <th scope="col">Product</th>
              <th scope="col">Status</th>
              <th scope="col">Submitted</th>
              <th scope="col">Reasons</th>
              <th scope="col">
                <span className="visually-hidden">Action</span>
              </th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr
                key={item.applicationId}
                data-testid={`queue-row-${item.applicationId}`}
                data-status={item.status}
                aria-selected={item.applicationId === selectedId}
              >
                <td data-label="Application" className="mono">
                  {item.applicationId}
                </td>
                <td data-label="Product">{item.productCode}</td>
                <td data-label="Status">
                  <StatusBadge status={item.status} testId={`row-status-${item.applicationId}`} />
                </td>
                <td data-label="Submitted">{item.submittedAt}</td>
                <td data-label="Reasons">{item.reasonCodes.join(', ')}</td>
                <td data-label="">
                  <button
                    type="button"
                    className="button button-secondary"
                    data-testid={`select-${item.applicationId}`}
                    onClick={() => {
                      setSelectedId(item.applicationId);
                      setResult(null);
                    }}
                  >
                    Review
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : null}

      {selected ? (
        <section className="panel" aria-labelledby="case-heading" data-testid="case-detail" key={selected.applicationId}>
          <h2 id="case-heading">
            Case <span className="mono">{selected.applicationId}</span>
          </h2>
          <dl className="summary">
            <div>
              <dt>Status</dt>
              <dd>
                <StatusBadge status={selected.status} testId="case-status" />
              </dd>
            </div>
            <div>
              <dt>Product</dt>
              <dd data-testid="case-product">{selected.productCode}</dd>
            </div>
            <div>
              <dt>Decision</dt>
              <dd data-testid="case-decision">{selected.decision}</dd>
            </div>
            <div>
              <dt>Rule version</dt>
              <dd data-testid="case-rule-version">{selected.ruleVersion}</dd>
            </div>
            <div>
              <dt>Full name</dt>
              <dd data-testid="case-fullName">{selected.kyc.fullName}</dd>
            </div>
            <div>
              <dt>Aadhaar</dt>
              <dd className="mono" data-testid="case-aadhaar">
                {selected.kyc.aadhaar}
              </dd>
            </div>
            <div>
              <dt>PAN</dt>
              <dd className="mono" data-testid="case-pan">
                {selected.kyc.pan}
              </dd>
            </div>
          </dl>

          <h3>Risk inputs</h3>
          <dl className="summary" data-testid="case-risk-inputs">
            {Object.entries(selected.riskInputs).map(([name, value]) => (
              <div key={name}>
                <dt>{name}</dt>
                <dd data-testid={`case-risk-${name}`}>{showValue(value)}</dd>
              </div>
            ))}
          </dl>

          <h3>Reasons</h3>
          <ReasonList reasons={selected.reasons} />

          {selected.status === 'REFERRED' ? (
            <CaseActionForm kind="decision" applicationId={selected.applicationId} onDone={onDone} />
          ) : null}
          {selected.status === 'DECLINED' && isAdmin === true ? (
            <CaseActionForm kind="override" applicationId={selected.applicationId} onDone={onDone} />
          ) : null}
        </section>
      ) : null}
    </main>
  );
}
