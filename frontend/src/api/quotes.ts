import { requestJson } from './http';
import type { Quote, QuoteRequest } from './types';

/** C2: public quote creation. Rejects with an ApiError (400 carries `fieldErrors`). */
export function createQuote(request: QuoteRequest): Promise<Quote> {
  return requestJson<Quote>('/api/quotes', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  });
}

/** C2: public quote read. Health inputs are never part of the body (NFR-04.2). */
export function getQuote(quoteId: string): Promise<Quote> {
  return requestJson<Quote>(`/api/quotes/${encodeURIComponent(quoteId)}`);
}
