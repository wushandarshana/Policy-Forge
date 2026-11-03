import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { vi } from 'vitest';
import { SessionProvider } from '../auth/SessionContext';
import type {
  ApplicationSummary,
  Credentials,
  Endorsement,
  PolicyDetail,
  PolicyHeader,
  PolicySummary,
  PolicyTransition,
  PolicyVersion,
  QueueItem,
} from '../api/types';

// Synthetic fixtures only (root CLAUDE.md rule 4).
export const CUSTOMER: Credentials = { username: 'customer1', password: 'test-password' };
export const UNDERWRITER: Credentials = { username: 'underwriter1', password: 'test-password' };
export const ADMIN: Credentials = { username: 'admin1', password: 'test-password' };

export const QUOTE_ID = 'Q-123e4567-e89b-42d3-a456-426614174000';

export const OK_REASONS = [{ code: 'UW-OK', description: 'No underwriting rule fired' }];

export function application(overrides: Partial<ApplicationSummary> = {}): ApplicationSummary {
  return {
    applicationId: 'A-0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7e8',
    quoteId: QUOTE_ID,
    productCode: 'TERM_LIFE',
    status: 'APPROVED',
    decision: 'AUTO_BIND',
    reasonCodes: ['UW-OK'],
    reasons: OK_REASONS,
    ruleVersion: 1,
    submittedAt: '2026-10-28T09:30:00Z',
    ...overrides,
  };
}

export function queueItem(overrides: Partial<QueueItem> = {}): QueueItem {
  return {
    applicationId: 'A-11111111-2222-4333-8444-555555555555',
    quoteId: 'Q-aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee',
    productCode: 'TERM_LIFE',
    status: 'REFERRED',
    submittedAt: '2026-10-28T09:00:00Z',
    ruleVersion: 1,
    decision: 'MANUAL_REVIEW',
    reasonCodes: ['UW-TL-002'],
    reasons: [{ code: 'UW-TL-002', description: 'Smoker with sum insured above 10,000,000' }],
    kyc: { fullName: 'Asha Test', aadhaar: '**********1234', pan: '******234F' },
    riskInputs: { age: 41, smoker: true, sumInsured: '12000000.00', termYears: 20, bmi: '24.0', healthDeclarations: [] },
    ...overrides,
  };
}

export const POLICY_NUMBER = 'PF-TL-2026-000001';

/** Synthetic C1/C3 header: the AC-06.2 TERM_LIFE policy (2026-11-01..2027-10-31, premium 6850.00). */
export function policyHeader(overrides: Partial<PolicyHeader> = {}): PolicyHeader {
  return {
    policyNumber: POLICY_NUMBER,
    applicationId: 'A-0b1c2d3e-4f50-4a61-8b72-93a4b5c6d7e8',
    productCode: 'TERM_LIFE',
    status: 'ACTIVE',
    currentVersionNo: 1,
    ruleVersion: 1,
    effectiveDate: '2026-11-01',
    expiryDate: '2027-10-31',
    sumInsured: '5000000.00',
    annualPremium: '6850.00',
    nextPremiumDueDate: '2027-11-01',
    endorsementCount: 0,
    ...overrides,
  };
}

export function policySummary(overrides: Partial<PolicySummary> = {}): PolicySummary {
  return {
    policyNumber: POLICY_NUMBER,
    productCode: 'TERM_LIFE',
    status: 'ACTIVE',
    sumInsured: '5000000.00',
    annualPremium: '6850.00',
    nextPremiumDueDate: '2027-11-01',
    endorsementCount: 0,
    ...overrides,
  };
}

export function policyVersion(overrides: Partial<PolicyVersion> = {}): PolicyVersion {
  return {
    versionNo: 1,
    effectiveDate: '2026-11-01',
    expiryDate: '2027-10-31',
    sumInsured: '5000000.00',
    annualPremium: '6850.00',
    ruleVersion: 1,
    riskInputs: { age: 35, smoker: false, sumInsured: '5000000.00', termYears: 20 },
    address: { line1: '12 Old Street', city: 'Pune', postcode: '411001' },
    nominees: [],
    createdAt: '2026-11-01T09:00:00Z',
    ...overrides,
  };
}

export function endorsement(overrides: Partial<Endorsement> = {}): Endorsement {
  return {
    sequenceNo: 1,
    type: 'CHANGE_ADDRESS',
    changes: { address: { line1: '99 New Road', city: 'Mumbai', postcode: '400001' } },
    beforeVersionNo: 1,
    afterVersionNo: 2,
    newAnnualPremium: '6850.00',
    adjustment: '0.00',
    effectiveDate: '2026-11-02',
    actorId: 'customer1',
    createdAt: '2026-11-02T10:00:00Z',
    ...overrides,
  };
}

export function transition(overrides: Partial<PolicyTransition> = {}): PolicyTransition {
  return {
    fromStatus: null,
    toStatus: 'ACTIVE',
    reason: 'ISSUANCE',
    actorId: 'customer1',
    at: '2026-11-01T09:00:00Z',
    ...overrides,
  };
}

/** A freshly issued policy: v1, no endorsements, the `null -> ACTIVE` transition. */
export function policyDetail(overrides: Partial<PolicyDetail> = {}): PolicyDetail {
  return {
    policy: policyHeader(),
    versions: [policyVersion()],
    endorsements: [],
    transitions: [transition()],
    terms: [{ termNo: 1, startDate: '2026-11-01', endDate: '2027-10-31', premium: '6850.00', paid: true }],
    refunds: [],
    ...overrides,
  };
}

export function jsonResponse(status: number, body: unknown, contentType = 'application/json'): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': contentType } });
}

/** A C3 problem+json response. */
export function problem(status: number, body: Record<string, unknown> = {}): Response {
  return jsonResponse(status, { title: 'Problem', status, ...body }, 'application/problem+json');
}

export type FetchMock = ReturnType<typeof vi.fn>;
export type Handler = (url: string, init: RequestInit | undefined) => Response | Promise<Response>;

export function installFetch(handler: Handler): FetchMock {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => handler(String(input), init));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

export function renderWithSession(ui: ReactElement, credentials: Credentials | null) {
  return render(<SessionProvider initialCredentials={credentials}>{ui}</SessionProvider>);
}

export interface Call {
  url: string;
  method: string;
  init: RequestInit | undefined;
}

export function calls(fetchMock: FetchMock): Call[] {
  return fetchMock.mock.calls.map(([input, init]) => ({
    url: String(input),
    method: ((init as RequestInit | undefined)?.method ?? 'GET').toUpperCase(),
    init: init as RequestInit | undefined,
  }));
}

export function postedJson(call: Call): unknown {
  return JSON.parse(String(call.init?.body));
}

export function header(call: Call, name: string): string | null {
  return new Headers(call.init?.headers).get(name);
}

export function basic(credentials: Credentials): string {
  return `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
}
