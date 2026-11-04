import { useState } from 'react';
import { ApiError, payRenewal } from '../api';
import type { PendingRenewal } from '../api';
import { useSession } from '../auth/SessionContext';
import { genericMessage, POLICY_NOT_FOUND_MESSAGE, RENEWAL_CODE_MESSAGES } from '../messages';

/** A fixed message for a failed pay. Nothing the server sent is echoed. */
function failureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    const code = error.code;
    if (code && RENEWAL_CODE_MESSAGES[code]) return RENEWAL_CODE_MESSAGES[code];
    if (error.status === 404) return POLICY_NOT_FOUND_MESSAGE;
  }
  return genericMessage(error, 'The renewal could not be paid.');
}

interface Props {
  policyNumber: string;
  pending: PendingRenewal;
  /** Called after a 200 so the page reloads the detail. */
  onPaid: () => void;
}

/** The pending renewal offer (C5 `pendingRenewal`), shown verbatim, with Pay. The body posted is always `{}`. */
export function RenewPanel({ policyNumber, pending, onPaid }: Props) {
  const { credentials } = useSession();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onPay() {
    setError(null);
    setBusy(true);
    try {
      await payRenewal(credentials, policyNumber);
      onPaid();
    } catch (failure) {
      setError(failureMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="panel" aria-labelledby="renew-heading" data-testid="renew-panel">
      <h2 id="renew-heading">Renew</h2>
      <dl className="summary">
        <div>
          <dt>Renewal starts</dt>
          <dd data-testid="renew-start">{pending.startDate}</dd>
        </div>
        <div>
          <dt>Renewal ends</dt>
          <dd data-testid="renew-end">{pending.endDate}</dd>
        </div>
        <div>
          <dt>Renewal premium</dt>
          <dd data-testid="renew-premium">{pending.premium}</dd>
        </div>
        <div>
          <dt>Rule version</dt>
          <dd data-testid="renew-rule-version">{pending.ruleVersion}</dd>
        </div>
        <div>
          <dt>Payable until</dt>
          <dd data-testid="renew-payable-until">{pending.payableUntil}</dd>
        </div>
      </dl>
      {error ? (
        <p className="banner banner-error" role="alert" data-testid="renew-error">
          {error}
        </p>
      ) : null}
      <button type="button" className="button" data-testid="renew-pay" disabled={busy} onClick={() => void onPay()}>
        {busy ? 'Paying...' : 'Pay renewal'}
      </button>
    </section>
  );
}
