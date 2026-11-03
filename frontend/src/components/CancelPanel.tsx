import { useRef, useState } from 'react';
import { ApiError, cancelPolicy, previewCancellation } from '../api';
import type { CancelRequest, CancellationReason, FieldError, RefundQuote } from '../api';
import { useSession } from '../auth/SessionContext';
import {
  CANCEL_CONFLICT_MESSAGE,
  CANCELLATION_CODE_MESSAGES,
  CANCELLATION_REASON_LABELS,
  genericMessage,
  POLICY_NOT_FOUND_MESSAGE,
  REQUIRED_MESSAGE,
} from '../messages';
import { Field } from './Field';

interface Props {
  policyNumber: string;
  /** Called after a 201 so the page reloads the detail. */
  onCancelled: () => void;
}

interface Failure {
  dateError?: string;
  reasonError?: string;
  banner?: string;
  unmatched: FieldError[];
}

const DATE_OUT_OF_TERM = 'CANCELLATION_DATE_OUT_OF_TERM';

/** Maps a failed call to the date input, the reason select, a fixed banner or leftover field errors. */
function toFailure(error: unknown, dateField: 'date' | 'cancellationDate'): Failure {
  const failure: Failure = { unmatched: [] };
  if (!(error instanceof ApiError)) {
    failure.banner = genericMessage(error, 'The cancellation could not be processed.');
    return failure;
  }
  const code = error.code;
  const codeMessage = code ? CANCELLATION_CODE_MESSAGES[code] : undefined;
  if (code === DATE_OUT_OF_TERM) failure.dateError = codeMessage;
  else if (codeMessage) failure.banner = codeMessage;
  else if (error.status === 404) failure.banner = POLICY_NOT_FOUND_MESSAGE;
  else if (error.status === 409) failure.banner = CANCEL_CONFLICT_MESSAGE;

  for (const entry of error.fieldErrors) {
    if (entry.field === dateField) failure.dateError = entry.message;
    else if (entry.field === 'reason') failure.reasonError = entry.message;
    else if (!codeMessage) failure.unmatched.push(entry);
  }
  const hasFieldError = failure.dateError || failure.reasonError || failure.unmatched.length > 0;
  if (!failure.banner && !hasFieldError) {
    failure.banner = genericMessage(error, 'The cancellation could not be processed.');
  }
  return failure;
}

/**
 * Cancel a policy: an optional date and a required reason, a C3 refund preview, then a confirm that is only enabled for
 * a preview of the current inputs. The refund values are the server's strings, shown verbatim.
 */
