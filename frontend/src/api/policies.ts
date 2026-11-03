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

const NOT_IMPLEMENTED = 'not implemented';

/** C1: issue an APPROVED application (CUSTOMER). */
export function issuePolicy(
  _credentials: Credentials | null,
  _applicationId: string,
  _request: IssueRequest,
): Promise<PolicyHeader> {
  return Promise.reject(new Error(NOT_IMPLEMENTED));
}

/** C2: the caller's policies, newest first. */
export function listMyPolicies(_credentials: Credentials | null): Promise<PolicySummary[]> {
  return Promise.reject(new Error(NOT_IMPLEMENTED));
}

/** C3: one policy with versions, endorsements, transitions and terms. */
export function getPolicy(_credentials: Credentials | null, _policyNumber: string): Promise<PolicyDetail> {
  return Promise.reject(new Error(NOT_IMPLEMENTED));
}

/** C4: apply an endorsement (owner). */
export function endorsePolicy(
  _credentials: Credentials | null,
  _policyNumber: string,
  _request: EndorseRequest,
): Promise<EndorsementResult> {
  return Promise.reject(new Error(NOT_IMPLEMENTED));
}

/** C5: preview an endorsement. Writes nothing. */
export function previewEndorsement(
  _credentials: Credentials | null,
  _policyNumber: string,
  _request: EndorseRequest,
): Promise<EndorsementPreview> {
  return Promise.reject(new Error(NOT_IMPLEMENTED));
}
