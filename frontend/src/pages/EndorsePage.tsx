import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, endorsePolicy, getPolicy, previewEndorsement } from '../api';
import type {
  EndorseRequest,
  EndorsementPreview,
  EndorsementType,
  FieldError,
  NomineeRelationship,
  PolicyDetail,
} from '../api';
import { useSession } from '../auth/SessionContext';
import { Field } from '../components/Field';
import { ENDORSEMENT_CODE_MESSAGES, genericMessage, POLICY_NOT_FOUND_MESSAGE, REQUIRED_MESSAGE } from '../messages';

type FieldName =
  | 'line1'
  | 'city'
  | 'postcode'
  | 'nomineeName'
  | 'nomineeRelationship'
  | 'nomineeSharePercent'
  | 'newSumInsured'
  | 'newBuildingSumInsured'
  | 'newContentsSumInsured'
  | 'effectiveDate';

type Values = Record<FieldName, string>;
type Errors = Partial<Record<FieldName, string>>;

interface FieldSpec {
  name: FieldName;
  label: string;
  /** Dotted path the API uses for this input (C6). */
  path: string;
  kind: 'text' | 'date' | 'relationship';
  required: boolean;
  inputMode?: 'numeric' | 'decimal';
  autoComplete?: string;
}

const TYPES: readonly { value: EndorsementType; label: string }[] = [
  { value: 'CHANGE_ADDRESS', label: 'Change address' },
  { value: 'ADD_NOMINEE', label: 'Add nominee' },
  { value: 'CHANGE_SUM_INSURED', label: 'Change sum insured' },
];

/** The five relationship values of the endorsement spec. */
const RELATIONSHIPS: readonly NomineeRelationship[] = ['SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER'];

const EFFECTIVE_DATE: FieldSpec = {
  name: 'effectiveDate',
  label: 'Effective date (optional)',
  path: 'effectiveDate',
  kind: 'date',
  required: false,
};

const ADDRESS_FIELDS: readonly FieldSpec[] = [
  { name: 'line1', label: 'Address line 1', path: 'address.line1', kind: 'text', required: true, autoComplete: 'address-line1' },
  { name: 'city', label: 'City', path: 'address.city', kind: 'text', required: true, autoComplete: 'address-level2' },
  {
    name: 'postcode',
    label: 'Postcode',
    path: 'address.postcode',
    kind: 'text',
    required: true,
    inputMode: 'numeric',
    autoComplete: 'postal-code',
  },
];

const NOMINEE_FIELDS: readonly FieldSpec[] = [
  { name: 'nomineeName', label: 'Nominee name', path: 'nominee.name', kind: 'text', required: true, autoComplete: 'off' },
  { name: 'nomineeRelationship', label: 'Relationship', path: 'nominee.relationship', kind: 'relationship', required: true },
  {
    name: 'nomineeSharePercent',
    label: 'Share percent',
    path: 'nominee.sharePercent',
    kind: 'text',
    required: true,
    inputMode: 'numeric',
    autoComplete: 'off',
  },
];

const SUM_INSURED_FIELD: FieldSpec = {
  name: 'newSumInsured',
  label: 'New sum insured',
  path: 'newSumInsured',
  kind: 'text',
  required: true,
  inputMode: 'decimal',
  autoComplete: 'off',
};

const HOUSEHOLD_FIELDS: readonly FieldSpec[] = [
  {
    name: 'newBuildingSumInsured',
    label: 'New building sum insured',
    path: 'newBuildingSumInsured',
    kind: 'text',
    // HOUSEHOLD needs at least one of the two components, which is checked separately.
    required: false,
    inputMode: 'decimal',
    autoComplete: 'off',
  },
  {
    name: 'newContentsSumInsured',
    label: 'New contents sum insured',
    path: 'newContentsSumInsured',
    kind: 'text',
    required: false,
    inputMode: 'decimal',
    autoComplete: 'off',
  },
];

const EMPTY: Values = {
  line1: '',
  city: '',
  postcode: '',
  nomineeName: '',
  nomineeRelationship: '',
  nomineeSharePercent: '',
  newSumInsured: '',
  newBuildingSumInsured: '',
  newContentsSumInsured: '',
  effectiveDate: '',
};

