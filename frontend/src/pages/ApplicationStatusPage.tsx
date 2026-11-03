import { useEffect, useState } from 'react';
import { listMyApplications } from '../api';
import type { ApplicationSummary } from '../api';
import { useSession } from '../auth/SessionContext';
import { ReasonList } from '../components/ReasonList';
import { StatusBadge } from '../components/StatusBadge';
import { APPLICATION_NOT_FOUND_MESSAGE, genericMessage } from '../messages';

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'loaded'; application: ApplicationSummary | null };

/**
 * Shows one application. app_spec has no read-one endpoint, so this reads the caller's own list (C2) and picks the
 * application by ID (P-6). Nothing here carries KYC.
 */
export function ApplicationStatusPage({ applicationId }: { applicationId: string; onIssued?: (policyNumber: string) => void }) {
  const { credentials } = useSession();
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  useEffect(() => {
    let active = true;
    setLoad({ state: 'loading' });
    listMyApplications(credentials)
      .then((list) => {
        if (!active) return;
        setLoad({ state: 'loaded', application: list.find((item) => item.applicationId === applicationId) ?? null });
      })
      .catch((error: unknown) => {
        if (active) setLoad({ state: 'error', message: genericMessage(error, 'The application could not be loaded.') });
      });
    return () => {
      active = false;
    };
  }, [applicationId, credentials]);

  return (
    <main className="page">
      <h1>Application status</h1>

      {load.state === 'loading' ? (
        <p className="muted" data-testid="application-loading">
          Loading...
        </p>
      ) : null}

      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid="application-error">
          {load.message}
        </p>
      ) : null}

      {load.state === 'loaded' && !load.application ? (
        <p className="banner banner-error" role="alert" data-testid="application-not-found">
          {APPLICATION_NOT_FOUND_MESSAGE}
        </p>
      ) : null}

      {load.state === 'loaded' && load.application ? <ApplicationDetails application={load.application} /> : null}
    </main>
  );
}

function ApplicationDetails({ application }: { application: ApplicationSummary }) {
  return (
    <section className="panel" aria-labelledby="application-heading" data-testid="application-details">
      <h2 id="application-heading">
        <StatusBadge status={application.status} />
      </h2>
      <dl className="summary">
        <div>
          <dt>Application ID</dt>
          <dd className="mono" data-testid="application-id">
            {application.applicationId}
          </dd>
        </div>
        <div>
          <dt>Product</dt>
          <dd data-testid="application-product">{application.productCode}</dd>
        </div>
        <div>
          <dt>Decision</dt>
          <dd data-testid="application-decision">{application.decision}</dd>
        </div>
        <div>
          <dt>Rule version</dt>
          <dd data-testid="application-rule-version">{application.ruleVersion}</dd>
        </div>
        <div>
          <dt>Submitted</dt>
          <dd data-testid="application-submitted-at">{application.submittedAt}</dd>
        </div>
        <div>
          <dt>Quote ID</dt>
          <dd className="mono" data-testid="application-quote-id">
            {application.quoteId}
          </dd>
        </div>
      </dl>
      <h3>Reasons</h3>
      <ReasonList reasons={application.reasons} />
    </section>
  );
}
