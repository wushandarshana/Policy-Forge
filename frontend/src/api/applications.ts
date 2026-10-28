import type { ApplicationRequest, ApplicationSummary, Credentials } from './types';

/** C1: stub, implemented in the green step. */
export function submitApplication(_credentials: Credentials | null, _request: ApplicationRequest): Promise<ApplicationSummary> {
  return Promise.reject(new Error('not implemented'));
}

/** C2: stub, implemented in the green step. */
export function listMyApplications(_credentials: Credentials | null): Promise<ApplicationSummary[]> {
  return Promise.reject(new Error('not implemented'));
}