/** The inputs one endorsement type shows. The API names the household components, so the product decides them. */
function fieldsFor(type: EndorsementType, productCode: string): readonly FieldSpec[] {
  switch (type) {
    case 'CHANGE_ADDRESS':
      return [...ADDRESS_FIELDS, EFFECTIVE_DATE];
    case 'ADD_NOMINEE':
      return [...NOMINEE_FIELDS, EFFECTIVE_DATE];
    default:
      return productCode === 'HOUSEHOLD'
        ? [...HOUSEHOLD_FIELDS, EFFECTIVE_DATE]
        : [SUM_INSURED_FIELD, EFFECTIVE_DATE];
  }
}

/** The household sum-insured fields: at least one of them must be filled. */
function isHouseholdSumInsured(type: EndorsementType, productCode: string): boolean {
  return type === 'CHANGE_SUM_INSURED' && productCode === 'HOUSEHOLD';
}

/** Client-side checks cover required fields only. Format and business rules belong to the server. */
function missingFields(type: EndorsementType, productCode: string, values: Values): Errors {
  const missing: Errors = {};
  for (const spec of fieldsFor(type, productCode)) {
    if (spec.required && values[spec.name].trim() === '') missing[spec.name] = REQUIRED_MESSAGE;
  }
  if (
    isHouseholdSumInsured(type, productCode) &&
    values.newBuildingSumInsured.trim() === '' &&
    values.newContentsSumInsured.trim() === ''
  ) {
    missing.newBuildingSumInsured = REQUIRED_MESSAGE;
  }
  return missing;
}

/**
 * The exact C4 / C5 body: `type`, that type's payload keys, and `effectiveDate` only when it was given. Money goes as
 * the typed decimal string. `sharePercent` goes as an integer when the input is whole digits and as typed otherwise, so
 * the server (not the browser) rejects it.
 */
function buildRequest(type: EndorsementType, values: Values): EndorseRequest {
  const text = (name: FieldName) => values[name].trim();
  const effective = text('effectiveDate') === '' ? {} : { effectiveDate: text('effectiveDate') };
  switch (type) {
    case 'CHANGE_ADDRESS':
      return {
        type,
        address: { line1: text('line1'), city: text('city'), postcode: text('postcode') },
        ...effective,
      };
    case 'ADD_NOMINEE': {
      const share = text('nomineeSharePercent');
      return {
        type,
        nominee: {
          name: text('nomineeName'),
          relationship: text('nomineeRelationship'),
          sharePercent: /^\d+$/.test(share) ? parseInt(share, 10) : share,
        },
        ...effective,
      };
    }
    default: {
      const request: EndorseRequest = { type: 'CHANGE_SUM_INSURED', ...effective };
      if (text('newSumInsured') !== '') request.newSumInsured = text('newSumInsured');
      if (text('newBuildingSumInsured') !== '') request.newBuildingSumInsured = text('newBuildingSumInsured');
      if (text('newContentsSumInsured') !== '') request.newContentsSumInsured = text('newContentsSumInsured');
      return request;
    }
  }
}

type Load =
  | { state: 'loading' }
  | { state: 'error'; message: string; notFound: boolean }
  | { state: 'loaded'; detail: PolicyDetail };

interface Banner {
  code?: string;
  message: string;
}

interface Props {
  policyNumber: string;
  /** Called with the policy number after a 201. The shell uses it to return to the detail page. */
  onEndorsed?: (policyNumber: string) => void;
}

/** Endorse a policy: a type selector with a dynamic form, and a C5 preview before a sum-insured change is confirmed. */
export function EndorsePage({ policyNumber, onEndorsed }: Props) {
  const { credentials } = useSession();
  const [load, setLoad] = useState<Load>({ state: 'loading' });

  useEffect(() => {
    let active = true;
    setLoad({ state: 'loading' });
    getPolicy(credentials, policyNumber)
      .then((detail) => {
        if (active) setLoad({ state: 'loaded', detail });
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiError && error.status === 404) {
          setLoad({ state: 'error', message: POLICY_NOT_FOUND_MESSAGE, notFound: true });
        } else {
          setLoad({ state: 'error', message: genericMessage(error, 'The policy could not be loaded.'), notFound: false });
        }
      });
    return () => {
      active = false;
    };
  }, [policyNumber, credentials]);

  return (
    <main className="page">
      <h1>Endorse policy</h1>
      <a className="back-link" data-testid="endorse-back-link" href={`#/policies/${encodeURIComponent(policyNumber)}`}>
        Back to policy
      </a>

      {load.state === 'loading' ? (
        <p className="muted" data-testid="endorse-loading">
          Loading...
        </p>
      ) : null}

      {load.state === 'error' ? (
        <p className="banner banner-error" role="alert" data-testid={load.notFound ? 'endorse-not-found' : 'endorse-load-error'}>
          {load.message}
        </p>
      ) : null}

      {load.state === 'loaded' ? (
        <EndorseForm
          policyNumber={policyNumber}
          productCode={load.detail.policy.productCode}
          onEndorsed={onEndorsed}
        />
      ) : null}
    </main>
  );
}

