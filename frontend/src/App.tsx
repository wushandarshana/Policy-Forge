import { useEffect, useState } from 'react';
import { RequireSession } from './auth/RequireSession';
import { SessionProvider, useSession } from './auth/SessionContext';
import { AdminDashboardPage } from './pages/AdminDashboardPage';
import { ApplicationStatusPage } from './pages/ApplicationStatusPage';
import { ApplyPage } from './pages/ApplyPage';
import { EndorsePage } from './pages/EndorsePage';
import { MyPoliciesPage } from './pages/MyPoliciesPage';
import { PolicyDetailPage } from './pages/PolicyDetailPage';
import { QuotePage } from './pages/QuotePage';
import { WorkbenchPage } from './pages/WorkbenchPage';

type Route =
  | { name: 'quote' }
  | { name: 'apply'; quoteId: string | null }
  | { name: 'application'; applicationId: string }
  | { name: 'workbench' }
  | { name: 'dashboard' }
  | { name: 'policies' }
  | { name: 'policy'; policyNumber: string }
  | { name: 'endorse'; policyNumber: string };

/** Parses the location hash into a route (C8). Anything unknown is the public Quote page, the default route. */
export function parseRoute(hash: string): Route {
  const raw = hash.startsWith('#') ? hash.slice(1) : hash;
  const [path, query = ''] = raw.split('?', 2);
  if (path === '/apply') {
    const quoteId = new URLSearchParams(query).get('quoteId');
    return { name: 'apply', quoteId: quoteId && quoteId.trim() !== '' ? quoteId : null };
  }
  if (path === '/workbench') return { name: 'workbench' };
  if (path === '/admin/dashboard') return { name: 'dashboard' };
  if (path === '/policies') return { name: 'policies' };
  const endorse = /^\/policies\/([^/]+)\/endorse$/.exec(path);
  if (endorse) return { name: 'endorse', policyNumber: decodeURIComponent(endorse[1]) };
  const policy = /^\/policies\/([^/]+)$/.exec(path);
  if (policy) return { name: 'policy', policyNumber: decodeURIComponent(policy[1]) };
  const application = /^\/applications\/([^/]+)$/.exec(path);
  if (application) return { name: 'application', applicationId: decodeURIComponent(application[1]) };
  return { name: 'quote' };
}

function useHashRoute(): Route {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => setHash(window.location.hash);
    window.addEventListener('hashchange', onChange);
    // A hash change between the first render and this effect would otherwise be missed.
    onChange();
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return parseRoute(hash);
}

function NavBar({ route }: { route: Route }) {
  const { credentials, signOut } = useSession();
  return (
    <nav className="nav" aria-label="Main" data-testid="app-nav">
      <a href="#/" data-testid="nav-quote" aria-current={route.name === 'quote' ? 'page' : undefined}>
        Quote
      </a>
      <a href="#/workbench" data-testid="nav-workbench" aria-current={route.name === 'workbench' ? 'page' : undefined}>
        Workbench
      </a>
      <a href="#/admin/dashboard" data-testid="nav-dashboard" aria-current={route.name === 'dashboard' ? 'page' : undefined}>
        Dashboard
      </a>
      <a
        href="#/policies"
        data-testid="nav-policies"
        aria-current={route.name === 'policies' || route.name === 'policy' || route.name === 'endorse' ? 'page' : undefined}
      >
        My Policies
      </a>
      {credentials ? (
        <span className="nav-session">
          <span data-testid="nav-user">{credentials.username}</span>
          <button type="button" className="button button-secondary" data-testid="nav-signout" onClick={signOut}>
            Sign out
          </button>
        </span>
      ) : null}
    </nav>
  );
}

function Routes({ route }: { route: Route }) {
  switch (route.name) {
    case 'apply':
      if (route.quoteId === null) {
        return (
          <main className="page">
            <p className="banner banner-error" role="alert" data-testid="apply-missing-quote">
              Start from a quote: there is no quote ID in this link.
            </p>
          </main>
        );
      }
      return (
        <RequireSession>
          <ApplyPage
            key={route.quoteId}
            quoteId={route.quoteId}
            onSubmitted={(applicationId) => {
              window.location.hash = `#/applications/${applicationId}`;
            }}
          />
        </RequireSession>
      );
    case 'application':
      return (
        <RequireSession>
          <ApplicationStatusPage
            key={route.applicationId}
            applicationId={route.applicationId}
            onIssued={(policyNumber) => {
              window.location.hash = `#/policies/${encodeURIComponent(policyNumber)}`;
            }}
          />
        </RequireSession>
      );
    case 'policies':
      return (
        <RequireSession>
          <MyPoliciesPage />
        </RequireSession>
      );
    case 'policy':
      return (
        <RequireSession>
          <PolicyDetailPage key={route.policyNumber} policyNumber={route.policyNumber} />
        </RequireSession>
      );
    case 'endorse':
      return (
        <RequireSession>
          <EndorsePage
            key={route.policyNumber}
            policyNumber={route.policyNumber}
            onEndorsed={(policyNumber) => {
              window.location.hash = `#/policies/${encodeURIComponent(policyNumber)}`;
            }}
          />
        </RequireSession>
      );
    case 'workbench':
      return (
        <RequireSession>
          <WorkbenchPage />
        </RequireSession>
      );
    case 'dashboard':
      return (
        <RequireSession>
          <AdminDashboardPage />
        </RequireSession>
      );
    default:
      return <QuotePage />;
  }
}

/**
 * App shell: the in-memory session sits above the router so hash navigation keeps the user signed in (TASK-002
 * contract). The Quote page is the default route and stays public; the other routes sit behind the sign-in gate.
 */
export function App() {
  return (
    <SessionProvider>
      <Shell />
    </SessionProvider>
  );
}

function Shell() {
  const route = useHashRoute();
  return (
    <>
      <NavBar route={route} />
      <Routes route={route} />
    </>
  );
}
