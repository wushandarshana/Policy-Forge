import { postProtected, requestProtected } from './http';
import type {
  Credentials,
  EndorseRequest,
  EndorsementPreview,
  EndorsementResult,
  IssueRequest,
  PolicyDetail,
  PolicyHeader,
  PolicySummary,
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
