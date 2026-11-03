import { useEffect, useState } from 'react';
import { ApiError, getPolicy } from '../api';
import type {
  Endorsement,
  Nominee,
  PolicyAddress,
  PolicyDetail,
  PolicyStatus,
  PolicyTransition,
  PolicyVersion,
  Refund,
} from '../api';
import { useSession } from '../auth/SessionContext';
import { CancelPanel } from '../components/CancelPanel';
import { RenewPanel } from '../components/RenewPanel';
import { StatusBadge } from '../components/StatusBadge';
import { CANCELLED_NOTICE, genericMessage, POLICY_NOT_FOUND_MESSAGE, RENEWAL_PAID_NOTICE } from '../messages';

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string; notFound: boolean }
  | { state: 'loaded'; detail: PolicyDetail };

type TabName = 'versions' | 'endorsements' | 'status' | 'refunds';

const TABS: readonly { name: TabName; label: string }[] = [
  { name: 'versions', label: 'Versions' },
  { name: 'endorsements', label: 'Endorsements' },
  { name: 'status', label: 'Status' },
  { name: 'refunds', label: 'Refunds' },
];

/** P-13: only these statuses can be endorsed, renewed or cancelled. The server still decides (409). */
const IN_FORCE: readonly PolicyStatus[] = ['ACTIVE', 'ENDORSED', 'RENEWED'];

const NO_VALUE = '—';

function formatAddress(address: PolicyAddress): string {
  return `${address.line1}, ${address.city}, ${address.postcode}`;
}

function formatNominee(nominee: Nominee): string {
  return `${nominee.name} (${nominee.relationship}) ${nominee.sharePercent}%`;
}

/** C3: one policy with its versions, endorsements, status history and refunds. Values are rendered as received. */
export function PolicyDetailPage({ policyNumber }: { policyNumber: string }) {
  const { credentials } = useSession();
  const [load, setLoad] = useState<Load>({ state: 'loading' });
  const [tab, setTab] = useState<TabName>('versions');
  const [notice, setNotice] = useState<string | null>(null);
  /** Bumped after a successful pay or cancel: the detail is fetched again and swapped in without a loading flash. */
  const [reloads, setReloads] = useState(0);

  useEffect(() => {
    let active = true;
    setLoad((current) => (reloads > 0 && current.state === 'loaded' ? current : { state: 'loading' }));
    getPolicy(credentials, policyNumber)
      .then((detail) => {
        if (active) setLoad({ state: 'loaded', detail });
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 404) {
          setLoad({ state: 'error', message: POLICY_NOT_FOUND_MESSAGE, notFound: true });
        } else {
          setLoad({ state: 'error', message: genericMessage(error, 'The policy could not be loaded.'), notFound: false });
        }
      });
    return () => {
      active = false;
    };
  }, [policyNumber, credentials, reloads]);

  function onChanged(message: string) {
    setNotice(message);
    setReloads((count) => count + 1);
  }

  return (
    <main className="page">
      <h1>Policy</h1>
      <a className="back-link" data-testid="policy-back-link" href="#/policies">
        Back to my policies
      </a>

      {load.state === 'loading' ? (
        <p className="muted" data-testid="policy-loading">
          Loading...
        </p>
      ) : null}

      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid={load.notFound ? 'policy-not-found' : 'policy-error'}>
          {load.message}
        </p>
      ) : null}

      {load.state === 'loaded' && notice ? (
        <p className="banner banner-ok" role="status" data-testid="policy-notice">
          {notice}
        </p>
      ) : null}

      {load.state === 'loaded' ? <Loaded detail={load.detail} tab={tab} onTab={setTab} onChanged={onChanged} /> : null}
    </main>
  );
}

