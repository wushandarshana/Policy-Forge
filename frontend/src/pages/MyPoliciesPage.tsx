import { useEffect, useState } from 'react';
import { listMyPolicies } from '../api';
import type { PolicySummary } from '../api';
import { useSession } from '../auth/SessionContext';
import { StatusBadge } from '../components/StatusBadge';
import { genericMessage } from '../messages';

type Load = { state: 'loading' } | { state: 'error'; message: string } | { state: 'loaded'; policies: PolicySummary[] };

/** C2: the caller's own policies, in the order the API returns them (newest first). Every value is shown verbatim. */
export function MyPoliciesPage() {
  const { credentials } = useSession();
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  useEffect(() => {
    let active = true;
    setLoad({ state: 'loading' });
    listMyPolicies(credentials)
      .then((policies) => {
        if (active) setLoad({ state: 'loaded', policies });
      })
      .catch((error: unknown) => {
        if (active) setLoad({ state: 'error', message: genericMessage(error, 'Your policies could not be loaded.') });
      });
    return () => {
      active = false;
    };
  }, [credentials]);

  return (
    <main className="page">
      <h1>My policies</h1>

      {load.state === 'loading' ? (
        <p className="muted" data-testid="policies-loading">
          Loading...
        </p>
      ) : null}

      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid="policies-error">
          {load.message}
        </p>
      ) : null}

      {load.state === 'loaded' && load.policies.length === 0 ? (
        <p className="panel muted" data-testid="policies-empty">
          You have no policies yet. Issue one from an approved application.
        </p>
      ) : null}

      {load.state === 'loaded' && load.policies.length > 0 ? (
        <ul className="cards" data-testid="policies-list">
          {load.policies.map((policy) => (
            <PolicyCard key={policy.policyNumber} policy={policy} />
          ))}
        </ul>
      ) : null}
    </main>
  );
}

function PolicyCard({ policy }: { policy: PolicySummary }) {
  const id = `policy-card-${policy.policyNumber}`;
  return (
    <li className="panel card" data-testid={id}>
      <div className="card-head">
        <h2 className="mono card-title">{policy.policyNumber}</h2>
        <StatusBadge status={policy.status} testId={`${id}-status`} />
      </div>
      <dl className="summary">
        <div>
          <dt>Product</dt>
          <dd data-testid={`${id}-product`}>{policy.productCode}</dd>
        </div>
        <div>
          <dt>Sum insured</dt>
          <dd data-testid={`${id}-sum-insured`}>{policy.sumInsured}</dd>
        </div>
        <div>
          <dt>Annual premium</dt>
          <dd data-testid={`${id}-premium`}>{policy.annualPremium}</dd>
        </div>
        <div>
          <dt>Next premium due</dt>
          <dd data-testid={`${id}-next-due`}>{policy.nextPremiumDueDate}</dd>
        </div>
        <div>
          <dt>Endorsements</dt>
          <dd data-testid={`${id}-endorsements`}>{policy.endorsementCount}</dd>
        </div>
      </dl>
      <a className="button" data-testid={`${id}-link`} href={`#/policies/${encodeURIComponent(policy.policyNumber)}`}>
        View policy
      </a>
    </li>
  );
}
