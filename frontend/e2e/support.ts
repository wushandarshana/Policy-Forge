import { mkdirSync, rmSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { expect } from '@playwright/test';
import type { APIRequestContext, Page } from '@playwright/test';

// Synthetic data only (root CLAUDE.md rule 4). The seeded users share one dummy password (SecurityConfig).
export const PASSWORD = 'password';
export type User = 'customer1' | 'customer2' | 'underwriter1' | 'admin1';

export const KYC = {
  fullName: 'Asha Test',
  aadhaar: 'XXXX-XXXX-1234',
  pan: 'ABCDE1234F',
  line1: '1 Demo Street',
  city: 'Pune',
  postcode: '411001',
};

/**
 * A date of birth giving exactly `age` completed years on the server's clock (zone Asia/Kolkata), with about half a
 * year of margin so the age is the same on either side of midnight (P-8).
 */
export function dateOfBirthFor(age: number): string {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());
  const [year, month, day] = today.split('-').map(Number);
  const born = new Date(Date.UTC(year - age, month - 1, day));
  born.setUTCDate(born.getUTCDate() - 180);
  return born.toISOString().slice(0, 10);
}

export function basic(user: User): string {
  return `Basic ${Buffer.from(`${user}:${PASSWORD}`).toString('base64')}`;
}

export interface TermLifeProfile {
  age: number;
  smoker: boolean;
  sumInsured: string;
  bmi: string;
}

export const AC_04_1_PROFILE: TermLifeProfile = { age: 35, smoker: false, sumInsured: '5000000.00', bmi: '24.0' };
export const AC_04_2_PROFILE: TermLifeProfile = { age: 41, smoker: true, sumInsured: '12000000.00', bmi: '24.0' };
export const AC_04_3_PROFILE: TermLifeProfile = { age: 62, smoker: false, sumInsured: '5000000.00', bmi: '37.0' };

/** Creates a TERM_LIFE quote through the public API, so each test owns its quote (P-8). */
export async function createQuote(request: APIRequestContext, profile: TermLifeProfile): Promise<string> {
  const response = await request.post('/api/quotes', {
    data: {
      productCode: 'TERM_LIFE',
      riskInputs: {
        age: profile.age,
        smoker: profile.smoker,
        sumInsured: profile.sumInsured,
        termYears: 20,
        bmi: profile.bmi,
        healthDeclarations: [],
      },
    },
  });
  expect(response.status()).toBe(201);
  const body = (await response.json()) as { quoteId: string };
  return body.quoteId;
}

/** Submits an application through the API as customer1 and returns its ID. */
export async function createApplication(request: APIRequestContext, profile: TermLifeProfile): Promise<string> {
  const quoteId = await createQuote(request, profile);
  const response = await request.post('/api/applications', {
    headers: { Authorization: basic('customer1') },
    data: {
      quoteId,
      kyc: {
        fullName: KYC.fullName,
        dateOfBirth: dateOfBirthFor(profile.age),
        aadhaar: KYC.aadhaar,
        pan: KYC.pan,
        address: { line1: KYC.line1, city: KYC.city, postcode: KYC.postcode },
      },
    },
  });
  expect(response.status()).toBe(201);
  const body = (await response.json()) as { applicationId: string };
  return body.applicationId;
}

export interface IssuedPolicy {
  applicationId: string;
  policyNumber: string;
}

/**
 * Creates an APPROVED application and issues it through the API as customer1 (effective date omitted, so today on the
 * server's clock). Each test owns its policy and acts on it by number (P-8).
 */
export async function createPolicy(request: APIRequestContext, profile: TermLifeProfile): Promise<IssuedPolicy> {
  const applicationId = await createApplication(request, profile);
  const response = await request.post(`/api/applications/${applicationId}/issue`, {
    headers: { Authorization: basic('customer1') },
    data: {},
  });
  expect(response.status()).toBe(201);
  const body = (await response.json()) as { policyNumber: string };
  return { applicationId, policyNumber: body.policyNumber };
}

