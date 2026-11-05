import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, submitApplication } from '../api';
import type { ApplicationRequest, FieldError } from '../api';
import { useSession } from '../auth/SessionContext';
import { Field } from '../components/Field';
import {
  genericMessage,
  KYC_AGE_MISMATCH_MESSAGE,
  QUOTE_ALREADY_APPLIED_MESSAGE,
  QUOTE_EXPIRED_MESSAGE,
  QUOTE_NOT_FOUND_MESSAGE,
  REQUIRED_MESSAGE,
} from '../messages';

type KycFieldName = 'fullName' | 'dateOfBirth' | 'aadhaar' | 'pan' | 'line1' | 'city' | 'postcode';

interface KycFieldSpec {
  name: KycFieldName;
  label: string;
  /** Dotted path the API uses for this input (C3). */
  path: string;
  type: 'text' | 'date';
  autoComplete?: string;
  inputMode?: 'numeric';
}

const FIELDS: readonly KycFieldSpec[] = [
  { name: 'fullName', label: 'Full name', path: 'kyc.fullName', type: 'text', autoComplete: 'name' },
  { name: 'dateOfBirth', label: 'Date of birth', path: 'kyc.dateOfBirth', type: 'date', autoComplete: 'bday' },
  { name: 'aadhaar', label: 'Aadhaar', path: 'kyc.aadhaar', type: 'text', autoComplete: 'off' },
  { name: 'pan', label: 'PAN', path: 'kyc.pan', type: 'text', autoComplete: 'off' },
  { name: 'line1', label: 'Address line 1', path: 'kyc.address.line1', type: 'text', autoComplete: 'address-line1' },
  { name: 'city', label: 'City', path: 'kyc.address.city', type: 'text', autoComplete: 'address-level2' },
  { name: 'postcode', label: 'Postcode', path: 'kyc.address.postcode', type: 'text', autoComplete: 'postal-code', inputMode: 'numeric' },
];

type Values = Record<KycFieldName, string>;
type Errors = Partial<Record<KycFieldName, string>>;

const EMPTY: Values = { fullName: '', dateOfBirth: '', aadhaar: '', pan: '', line1: '', city: '', postcode: '' };

const BANNER_MESSAGES: Record<string, string> = {
  QUOTE_NOT_FOUND: QUOTE_NOT_FOUND_MESSAGE,
  QUOTE_EXPIRED: QUOTE_EXPIRED_MESSAGE,
  QUOTE_ALREADY_APPLIED: QUOTE_ALREADY_APPLIED_MESSAGE,
};

/** The exact C1 request body. Values are sent as typed: format checks belong to the server. */
function buildRequest(quoteId: string, values: Values): ApplicationRequest {
  return {
    quoteId,
    kyc: {
      fullName: values.fullName,
      dateOfBirth: values.dateOfBirth,
      aadhaar: values.aadhaar,
      pan: values.pan,
      address: { line1: values.line1, city: values.city, postcode: values.postcode },
    },
  };
}

interface Props {
  quoteId: string;
  /** Called with the new application ID after a 201. The shell uses it to open the Status page. */
  onSubmitted?: (applicationId: string) => void;
}

export function ApplyPage({ quoteId, onSubmitted }: Props) {
  const { credentials } = useSession();
  const [values, setValues] = useState<Values>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [unmatched, setUnmatched] = useState<FieldError[]>([]);
  const [banner, setBanner] = useState<{ code?: string; message: string } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdId, setCreatedId] = useState<string | null>(null);

  function onValueChange(name: KycFieldName, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
  }

  function showFailure(error: unknown) {
    const nextErrors: Errors = {};
    const rest: FieldError[] = [];
    let nextBanner: { code?: string; message: string } | null = null;

    if (error instanceof ApiError) {
      const code = error.problem.code;
      const bannerMessage = code ? BANNER_MESSAGES[code] : undefined;
      if (code === 'KYC_AGE_MISMATCH') nextErrors.dateOfBirth = KYC_AGE_MISMATCH_MESSAGE;
      if (bannerMessage) nextBanner = { code, message: bannerMessage };

      for (const entry of error.fieldErrors) {
        if (code === 'KYC_AGE_MISMATCH' && entry.field === 'kyc.dateOfBirth') continue;
        if (bannerMessage && entry.field === 'quoteId') continue;
        const spec = FIELDS.find((candidate) => candidate.path === entry.field);
        if (spec) {
          const existing = nextErrors[spec.name];
          nextErrors[spec.name] = existing ? `${existing}; ${entry.message}` : entry.message;
        } else {
          rest.push(entry);
        }
      }
      if (!nextBanner && Object.keys(nextErrors).length === 0 && rest.length === 0) {
        nextBanner = { message: genericMessage(error, 'The application could not be submitted.') };
      }
    } else {
      nextBanner = { message: genericMessage(error, 'The application could not be submitted.') };
    }

    setErrors(nextErrors);
    setUnmatched(rest);
    setBanner(nextBanner);
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBanner(null);
    setUnmatched([]);

    const missing: Errors = {};
    for (const spec of FIELDS) {
      if (values[spec.name].trim() === '') missing[spec.name] = REQUIRED_MESSAGE;
    }
    setErrors(missing);
    if (Object.keys(missing).length > 0) return;

    setSubmitting(true);
    try {
      const created = await submitApplication(credentials, buildRequest(quoteId, values));
      setCreatedId(created.applicationId);
      onSubmitted?.(created.applicationId);
    } catch (error) {
      showFailure(error);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="page">
      <h1>Apply</h1>
      <p className="muted">
        Quote <span className="mono" data-testid="apply-quote-id">{quoteId}</span>
      </p>

      <form className="panel" onSubmit={onSubmit} noValidate data-testid="apply-form">
        {banner ? (
          <p className="banner banner-error" role="alert" data-testid="apply-error" data-code={banner.code}>
            {banner.message}
          </p>
        ) : null}
        {unmatched.length > 0 ? (
          <ul className="banner banner-error" role="alert" data-testid="form-errors">
            {unmatched.map((entry, index) => (
              <li key={`${entry.field}-${index}`}>
                {entry.field}: {entry.message}
              </li>
            ))}
          </ul>
        ) : null}

        <div className="fields">
          {FIELDS.map((spec) => {
            const error = errors[spec.name];
            const id = `apply-input-${spec.name}`;
            return (
              <Field
                key={spec.name}
                id={id}
                label={spec.label}
                fieldTestId={`field-${spec.name}`}
                errorTestId={`error-${spec.name}`}
                error={error}
              >
                <input
                  id={id}
                  data-testid={`input-${spec.name}`}
                  type={spec.type}
                  autoComplete={spec.autoComplete}
                  inputMode={spec.inputMode}
                  value={values[spec.name]}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? `${id}-error` : undefined}
                  onChange={(event) => onValueChange(spec.name, event.target.value)}
                />
              </Field>
            );
          })}
        </div>

        <button type="submit" className="button" data-testid="submit-application" disabled={submitting}>
          {submitting ? 'Submitting...' : 'Submit application'}
        </button>
      </form>

      {createdId && !onSubmitted ? (
        <p className="banner" data-testid="apply-success">
          Application submitted.{' '}
          <a data-testid="apply-success-link" href={`#/applications/${encodeURIComponent(createdId)}`}>
            View status
          </a>
        </p>
      ) : null}
    </main>
  );
}
