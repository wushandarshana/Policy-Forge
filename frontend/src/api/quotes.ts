import { postProtected, requestJson } from './http';
import type { Credentials, Quote, QuoteRequest } from './types';

/**
 * C2: quote creation. The endpoint is public, but when someone is signed in the caller passes the credentials so the
 * server can record the quote's owner (AC-01.13, P-8). Pass null when signed out: no Authorization header is sent.
 * Rejects with an ApiError (400 carries `fieldErrors`; 401 means the credentials were not accepted).
 */
export function createQuote(request: QuoteRequest, credentials: Credentials | null = null): Promise<Quote> {
  return postProtected<Quote>('/api/quotes', credentials, request);
}

/** C2: public quote read. Never carries credentials. Health inputs are never part of the body (NFR-04.2). */
export function getQuote(quoteId: string): Promise<Quote> {
  return requestJson<Quote>(`/api/quotes/${encodeURIComponent(quoteId)}`);
}
