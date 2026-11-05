import { postProtected, requestProtected } from './http';
import type {
  CancelRequest,
  CancellationResult,
  Credentials,
  EndorseRequest,
  EndorsementPreview,
  EndorsementResult,
  IssueRequest,
  PolicyDetail,
  PolicyHeader,
  PolicySummary,
  RefundQuote,
  RenewalPayment,
} from './types';

/** C1: issue an APPROVED application (CUSTOMER). The body is `{}` or `{ effectiveDate }`. */
export function issuePolicy(
  credentials: Credentials | null,
  applicationId: string,
  request: IssueRequest,
): Promise<PolicyHeader> {
  return postProtected<PolicyHeader>(`/api/applications/${encodeURIComponent(applicationId)}/issue`, credentials, request);
}

/** C2: the caller's policies, newest first. */
export function listMyPolicies(credentials: Credentials | null): Promise<PolicySummary[]> {
  return requestProtected<PolicySummary[]>('/api/policies/mine', credentials);
}

/** C3: one policy with versions, endorsements, transitions and terms. */
export function getPolicy(credentials: Credentials | null, policyNumber: string): Promise<PolicyDetail> {
  return requestProtected<PolicyDetail>(`/api/policies/${encodeURIComponent(policyNumber)}`, credentials);
}

/** C4: apply an endorsement (owner). */
export function endorsePolicy(
  credentials: Credentials | null,
  policyNumber: string,
  request: EndorseRequest,
): Promise<EndorsementResult> {
  return postProtected<EndorsementResult>(
    `/api/policies/${encodeURIComponent(policyNumber)}/endorsements`,
    credentials,
    request,
  );
}

/** C5: preview an endorsement. Same body and checks as C4; writes nothing. */
export function previewEndorsement(
  credentials: Credentials | null,
  policyNumber: string,
  request: EndorseRequest,
): Promise<EndorsementPreview> {
  return postProtected<EndorsementPreview>(
    `/api/policies/${encodeURIComponent(policyNumber)}/endorsements/preview`,
    credentials,
    request,
  );
}

/** C2 (sprint 4): pay the pending renewal offer (owner). The body is exactly `{}`: the amount is never client-supplied. */
export function payRenewal(credentials: Credentials | null, policyNumber: string): Promise<RenewalPayment> {
  return postProtected<RenewalPayment>(
    `/api/policies/${encodeURIComponent(policyNumber)}/renewal/pay`,
    credentials,
    {},
  );
}

/** C3 (sprint 4): preview the refund of a cancellation. `date` is sent only when given. Writes nothing. */
export function previewCancellation(
  credentials: Credentials | null,
  policyNumber: string,
  date?: string,
): Promise<RefundQuote> {
  const query = date ? `?date=${encodeURIComponent(date)}` : '';
  return requestProtected<RefundQuote>(
    `/api/policies/${encodeURIComponent(policyNumber)}/cancel/preview${query}`,
    credentials,
  );
}

/** C4 (sprint 4): cancel a policy (owner). The body is exactly `{ reason }` or `{ reason, cancellationDate }`. */
export function cancelPolicy(
  credentials: Credentials | null,
  policyNumber: string,
  request: CancelRequest,
): Promise<CancellationResult> {
  return postProtected<CancellationResult>(`/api/policies/${encodeURIComponent(policyNumber)}/cancel`, credentials, request);
}
