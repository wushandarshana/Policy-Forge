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

// ---- Policy issuance and endorsement (contracts C1-C6, sprint 3 plan; specs/policy-issuance_spec.md and
// specs/endorsement_spec.md). Money is a decimal string, dates are ISO YYYY-MM-DD, instants are ISO-8601. ----

export type PolicyStatus = 'ACTIVE' | 'ENDORSED' | 'RENEWED' | 'LAPSED' | 'CANCELLED';
export type EndorsementType = 'CHANGE_ADDRESS' | 'ADD_NOMINEE' | 'CHANGE_SUM_INSURED';
export type NomineeRelationship = 'SPOUSE' | 'CHILD' | 'PARENT' | 'SIBLING' | 'OTHER';

/** C1 request body: `{}` or `{ effectiveDate }`. Any other key is a 400. */
export interface IssueRequest {
  effectiveDate?: string;
}

/** C1 response (201), `policy` of C3 and C4. */
export interface PolicyHeader {
  policyNumber: string;
  applicationId: string;
  productCode: string;
  status: PolicyStatus;
  currentVersionNo: number;
  ruleVersion: number;
  effectiveDate: string;
  expiryDate: string;
  sumInsured: string;
  annualPremium: string;
  nextPremiumDueDate: string;
  endorsementCount: number;
}

/** C2: one item of `GET /api/policies/mine`, newest first. */
export interface PolicySummary {
  policyNumber: string;
  productCode: string;
  status: PolicyStatus;
  sumInsured: string;
  annualPremium: string;
  nextPremiumDueDate: string;
  endorsementCount: number;
}

export interface PolicyAddress {
  line1: string;
  city: string;
  postcode: string;
}

export interface Nominee {
  name: string;
  relationship: string;
  sharePercent: number;
}

/** C3 version. `riskInputs` is the P-9 allow-list: declared inputs minus the health inputs. */
export interface PolicyVersion {
  versionNo: number;
  effectiveDate: string;
  expiryDate: string;
  sumInsured: string;
  annualPremium: string;
  ruleVersion: number;
  riskInputs: Record<string, RiskInputValue>;
  address: PolicyAddress;
  nominees: Nominee[];
  createdAt: string;
}

/** C3 endorsement `changes`: `{address}`, `{nominee}`, `{newSumInsured}` or `{newBuildingSumInsured?, newContentsSumInsured?}`. */
export interface EndorsementChanges {
  address?: PolicyAddress;
  nominee?: Nominee;
  newSumInsured?: string;
  newBuildingSumInsured?: string;
  newContentsSumInsured?: string;
}

/** C3 endorsement, ascending by `sequenceNo`. */
export interface Endorsement {
  sequenceNo: number;
  type: EndorsementType;
  changes: EndorsementChanges;
  beforeVersionNo: number;
  afterVersionNo: number;
  newAnnualPremium: string;
  adjustment: string;
  effectiveDate: string;
  actorId: string;
  createdAt: string;
}

/** C3 transition, in append order. `fromStatus` is null for issuance. */
export interface PolicyTransition {
  fromStatus: PolicyStatus | null;
  toStatus: PolicyStatus;
  reason: string;
  actorId: string;
  at: string;
}

/** C5 term: one row of the paid-term history. `ruleVersion` is the rule version the term was rated with. */
export interface PolicyTerm {
  termNo: number;
  startDate: string;
  endDate: string;
  premium: string;
  paid: boolean;
  ruleVersion: number;
}

/** C5: the unpaid renewal offer, or null on the detail when there is none. `payableUntil` = current term end + grace. */
export interface PendingRenewal {
  termNo: number;
  startDate: string;
  endDate: string;
  premium: string;
  ruleVersion: number;
  payableUntil: string;
}

/** C5 refund record (append-only). `formula` carries only the numbers of the calculation. */
export interface Refund {
  termNo: number;
  cancellationDate: string;
  premium: string;
  termDays: number;
  unexpiredDays: number;
  coolingOff: boolean;
  fee: string;
  amount: string;
  formula: string;
  actorId: string;
  createdAt: string;
}

/** C3 and C5: `GET /api/policies/{policyNumber}`. `refunds` is in append order. */
export interface PolicyDetail {
  policy: PolicyHeader;
  versions: PolicyVersion[];
  endorsements: Endorsement[];
  transitions: PolicyTransition[];
  terms: PolicyTerm[];
  pendingRenewal: PendingRenewal | null;
  refunds: Refund[];
}

/**
 * C4 and C5 request body: flat `{ type, effectiveDate?, ...payload }`. The payload keys are exactly the spec table's.
 * `sharePercent` is an integer; a value that is not whole digits is sent as typed so the server rejects it.
 */
export type EndorseRequest = { effectiveDate?: string } & (
  | { type: 'CHANGE_ADDRESS'; address: PolicyAddress }
  | { type: 'ADD_NOMINEE'; nominee: { name: string; relationship: string; sharePercent: number | string } }
  | {
      type: 'CHANGE_SUM_INSURED';
      newSumInsured?: string;
      newBuildingSumInsured?: string;
      newContentsSumInsured?: string;
    }
);

/** C4 response (201). */
export interface EndorsementResult {
  endorsement: Endorsement;
  policy: PolicyHeader;
}

/** C5 response (200). Writes nothing. */
export interface EndorsementPreview {
  type: EndorsementType;
  effectiveDate: string;
  currentAnnualPremium: string;
  newAnnualPremium: string;
  adjustment: string;
  unexpiredDays: number;
  termDays: number;
}

// ---- Renewal, cancellation and the admin dashboard (contracts C2-C6 and C9, sprint 4 plan; specs/renewal-cancellation_spec.md
// and specs/portfolio-dashboard_spec.md). Money is a decimal string, dates are ISO YYYY-MM-DD, instants are ISO-8601. ----

/** C2 response (200): the policy header and the newly paid term. */
export interface RenewalPayment {
  policy: PolicyHeader;
  term: PolicyTerm;
}

/** G-4: the closed set of cancellation reasons. */
export type CancellationReason = 'NO_LONGER_NEEDED' | 'REPLACED_BY_OTHER_COVER' | 'AFFORDABILITY' | 'OTHER';

/** C3 response (200): the refund the policy would get on `cancellationDate`. Writes nothing. */
export interface RefundQuote {
  cancellationDate: string;
  termNo: number;
  termStart: string;
  termEnd: string;
  premium: string;
  termDays: number;
  unexpiredDays: number;
  coolingOff: boolean;
  fee: string;
  amount: string;
  formula: string;
}

/** C4 request body: exactly `{ reason }` or `{ reason, cancellationDate }`. */
export interface CancelRequest {
  reason: CancellationReason;
  cancellationDate?: string;
}

/** C4 response (201). */
export interface CancellationResult {
  refund: Refund;
  policy: PolicyHeader;
}

/** C6: one renewal-pipeline row. `renewalPremium` is null when no offer exists yet. */
export interface PipelineEntry {
  policyNumber: string;
  termEnd: string;
  renewalPremium: string | null;
  paid: boolean;
}

/** C6 response (200). `activePoliciesByProduct` always has all three products. */
export interface Dashboard {
  asOf: string;
  activePoliciesByProduct: Record<ProductCode, number>;
  premiumCollected: Record<ProductCode, string> & { total: string };
  renewalPipeline: PipelineEntry[];
  lapseForecast: { count: number; policies: string[] };
}