export function CancelPanel({ policyNumber, onCancelled }: Props) {
  const { credentials } = useSession();
  const [date, setDate] = useState('');
  const [reason, setReason] = useState('');
  const [preview, setPreview] = useState<RefundQuote | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [busy, setBusy] = useState<'preview' | 'confirm' | null>(null);
  /** Bumped on every edit, so a preview that answers an older input set is dropped. */
  const generation = useRef(0);

  function clearOutcome() {
    generation.current += 1;
    setPreview(null);
    setFailure(null);
  }

  function onDateChange(value: string) {
    setDate(value);
    clearOutcome();
  }

  function onReasonChange(value: string) {
    setReason(value);
    clearOutcome();
  }

  /** The reason is required for both steps, so a preview is never invalidated by choosing it afterwards. */
  function requireReason(): boolean {
    if (reason !== '') return true;
    setFailure({ reasonError: REQUIRED_MESSAGE, unmatched: [] });
    return false;
  }

  async function onPreview() {
    setPreview(null);
    setFailure(null);
    if (!requireReason()) return;
    const mine = generation.current;
    setBusy('preview');
    try {
      const quote = await previewCancellation(credentials, policyNumber, date.trim() === '' ? undefined : date.trim());
      if (generation.current === mine) setPreview(quote);
    } catch (error) {
      if (generation.current === mine) setFailure(toFailure(error, 'date'));
    } finally {
      setBusy(null);
    }
  }

  async function onConfirm() {
    if (!preview) return;
    setFailure(null);
    if (!requireReason()) return;
    const request: CancelRequest = { reason: reason as CancellationReason };
    if (date.trim() !== '') request.cancellationDate = date.trim();
    setBusy('confirm');
    try {
      await cancelPolicy(credentials, policyNumber, request);
      onCancelled();
    } catch (error) {
      setFailure(toFailure(error, 'cancellationDate'));
    } finally {
      setBusy(null);
    }
  }

  return (
    <section className="panel" aria-labelledby="cancel-heading" data-testid="cancel-panel">
      <h2 id="cancel-heading">Cancel policy</h2>
      <div className="fields">
        <Field
          id="cancel-input-date"
          label="Cancellation date (optional)"
          fieldTestId="field-cancel-date"
          errorTestId="cancel-date-error"
          error={failure?.dateError}
        >
          <input
            id="cancel-input-date"
            type="date"
            data-testid="cancel-date"
            value={date}
            aria-invalid={failure?.dateError ? true : undefined}
            aria-describedby={failure?.dateError ? 'cancel-input-date-error' : undefined}
            onChange={(event) => onDateChange(event.target.value)}
          />
        </Field>
        <Field
          id="cancel-input-reason"
          label="Reason"
          fieldTestId="field-cancel-reason"
          errorTestId="cancel-reason-error"
          error={failure?.reasonError}
        >
          <select
            id="cancel-input-reason"
            data-testid="cancel-reason"
            value={reason}
            aria-invalid={failure?.reasonError ? true : undefined}
            aria-describedby={failure?.reasonError ? 'cancel-input-reason-error' : undefined}
            onChange={(event) => onReasonChange(event.target.value)}
          >
            <option value="">Select a reason</option>
            {CANCELLATION_REASON_LABELS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </Field>
      </div>

      {failure && failure.unmatched.length > 0 ? (
        <ul className="banner banner-error" role="alert" data-testid="cancel-form-errors">
          {failure.unmatched.map((entry, index) => (
            <li key={`${entry.field}-${index}`}>
              {entry.field}: {entry.message}
            </li>
          ))}
        </ul>
      ) : null}

      {failure?.banner ? (
        <p className="banner banner-error" role="alert" data-testid="cancel-error">
          {failure.banner}
        </p>
      ) : null}

      {preview ? <RefundPreview quote={preview} /> : null}

      <div className="toolbar">
        <button
          type="button"
          className="button button-secondary"
          data-testid="cancel-preview"
          disabled={busy !== null}
          onClick={() => void onPreview()}
        >
          {busy === 'preview' ? 'Calculating...' : 'Preview refund'}
        </button>
        <button
          type="button"
          className="button"
          data-testid="cancel-confirm"
          disabled={busy !== null || !preview}
          onClick={() => void onConfirm()}
        >
          {busy === 'confirm' ? 'Cancelling...' : 'Confirm cancellation'}
        </button>
      </div>
    </section>
  );
}

/** C3 response, exactly as received: nothing is recomputed in the browser. */
function RefundPreview({ quote }: { quote: RefundQuote }) {
  return (
    <section className="result" aria-labelledby="refund-preview-heading" data-testid="refund-preview">
      <h3 id="refund-preview-heading">Refund preview</h3>
      <dl className="summary">
        <div>
          <dt>Refund amount</dt>
          <dd data-testid="refund-preview-amount">{quote.amount}</dd>
        </div>
        <div>
          <dt>Fee</dt>
          <dd data-testid="refund-preview-fee">{quote.fee}</dd>
        </div>
        <div>
          <dt>Unexpired / term days</dt>
          <dd data-testid="refund-preview-days">{`${quote.unexpiredDays} / ${quote.termDays}`}</dd>
        </div>
        <div>
          <dt>Cooling-off</dt>
          <dd data-testid="refund-preview-cooling-off">{String(quote.coolingOff)}</dd>
        </div>
        <div>
          <dt>Premium basis</dt>
          <dd data-testid="refund-preview-premium">{quote.premium}</dd>
        </div>
        <div>
          <dt>Cancellation date</dt>
          <dd data-testid="refund-preview-date">{quote.cancellationDate}</dd>
        </div>
      </dl>
      <p className="mono" data-testid="refund-preview-formula">
        {quote.formula}
      </p>
    </section>
  );
}
