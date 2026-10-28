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
