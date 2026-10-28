import type { Credentials, DecisionRequest, DecisionResult, OverrideRequest, QueueItem } from './types';

/** C4: stub, implemented in the green step. */
export function getUnderwritingQueue(_credentials: Credentials | null, _includeDeclined: boolean): Promise<QueueItem[]> {
  return Promise.reject(new Error('not implemented'));
}

/** C5: stub, implemented in the green step. */
export function submitDecision(_credentials: Credentials | null, _applicationId: string, _body: DecisionRequest): Promise<DecisionResult> {
  return Promise.reject(new Error('not implemented'));
}

/** C5: stub, implemented in the green step. */
export function submitOverride(_credentials: Credentials | null, _applicationId: string, _body: OverrideRequest): Promise<DecisionResult> {
  return Promise.reject(new Error('not implemented'));
}