/** Opens a policy's detail page, signing in as customer1 on the way (the gate shows the sign-in form first). */
export async function openPolicy(page: Page, policyNumber: string): Promise<void> {
  await page.goto(`/#/policies/${policyNumber}`);
  await signIn(page, 'customer1');
  await expect(page.getByTestId('policy-number')).toHaveText(policyNumber);
}

/** The page must never scroll sideways (spec UI section: no horizontal scrolling at 375px). */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}

export async function signIn(page: Page, user: User): Promise<void> {
  await page.getByTestId('signin-username').fill(user);
  await page.getByTestId('signin-password').fill(PASSWORD);
  await page.getByTestId('signin-submit').click();
}

/** Fills the Apply form. Overrides replace single values, for the invalid-KYC journey. */
export async function fillKyc(page: Page, age: number, overrides: Partial<Record<string, string>> = {}): Promise<void> {
  const values: Record<string, string> = {
    fullName: KYC.fullName,
    dateOfBirth: dateOfBirthFor(age),
    aadhaar: KYC.aadhaar,
    pan: KYC.pan,
    line1: KYC.line1,
    city: KYC.city,
    postcode: KYC.postcode,
    ...overrides,
  };
  for (const [name, value] of Object.entries(values)) {
    await page.getByTestId(`input-${name}`).fill(value);
  }
}

/** `iso` (YYYY-MM-DD) moved by `days`, as a calendar date (no clock or zone involved). */
export function shiftDate(iso: string, days: number): string {
  const [year, month, day] = iso.split('-').map(Number);
  const moved = new Date(Date.UTC(year, month - 1, day + days));
  return moved.toISOString().slice(0, 10);
}

/** The policy's expiry date (the end of its current paid term), read through the API as customer1. */
export async function policyExpiry(request: APIRequestContext, policyNumber: string): Promise<string> {
  const response = await request.get(`/api/policies/${policyNumber}`, { headers: { Authorization: basic('customer1') } });
  expect(response.status()).toBe(200);
  const body = (await response.json()) as { policy: { expiryDate: string } };
  return body.policy.expiryDate;
}

export interface RenewalCycleRun {
  runDate: string;
  offersCreated: string[];
  lapsed: string[];
}

/** Runs the renewal cycle for `date` through the admin job endpoint (C1) as admin1. */
export async function runRenewalCycle(request: APIRequestContext, date: string): Promise<RenewalCycleRun> {
  const response = await request.post(`/api/admin/jobs/renewal-cycle?date=${date}`, {
    headers: { Authorization: basic('admin1') },
    data: {},
  });
  expect(response.status()).toBe(200);
  return (await response.json()) as RenewalCycleRun;
}

const RENEWAL_LOCK = join(tmpdir(), 'policyforge-e2e-renewal.lock');
const STALE_LOCK_MS = 120_000;

function tryAcquireRenewalLock(): boolean {
  try {
    mkdirSync(RENEWAL_LOCK);
    return true;
  } catch {
    try {
      // A crashed run leaves its lock behind: a lock older than any test is taken over.
      if (Date.now() - statSync(RENEWAL_LOCK).mtimeMs > STALE_LOCK_MS) rmSync(RENEWAL_LOCK, { recursive: true, force: true });
    } catch {
      // The holder released it between the two calls: the next poll takes it.
    }
    return false;
  }
}

/**
 * Runs `work` as the only test that creates a policy and runs the renewal cycle (P-12). A cycle offers a renewal to every
 * in-force policy whose offer window has opened, not only to the test's own, so two tests running the job at the same
 * time (desktop and mobile projects, separate workers) could each offer the other's policy first and make the job's
 * `offersCreated` miss it. The lock is a directory in the OS temp folder, shared by every worker.
 */
export async function withRenewalLock<T>(work: () => Promise<T>): Promise<T> {
  await expect.poll(tryAcquireRenewalLock, { timeout: 120_000, intervals: [100, 200, 400] }).toBe(true);
  try {
    return await work();
  } finally {
    rmSync(RENEWAL_LOCK, { recursive: true, force: true });
  }
}
