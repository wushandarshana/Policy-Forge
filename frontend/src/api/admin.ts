import { requestProtected } from './http';
import type { Credentials, Dashboard } from './types';

/** C6: the admin portfolio dashboard. `asOf` is sent only when given; the server defaults it to today. */
export function getDashboard(credentials: Credentials | null, asOf?: string): Promise<Dashboard> {
  const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : '';
  return requestProtected<Dashboard>(`/api/admin/dashboard${query}`, credentials);
}