function EndorseForm({
  policyNumber,
  productCode,
  onEndorsed,
}: {
  policyNumber: string;
  productCode: string;
  onEndorsed?: (policyNumber: string) => void;
}) {
  const { credentials } = useSession();
  const [type, setType] = useState<EndorsementType>('CHANGE_ADDRESS');
  const [values, setValues] = useState<Values>(EMPTY);
  const [errors, setErrors] = useState<Errors>({});
  const [unmatched, setUnmatched] = useState<FieldError[]>([]);
  const [banner, setBanner] = useState<Banner | null>(null);
  const [preview, setPreview] = useState<EndorsementPreview | null>(null);
  const [busy, setBusy] = useState<'preview' | 'confirm' | null>(null);
  /** Bumped on every edit, so a preview that answers an older input set is dropped. */
  const generation = useRef(0);

  const fields = fieldsFor(type, productCode);
  const needsPreview = type === 'CHANGE_SUM_INSURED';

  function clearOutcome() {
    generation.current += 1;
    setPreview(null);
    setBanner(null);
    setUnmatched([]);
  }

  function onTypeChange(next: EndorsementType) {
    setType(next);
    setErrors({});
    clearOutcome();
  }

  function onValueChange(name: FieldName, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => {
      if (!current[name]) return current;
      const next = { ...current };
      delete next[name];
      return next;
    });
    clearOutcome();
  }

  function showFailure(error: unknown) {
    const nextErrors: Errors = {};
    const rest: FieldError[] = [];
    let nextBanner: Banner | null = null;

    if (error instanceof ApiError) {
      const code =
        error.problem.code ?? (error.problem.type === '/problems/invalid-policy-state' ? 'INVALID_POLICY_STATE' : undefined);
      const codeMessage = code ? ENDORSEMENT_CODE_MESSAGES[code] : undefined;
      if (codeMessage) nextBanner = { code, message: codeMessage };
      else if (error.status === 404) nextBanner = { message: POLICY_NOT_FOUND_MESSAGE };

      for (const entry of error.fieldErrors) {
        const spec = fields.find((candidate) => candidate.path === entry.field);
        if (spec) {
          const existing = nextErrors[spec.name];
          nextErrors[spec.name] = existing ? `${existing}; ${entry.message}` : entry.message;
        } else if (!codeMessage) {
          rest.push(entry);
        }
      }
      if (!nextBanner && Object.keys(nextErrors).length === 0 && rest.length === 0) {
        nextBanner = { message: genericMessage(error, 'The endorsement could not be processed.') };
      }
    } else {
      nextBanner = { message: genericMessage(error, 'The endorsement could not be processed.') };
    }

    setErrors(nextErrors);
    setUnmatched(rest);
    setBanner(nextBanner);
  }

  /** Returns true when every required input is filled. */
  function checkRequired(): boolean {
    const missing = missingFields(type, productCode, values);
    setErrors(missing);
    return Object.keys(missing).length === 0;
  }

  async function onPreview() {
    setBanner(null);
    setUnmatched([]);
    setPreview(null);
    if (!checkRequired()) return;
    const mine = generation.current;
    setBusy('preview');
    try {
      const result = await previewEndorsement(credentials, policyNumber, buildRequest(type, values));
      if (generation.current === mine) setPreview(result);
    } catch (error) {
      if (generation.current === mine) showFailure(error);
    } finally {
      setBusy(null);
    }
  }

  async function onConfirm() {
    if (needsPreview && !preview) return;
    setBanner(null);
    setUnmatched([]);
    if (!checkRequired()) return;
    setBusy('confirm');
    try {
      await endorsePolicy(credentials, policyNumber, buildRequest(type, values));
      onEndorsed?.(policyNumber);
    } catch (error) {
      showFailure(error);
    } finally {
      setBusy(null);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void onConfirm();
  }

  return (
    <form className="panel endorse-form" onSubmit={onSubmit} noValidate data-testid="endorse-form">
      <div className="field" data-testid="field-endorse-type">
        <label htmlFor="endorse-type">Endorsement type</label>
        <select
          id="endorse-type"
          data-testid="endorse-type"
          value={type}
          onChange={(event) => onTypeChange(event.target.value as EndorsementType)}
        >
          {TYPES.map((item) => (
            <option key={item.value} value={item.value}>
              {item.label}
            </option>
          ))}
        </select>
      </div>

      <div className="fields">
        {fields.map((spec) => (
          <Field
            key={`${type}-${spec.name}`}
            id={`endorse-input-${spec.name}`}
            label={spec.label}
            fieldTestId={`field-${spec.name}`}
            errorTestId={`error-${spec.name}`}
            error={errors[spec.name]}
          >
            <Control
              spec={spec}
              value={values[spec.name]}
              error={errors[spec.name]}
              onChange={(value) => onValueChange(spec.name, value)}
            />
          </Field>
        ))}
      </div>

      {unmatched.length > 0 ? (
        <ul className="banner banner-error" role="alert" data-testid="endorse-form-errors">
          {unmatched.map((entry, index) => (
            <li key={`${entry.field}-${index}`}>
              {entry.field}: {entry.message}
            </li>
          ))}
        </ul>
      ) : null}

      {banner ? (
        <p className="banner banner-error" role="alert" data-testid="endorse-error" data-code={banner.code}>
          {banner.message}
        </p>
      ) : null}

      {preview ? <PreviewResult preview={preview} /> : null}

      <div className="toolbar">
        {needsPreview ? (
          <button
            type="button"
            className="button button-secondary"
            data-testid="endorse-preview"
            disabled={busy !== null}
            onClick={() => void onPreview()}
          >
            {busy === 'preview' ? 'Calculating...' : 'Preview premium'}
          </button>
        ) : null}
        <button
          type="submit"
          className="button"
          data-testid="endorse-confirm"
          disabled={busy !== null || (needsPreview && !preview)}
        >
          {busy === 'confirm' ? 'Confirming...' : 'Confirm endorsement'}
        </button>
      </div>
    </form>
  );
}

