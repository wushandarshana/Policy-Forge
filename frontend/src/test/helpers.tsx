import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { vi } from 'vitest';
import { SessionProvider } from '../auth/SessionContext';
import type { ApplicationSummary, Credentials, QueueItem } from '../api/types';

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
