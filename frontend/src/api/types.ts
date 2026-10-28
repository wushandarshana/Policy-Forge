// Wire types for contracts C1-C3 (sprint plan) and specs/quote-engine_spec.md.
// Every money value is a decimal string exactly as the API sends it. Never convert it to a JS number.

export type ProductCode = 'TERM_LIFE' | 'MOTOR' | 'HOUSEHOLD';

/** C1: one item of `GET /api/products`. */
export interface Product {
  productCode: string;
  productName: string;
  ratingModel: string;
  activeVersion: number;
}

export interface TermLifeRiskInputs {
  age: number;
  smoker: boolean;
  sumInsured: string;
  termYears: number;
  bmi: string;
  healthDeclarations: string[];
}

export interface MotorRiskInputs {
  vehicleValue: string;
  vehicleAgeYears: number;
  driverAge: number;
  claimsLast3Years: number;
  noClaimBonusYears: number;
  vehicleUse: 'PRIVATE' | 'COMMERCIAL';
}

export interface HouseholdRiskInputs {
  buildingSumInsured: string;
  contentsSumInsured: string;
  propertyAgeYears: number;
  constructionType: 'BRICK' | 'CONCRETE' | 'TIMBER';
  floodZone: 'LOW' | 'MEDIUM' | 'HIGH';
  hasSecuritySystem: boolean;
  hasFloodDefences: boolean;
}

/** Inputs as the form builds them: a required input that was left blank is simply absent. */
export type RiskInputs = Partial<TermLifeRiskInputs> | Partial<MotorRiskInputs> | Partial<HouseholdRiskInputs>;

/** C2 request: `POST /api/quotes`. */
export interface QuoteRequest {
  productCode: string;
  riskInputs: RiskInputs;
}

/** One applied factor of a quote. `value` is a decimal string. */
export interface BreakdownItem {
  factor: string;
  value: string;
}

/** C2 response: `POST /api/quotes` (201) and `GET /api/quotes/{id}`. */
export interface Quote {
  quoteId: string;
  productCode: string;
  ruleVersion: number;
  sumInsured: string;
  annualPremium: string;
  breakdown: BreakdownItem[];
  /** ISO date, for example 2026-11-27. */
  validUntil: string;
}

/** C3: one entry of `errors[]` in a 400 problem+json body. */
export interface FieldError {
  field: string;
  message: string;
}

/** RFC 7807 body (C3). `errors` is present on validation failures. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  /** Named business error (C3): QUOTE_NOT_FOUND, QUOTE_EXPIRED, KYC_AGE_MISMATCH, QUOTE_ALREADY_APPLIED, ... */
  code?: string;
  errors?: FieldError[];
}

// ---- Application, KYC and underwriting (contracts C1-C5, specs/underwriting_spec.md) ----

/** HTTP Basic credentials, held in memory only (spec UI "Sign-in"). */
export interface Credentials {
  username: string;
  password: string;
}

export type ApplicationStatus = 'SUBMITTED' | 'UNDERWRITING' | 'APPROVED' | 'REFERRED' | 'DECLINED' | 'ISSUED';
export type UnderwritingOutcome = 'AUTO_BIND' | 'MANUAL_REVIEW' | 'DECLINE';

/** U-8: one fired rule, with its description. */
export interface Reason {
  code: string;
  description: string;
}

/** C1 request: exactly `{quoteId, kyc}` (U-1). */
export interface ApplicationRequest {
  quoteId: string;
  kyc: {
    fullName: string;
    dateOfBirth: string;
    aadhaar: string;
    pan: string;
    address: { line1: string; city: string; postcode: string };
  };
}

/** C1 response (201) and each item of C2. It carries no KYC field (U-7). */
export interface ApplicationSummary {
  applicationId: string;
  quoteId: string;
  productCode: string;
  status: ApplicationStatus;
  decision: UnderwritingOutcome;
  reasonCodes: string[];
  reasons: Reason[];
  ruleVersion: number;
  /** ISO instant. */
  submittedAt: string;
}

/** One declared risk input as the queue returns it. Money stays a decimal string. */
export type RiskInputValue = string | number | boolean | string[];

/** C4: one queue item, built from the U-7 allow-list. Aadhaar and PAN arrive masked. */
export interface QueueItem {
  applicationId: string;
  quoteId: string;
  productCode: string;
  status: ApplicationStatus;
  submittedAt: string;
  ruleVersion: number;
  decision: UnderwritingOutcome;
  reasonCodes: string[];
  reasons: Reason[];
  kyc: { fullName: string; aadhaar: string; pan: string };
  riskInputs: Record<string, RiskInputValue>;
}

export type ManualDecision = 'APPROVE' | 'DECLINE';

/** C5 decision body. */
export interface DecisionRequest {
  decision: ManualDecision;
  reasonCode: string;
  comment: string;
}

/** C5 override body. */
export interface OverrideRequest {
  reasonCode: string;
  comment: string;
}

/** C5 response (200) of both decision and override. */
export interface DecisionResult {
  applicationId: string;
  previousStatus: ApplicationStatus;
  status: ApplicationStatus;
}
