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
  errors?: FieldError[];
}
