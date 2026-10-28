import { postProtected, requestProtected } from './http';
import type { ApplicationRequest, ApplicationSummary, Credentials } from './types';

/** C1: submit an application (CUSTOMER). The 201 body has no KYC field. */
export function submitApplication(credentials: Credentials | null, request: ApplicationRequest): Promise<ApplicationSummary> {
  return postProtected<ApplicationSummary>('/api/applications', credentials, request);
}

/** C2: the caller's own applications, newest first. The Status page picks one by ID (P-6). */
export function listMyApplications(credentials: Credentials | null): Promise<ApplicationSummary[]> {
  return requestProtected<ApplicationSummary[]>('/api/applications/mine', credentials);
}