function Loaded({
  detail,
  tab,
  onTab,
  onChanged,
}: {
  detail: PolicyDetail;
  tab: TabName;
  onTab: (tab: TabName) => void;
  onChanged: (message: string) => void;
}) {
  const { policy } = detail;
  const inForce = IN_FORCE.includes(policy.status);
  return (
    <>
      <section className="panel" aria-labelledby="policy-heading" data-testid="policy-header">
        <div className="card-head">
          <h2 id="policy-heading" className="mono card-title" data-testid="policy-number">
            {policy.policyNumber}
          </h2>
          <StatusBadge status={policy.status} testId="policy-status-badge" />
        </div>
        <dl className="summary">
          <div>
            <dt>Product</dt>
            <dd data-testid="policy-product">{policy.productCode}</dd>
          </div>
          <div>
            <dt>Effective date</dt>
            <dd data-testid="policy-effective-date">{policy.effectiveDate}</dd>
          </div>
          <div>
            <dt>Expiry date</dt>
            <dd data-testid="policy-expiry-date">{policy.expiryDate}</dd>
          </div>
          <div>
            <dt>Sum insured</dt>
            <dd data-testid="policy-sum-insured">{policy.sumInsured}</dd>
          </div>
          <div>
            <dt>Annual premium</dt>
            <dd data-testid="policy-premium">{policy.annualPremium}</dd>
          </div>
          <div>
            <dt>Next premium due</dt>
            <dd data-testid="policy-next-due">{policy.nextPremiumDueDate}</dd>
          </div>
        </dl>
        {inForce ? (
          <a
            className="button"
            data-testid="policy-endorse-link"
            href={`#/policies/${encodeURIComponent(policy.policyNumber)}/endorse`}
          >
            Endorse
          </a>
        ) : null}
      </section>

      {inForce && detail.pendingRenewal ? (
        <RenewPanel
          policyNumber={policy.policyNumber}
          pending={detail.pendingRenewal}
          onPaid={() => onChanged(RENEWAL_PAID_NOTICE)}
        />
      ) : null}

      {inForce ? <CancelPanel policyNumber={policy.policyNumber} onCancelled={() => onChanged(CANCELLED_NOTICE)} /> : null}

      <div className="tabs" role="tablist" aria-label="Policy history" data-testid="policy-tabs">
        {TABS.map((item) => (
          <button
            key={item.name}
            type="button"
            role="tab"
            id={`tab-${item.name}`}
            className="tab"
            data-testid={`tab-${item.name}`}
            aria-selected={tab === item.name}
            aria-controls={`tabpanel-${item.name}`}
            onClick={() => onTab(item.name)}
          >
            {item.label}
          </button>
        ))}
      </div>

      <section className="panel" role="tabpanel" id={`tabpanel-${tab}`} aria-labelledby={`tab-${tab}`} data-testid={`tabpanel-${tab}`}>
        {tab === 'versions' ? <Versions versions={detail.versions} /> : null}
        {tab === 'endorsements' ? <Endorsements endorsements={detail.endorsements} /> : null}
        {tab === 'status' ? <Transitions transitions={detail.transitions} /> : null}
        {tab === 'refunds' ? <Refunds refunds={detail.refunds} /> : null}
      </section>
    </>
  );
}

function Versions({ versions }: { versions: PolicyVersion[] }) {
  return (
    <ul className="rows" data-testid="versions-list">
      {versions.map((version) => {
        const id = `version-${version.versionNo}`;
        return (
          <li key={version.versionNo} className="row" data-testid={`version-row-${version.versionNo}`}>
            <h3>Version {version.versionNo}</h3>
            <dl className="summary">
              <div>
                <dt>Effective date</dt>
                <dd data-testid={`${id}-effective-date`}>{version.effectiveDate}</dd>
              </div>
              <div>
                <dt>Sum insured</dt>
                <dd data-testid={`${id}-sum-insured`}>{version.sumInsured}</dd>
              </div>
              <div>
                <dt>Annual premium</dt>
                <dd data-testid={`${id}-premium`}>{version.annualPremium}</dd>
              </div>
              <div>
                <dt>Address</dt>
                <dd data-testid={`${id}-address`}>{formatAddress(version.address)}</dd>
              </div>
              <div>
                <dt>Nominees</dt>
                <dd data-testid={`${id}-nominees`}>
                  {version.nominees.length === 0 ? (
                    'None'
                  ) : (
                    <ul className="plain-list">
                      {version.nominees.map((nominee, index) => (
                        <li key={`${nominee.name}-${index}`} data-testid={`${id}-nominee-${index}`}>
                          {formatNominee(nominee)}
                        </li>
                      ))}
                    </ul>
                  )}
                </dd>
              </div>
            </dl>
          </li>
        );
      })}
    </ul>
  );
}

