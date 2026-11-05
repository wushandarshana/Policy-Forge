import { ApiError } from './api';

// Fixed messages for named business errors (C3). They never echo anything the server or the user sent.
export const QUOTE_NOT_FOUND_MESSAGE = 'We could not find that quote. Please request a new quote.';
export const QUOTE_EXPIRED_MESSAGE = 'This quote has expired. Please request a new quote.';
export const KYC_AGE_MISMATCH_MESSAGE = 'The date of birth does not match the age used for this quote.';
export const QUOTE_ALREADY_APPLIED_MESSAGE = 'This quote already has an application. Request a new quote to apply again.';
export const INVALID_APPLICATION_STATE_MESSAGE = 'This application is no longer in a state that allows that action.';
export const APPLICATION_NOT_FOUND_MESSAGE = 'We could not find that application.';
export const REQUIRED_MESSAGE = 'This field is required.';

/** A fixed message for a failure that carries no field errors and no named business code. */
export function genericMessage(error: unknown, action: string): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return 'Your sign-in was not accepted. Sign in again.';
    if (error.status === 403) return 'Your account is not allowed to do that.';
    if (error.status === 404) return APPLICATION_NOT_FOUND_MESSAGE;
  }
  return `${action} Please try again.`;
}

/** Quote creation answered 401: the credentials were not accepted. The request is never retried anonymously (P-8). */
export const QUOTE_SIGN_IN_AGAIN_MESSAGE =
  'Your sign-in was not accepted, so no quote was created. Sign out, sign in again and retry.';

export const POLICY_NOT_FOUND_MESSAGE = 'We could not find that policy.';

/** C6 business codes and states of the endorsement endpoints. They never echo anything the server or user sent. */
export const ENDORSEMENT_CODE_MESSAGES: Readonly<Record<string, string>> = {
  EFFECTIVE_DATE_OUT_OF_TERM: 'The effective date must fall within the current policy term.',
  EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT: 'The effective date cannot be before the latest endorsement.',
  NOMINEE_NOT_SUPPORTED: 'Nominees can only be added to Term Life policies.',
  NOMINEE_LIMIT_REACHED: 'A policy can have at most 3 nominees.',
  NOMINEE_SHARE_EXCEEDED: 'The total nominee share cannot exceed 100%.',
  NOMINEE_RELATIONSHIP_INVALID: 'Choose one of the listed relationships.',
  SUM_INSURED_OUT_OF_RANGE: 'The new sum insured is outside the allowed range for this policy.',
  SUM_INSURED_UNCHANGED: 'The new sum insured must differ from the current one.',
  POSTCODE_INVALID: 'The postcode must be exactly 6 digits.',
  INVALID_POLICY_STATE: 'This policy is not in force, so it cannot be endorsed.',
  POLICY_UPDATE_CONFLICT: 'The policy was changed by another request. Reload the policy and try again.',
};

/** Fixed messages for the renewal-pay endpoint (sprint 4 C2, C7). They never echo anything the server sent. */
export const RENEWAL_CODE_MESSAGES: Readonly<Record<string, string>> = {
  NO_PENDING_RENEWAL: 'There is no renewal offer to pay.',
  RENEWAL_PAYMENT_WINDOW_CLOSED: 'The payment window for this renewal has closed.',
  INVALID_POLICY_STATE: 'This policy is not in force, so the renewal cannot be paid.',
  POLICY_UPDATE_CONFLICT: 'The policy was changed by another request. Reload the policy and try again.',
};

/** Fixed messages for the cancel and cancel-preview endpoints (sprint 4 C3, C4, C7). */
export const CANCELLATION_CODE_MESSAGES: Readonly<Record<string, string>> = {
  CANCELLATION_DATE_OUT_OF_TERM: 'The cancellation date must fall within the current paid term.',
  INVALID_POLICY_STATE: 'This policy is not in force, so it cannot be cancelled.',
  POLICY_UPDATE_CONFLICT: 'The policy was changed by another request. Reload the policy and try again.',
};

/** A 409 with no recognised code on cancel. */
export const CANCEL_CONFLICT_MESSAGE = 'The policy could not be cancelled in its current state.';

export const RENEWAL_PAID_NOTICE = 'Renewal paid. The policy is renewed.';
export const CANCELLED_NOTICE = 'Policy cancelled. The refund is recorded under Refunds.';

export const DASHBOARD_FORBIDDEN_MESSAGE = 'You are not authorised to view the portfolio dashboard.';

/** The cancellation reasons the SPA offers (G-4). */
export const CANCELLATION_REASON_LABELS = [
  { value: 'NO_LONGER_NEEDED', label: 'No longer needed' },
  { value: 'REPLACED_BY_OTHER_COVER', label: 'Replaced by other cover' },
  { value: 'AFFORDABILITY', label: 'Affordability' },
  { value: 'OTHER', label: 'Other' },
] as const;
