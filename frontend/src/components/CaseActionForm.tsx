import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, submitDecision, submitOverride } from '../api';
import type { DecisionResult, ManualDecision } from '../api';
import { useSession } from '../auth/SessionContext';
import { genericMessage, INVALID_APPLICATION_STATE_MESSAGE, REQUIRED_MESSAGE } from '../messages';
import { manualReasonsFor, OVERRIDE_REASONS } from '../reasonCodes';
import { Field } from './Field';

type FieldName = 'decision' | 'reasonCode' | 'comment';
type Errors = Partial<Record<FieldName, string>>;

interface Props {
  kind: 'decision' | 'override';
  applicationId: string;
  onDone: (result: DecisionResult) => void;
}

/** Test IDs differ between the two forms because the Override action must be findable on its own. */
function ids(kind: Props['kind']) {
  if (kind === 'override') {
    return {
      form: 'override-form',
      reason: 'override-reason-select',
      comment: 'override-comment-input',
      submit: 'override-action',
      banner: 'override-error',
      field: (name: string) => `override-field-${name}`,
      error: (name: string) => `override-error-${name}`,
    };
  }
  return {
    form: 'decision-form',
    reason: 'reason-select',
    comment: 'comment-input',
    submit: 'submit-decision',
    banner: 'decision-error',
    field: (name: string) => `field-${name}`,
    error: (name: string) => `error-${name}`,
  };
}

/**
 * The manual decision form (APPROVE or DECLINE with a U-10 paired reason code) or the admin override form
 * (UW-OVR-001 or UW-OVR-002). Client checks cover required fields only: the comment length and the application
 * state are the server's to judge.
 */
export function CaseActionForm({ kind, applicationId, onDone }: Props) {
  const { credentials } = useSession();
  const testIds = ids(kind);
  const [decision, setDecision] = useState<ManualDecision | ''>('');
  const [reasonCode, setReasonCode] = useState('');
  const [comment, setComment] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const reasons = kind === 'override' ? OVERRIDE_REASONS : manualReasonsFor(decision);

  function clearError(name: FieldName) {
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  }

  function onDecisionChange(value: string) {
    setDecision(value === 'APPROVE' || value === 'DECLINE' ? value : '');
    setReasonCode('');
    clearError('decision');
    clearError('reasonCode');
  }

  function showFailure(error: unknown) {
    const next: Errors = {};
    const rest: string[] = [];
    let text: string | null = null;
    if (error instanceof ApiError && error.status === 409) {
      text = INVALID_APPLICATION_STATE_MESSAGE;
    } else if (error instanceof ApiError && error.status === 400 && error.fieldErrors.length > 0) {
      for (const entry of error.fieldErrors) {
        if (entry.field === 'decision' || entry.field === 'reasonCode' || entry.field === 'comment') {
          const existing = next[entry.field];
          next[entry.field] = existing ? `${existing}; ${entry.message}` : entry.message;
        } else {
          rest.push(`${entry.field}: ${entry.message}`);
        }
      }
      if (rest.length > 0) text = rest.join('; ');
    } else {
      text = genericMessage(error, 'The action could not be completed.');
    }
    setErrors(next);
    setMessage(text);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(null);

    const missing: Errors = {};
    if (kind === 'decision' && decision === '') missing.decision = REQUIRED_MESSAGE;
    if (reasonCode === '') missing.reasonCode = REQUIRED_MESSAGE;
    if (comment.trim() === '') missing.comment = REQUIRED_MESSAGE;
    setErrors(missing);
    if (Object.keys(missing).length > 0 || (kind === 'decision' && decision === '')) return;

    setSubmitting(true);
    try {
      const result =
        kind === 'override' || decision === ''
          ? await submitOverride(credentials, applicationId, { reasonCode, comment })
          : await submitDecision(credentials, applicationId, { decision, reasonCode, comment });
      onDone(result);
    } catch (error) {
      showFailure(error);
    } finally {
      setSubmitting(false);
    }
  }

  const prefix = `${kind}-form`;
  return (
    <form className="panel" onSubmit={onSubmit} noValidate data-testid={testIds.form}>
      <h3>{kind === 'override' ? 'Override decline' : 'Decide'}</h3>
      {message ? (
        <p className="banner banner-error" role="alert" data-testid={testIds.banner}>
          {message}
        </p>
      ) : null}

      {kind === 'decision' ? (
        <Field
          id={`${prefix}-decision`}
          label="Decision"
          fieldTestId={testIds.field('decision')}
          errorTestId={testIds.error('decision')}
          error={errors.decision}
        >
          <select
            id={`${prefix}-decision`}
            data-testid="decision-select"
            value={decision}
            aria-invalid={errors.decision ? true : undefined}
            onChange={(event) => onDecisionChange(event.target.value)}
          >
            <option value="">Choose a decision</option>
            <option value="APPROVE">Approve</option>
            <option value="DECLINE">Decline</option>
          </select>
        </Field>
      ) : null}

      <Field
        id={`${prefix}-reason`}
        label="Reason code"
        fieldTestId={testIds.field('reasonCode')}
        errorTestId={testIds.error('reasonCode')}
        error={errors.reasonCode}
      >
        <select
          id={`${prefix}-reason`}
          data-testid={testIds.reason}
          value={reasonCode}
          disabled={kind === 'decision' && decision === ''}
          aria-invalid={errors.reasonCode ? true : undefined}
          onChange={(event) => {
            setReasonCode(event.target.value);
            clearError('reasonCode');
          }}
        >
          <option value="">Choose a reason code</option>
          {reasons.map((reason) => (
            <option key={reason.code} value={reason.code} data-testid={`reason-option-${reason.code}`}>
              {reason.code} - {reason.description}
            </option>
          ))}
        </select>
      </Field>

      <Field
        id={`${prefix}-comment`}
        label="Comment"
        fieldTestId={testIds.field('comment')}
        errorTestId={testIds.error('comment')}
        error={errors.comment}
      >
        <textarea
          id={`${prefix}-comment`}
          data-testid={testIds.comment}
          rows={4}
          value={comment}
          aria-invalid={errors.comment ? true : undefined}
          onChange={(event) => {
            setComment(event.target.value);
            clearError('comment');
          }}
        />
      </Field>

      <button type="submit" className="button" data-testid={testIds.submit} disabled={submitting}>
        {kind === 'override' ? 'Override and approve' : 'Submit decision'}
      </button>
    </form>
  );
}