function Endorsements({ endorsements }: { endorsements: Endorsement[] }) {
  if (endorsements.length === 0) {
    return (
      <p className="muted" data-testid="endorsements-empty">
        No endorsements yet.
      </p>
    );
  }
  return (
    <ul className="rows" data-testid="endorsements-list">
      {endorsements.map((endorsement) => {
        const id = `endorsement-${endorsement.sequenceNo}`;
        return (
          <li key={endorsement.sequenceNo} className="row" data-testid={`endorsement-row-${endorsement.sequenceNo}`}>
            <h3>Endorsement {endorsement.sequenceNo}</h3>
            <dl className="summary">
              <div>
                <dt>Type</dt>
                <dd data-testid={`${id}-type`}>{endorsement.type}</dd>
              </div>
              <div>
                <dt>Versions</dt>
                <dd data-testid={`${id}-versions`}>{`v${endorsement.beforeVersionNo} → v${endorsement.afterVersionNo}`}</dd>
              </div>
              <div>
                <dt>Effective date</dt>
                <dd data-testid={`${id}-effective-date`}>{endorsement.effectiveDate}</dd>
              </div>
              <div>
                <dt>New annual premium</dt>
                <dd data-testid={`${id}-new-premium`}>{endorsement.newAnnualPremium}</dd>
              </div>
              <div>
                <dt>Adjustment</dt>
                <dd data-testid={`${id}-adjustment`}>{endorsement.adjustment}</dd>
              </div>
            </dl>
          </li>
        );
      })}
    </ul>
  );
}

function Transitions({ transitions }: { transitions: PolicyTransition[] }) {
  return (
    <ul className="rows" data-testid="transitions-list">
      {transitions.map((transition, index) => (
        <li key={`${transition.at}-${index}`} className="row" data-testid={`transition-row-${index}`}>
          <dl className="summary">
            <div>
              <dt>From</dt>
              <dd data-testid={`transition-${index}-from`}>{transition.fromStatus ?? NO_VALUE}</dd>
            </div>
            <div>
              <dt>To</dt>
              <dd data-testid={`transition-${index}-to`}>{transition.toStatus}</dd>
            </div>
            <div>
              <dt>Reason</dt>
              <dd data-testid={`transition-${index}-reason`}>{transition.reason}</dd>
            </div>
            <div>
              <dt>At</dt>
              <dd data-testid={`transition-${index}-at`}>{transition.at}</dd>
            </div>
          </dl>
        </li>
      ))}
    </ul>
  );
}

function Refunds({ refunds }: { refunds: Refund[] }) {
  if (refunds.length === 0) {
    return (
      <p className="muted" data-testid="refunds-empty">
        No refunds.
      </p>
    );
  }
  return (
    <ul className="rows" data-testid="refunds-list">
      {refunds.map((refund) => {
        const id = `refund-${refund.termNo}`;
        return (
          <li key={refund.termNo} className="row" data-testid={`refund-row-${refund.termNo}`}>
            <h3>Term {refund.termNo} refund</h3>
            <dl className="summary">
              <div>
                <dt>Cancellation date</dt>
                <dd data-testid={`${id}-date`}>{refund.cancellationDate}</dd>
              </div>
              <div>
                <dt>Refund amount</dt>
                <dd data-testid={`${id}-amount`}>{refund.amount}</dd>
              </div>
              <div>
                <dt>Premium basis</dt>
                <dd data-testid={`${id}-premium`}>{refund.premium}</dd>
              </div>
              <div>
                <dt>Fee</dt>
                <dd data-testid={`${id}-fee`}>{refund.fee}</dd>
              </div>
              <div>
                <dt>Unexpired / term days</dt>
                <dd data-testid={`${id}-days`}>{`${refund.unexpiredDays} / ${refund.termDays}`}</dd>
              </div>
              <div>
                <dt>Cooling-off</dt>
                <dd data-testid={`${id}-cooling-off`}>{String(refund.coolingOff)}</dd>
              </div>
              <div>
                <dt>Formula</dt>
                <dd className="mono" data-testid={`${id}-formula`}>
                  {refund.formula}
                </dd>
              </div>
            </dl>
          </li>
        );
      })}
    </ul>
  );
}