function Control({
  spec,
  value,
  error,
  onChange,
}: {
  spec: FieldSpec;
  value: string;
  error?: string;
  onChange: (value: string) => void;
}) {
  const common = {
    id: `endorse-input-${spec.name}`,
    'data-testid': `input-${spec.name}`,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? `endorse-input-${spec.name}-error` : undefined,
  } as const;

  if (spec.kind === 'relationship') {
    return (
      <select {...common} value={value} onChange={(event) => onChange(event.target.value)}>
        <option value="">Select a relationship</option>
        {RELATIONSHIPS.map((relationship) => (
          <option key={relationship} value={relationship}>
            {relationship}
          </option>
        ))}
      </select>
    );
  }
  return (
    <input
      {...common}
      type={spec.kind === 'date' ? 'date' : 'text'}
      inputMode={spec.inputMode}
      autoComplete={spec.autoComplete}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  );
}

/** C5 response, exactly as received: nothing is recomputed in the browser. */
function PreviewResult({ preview }: { preview: EndorsementPreview }) {
  return (
    <section className="result" aria-labelledby="preview-heading" data-testid="preview-result">
      <h2 id="preview-heading">Premium preview</h2>
      <dl className="summary">
        <div>
          <dt>Current annual premium</dt>
          <dd data-testid="preview-current-premium">{preview.currentAnnualPremium}</dd>
        </div>
        <div>
          <dt>New annual premium</dt>
          <dd data-testid="preview-new-premium">{preview.newAnnualPremium}</dd>
        </div>
        <div>
          <dt>Adjustment</dt>
          <dd data-testid="preview-adjustment">{preview.adjustment}</dd>
        </div>
        <div>
          <dt>Effective date</dt>
          <dd data-testid="preview-effective-date">{preview.effectiveDate}</dd>
        </div>
        <div>
          <dt>Unexpired days</dt>
          <dd data-testid="preview-unexpired-days">{preview.unexpiredDays}</dd>
        </div>
        <div>
          <dt>Term days</dt>
          <dd data-testid="preview-term-days">{preview.termDays}</dd>
        </div>
      </dl>
    </section>
  );
}
