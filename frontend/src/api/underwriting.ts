import { postProtected, requestProtected } from './http';
import type { Credentials, DecisionRequest, DecisionResult, OverrideRequest, QueueItem } from './types';

/** C4: the underwriting queue. `includeDeclined=true` is refused with 403 unless the caller is an ADMIN (U-11). */
export function getUnderwritingQueue(credentials: Credentials | null, includeDeclined: boolean): Promise<QueueItem[]> {
  const path = includeDeclined ? '/api/underwriting/queue?includeDeclined=true' : '/api/underwriting/queue';
  return requestProtected<QueueItem[]>(path, credentials);
}

/** C5: manual decision on a REFERRED application (UNDERWRITER). */
export function submitDecision(credentials: Credentials | null, applicationId: string, body: DecisionRequest): Promise<DecisionResult> {
  return postProtected<DecisionResult>(`/api/underwriting/${encodeURIComponent(applicationId)}/decision`, credentials, body);
}

/** C5: override of a DECLINED application (ADMIN). */
export function submitOverride(credentials: Credentials | null, applicationId: string, body: OverrideRequest): Promise<DecisionResult> {
  return postProtected<DecisionResult>(`/api/underwriting/${encodeURIComponent(applicationId)}/override`, credentials, body);
}
