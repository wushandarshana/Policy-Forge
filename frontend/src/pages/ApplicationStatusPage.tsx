import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, issuePolicy, listMyApplications } from '../api';
import type { ApplicationSummary, IssueRequest } from '../api';
import { useSession } from '../auth/SessionContext';
import { Field } from '../components/Field';
import { ReasonList } from '../components/ReasonList';
import { StatusBadge } from '../components/StatusBadge';
import { APPLICATION_NOT_FOUND_MESSAGE, genericMessage, INVALID_APPLICATION_STATE_MESSAGE } from '../messages';

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'loaded'; application: ApplicationSummary | null };

/**
 * Shows one application. app_spec has no read-one endpoint, so this reads the caller's own list (C2) and picks the
 * application by ID (P-6). Nothing here carries KYC.
 */
export function ApplicationStatusPage({
  applicationId,
  onIssued,
}: {
  applicationId: string;
  /** Called with the new policy number after a 201. The shell uses it to open the policy detail page. */
  onIssued?: (policyNumber: string) => void;
}) {
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

      {load.state === 'loaded' && load.application ? (
        <ApplicationDetails
          application={load.application}
          onIssued={(policyNumber) => {
            setLoad({ state: 'loaded', application: { ...load.application!, status: 'ISSUED' } });
            onIssued?.(policyNumber);
          }}
        />
      ) : null}
    </main>
  );
}

function ApplicationDetails({
  application,
  onIssued,
}: {
  application: ApplicationSummary;
  onIssued: (policyNumber: string) => void;
}) {
  return (
    <>
      <ApplicationSummaryPanel application={application} />
      {application.status === 'APPROVED' ? (
        <IssuePanel applicationId={application.applicationId} onIssued={onIssued} />
      ) : null}
      {application.status === 'ISSUED' ? (
        <section className="panel" aria-labelledby="issued-heading" data-testid="application-issued">
          <h2 id="issued-heading">Issued</h2>
          <p className="muted">A policy has been issued for this application.</p>
          <a className="button" data-testid="application-policies-link" href="#/policies">
            Go to My Policies
          </a>
        </section>
      ) : null}
    </>
  );
}

function ApplicationSummaryPanel({ application }: { application: ApplicationSummary }) {
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

/**
 * C1: issue an APPROVED application. The body is exactly `{}` when the date is blank, or `{ effectiveDate }`. Date
 * range checks belong to the server, which answers 400 at `effectiveDate`.
 */
function IssuePanel({ applicationId, onIssued }: { applicationId: string; onIssued: (policyNumber: string) => void }) {
  const { credentials } = useSession();
  const [effectiveDate, setEffectiveDate] = useState('');
  const [dateError, setDateError] = useState<string | null>(null);
  const [banner, setBanner] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function showFailure(error: unknown) {
    setDateError(null);
    setBanner(null);
    if (error instanceof ApiError) {
      const dateEntries = error.fieldErrors.filter((entry) => entry.field === 'effectiveDate');
      if (error.status === 400 && dateEntries.length > 0) {
        setDateError(dateEntries.map((entry) => entry.message).join('; '));
        return;
      }
      if (error.status === 409) {
        setBanner(INVALID_APPLICATION_STATE_MESSAGE);
        return;
      }
    }
    setBanner(genericMessage(error, 'The policy could not be issued.'));
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setDateError(null);
    setBanner(null);
    const trimmed = effectiveDate.trim();
    const request: IssueRequest = trimmed === '' ? {} : { effectiveDate: trimmed };
    setSubmitting(true);
    try {
      const policy = await issuePolicy(credentials, applicationId, request);
      onIssued(policy.policyNumber);
    } catch (error) {
      showFailure(error);
      setSubmitting(false);
    }
  }

  return (
    <form className="panel" onSubmit={onSubmit} noValidate aria-labelledby="issue-heading" data-testid="issue-panel">
      <h2 id="issue-heading">Issue policy</h2>
      <p className="muted">This application is approved. Choose when cover starts, or leave the date blank to start today.</p>
      <Field
        id="issue-effective-date"
        label="Effective date (optional)"
        fieldTestId="issue-effective-date-field"
        errorTestId="issue-effective-date-error"
        error={dateError ?? undefined}
      >
        <input
          id="issue-effective-date"
          data-testid="issue-effective-date"
          type="date"
          value={effectiveDate}
          aria-invalid={dateError ? true : undefined}
          aria-describedby={dateError ? 'issue-effective-date-error' : undefined}
          onChange={(event) => {
            setEffectiveDate(event.target.value);
            setDateError(null);
          }}
        />
      </Field>
      {banner ? (
        <p className="banner banner-error" role="alert" data-testid="issue-error">
          {banner}
        </p>
      ) : null}
      <button type="submit" className="button" data-testid="issue-submit" disabled={submitting}>
        {submitting ? 'Issuing...' : 'Issue policy'}
      </button>
    </form>
  );
}
