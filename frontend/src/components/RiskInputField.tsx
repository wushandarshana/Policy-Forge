import type { RiskField } from '../pages/riskFields';

interface Props {
  field: RiskField;
  value: string | boolean;
  error?: string;
  onChange: (name: string, value: string | boolean) => void;
}

/** One labelled risk input. The error, when present, renders inside the same wrapper as its input. */
export function RiskInputField({ field, value, error, onChange }: Props) {
  const inputId = `quote-input-${field.name}`;
  const errorId = `quote-error-${field.name}`;
  const common = {
    id: inputId,
    'data-testid': `input-${field.name}`,
    'aria-invalid': error ? true : undefined,
    'aria-describedby': error ? errorId : undefined,
  } as const;
  const text = typeof value === 'string' ? value : '';

  let control;
  switch (field.kind) {
    case 'boolean':
      control = (
        <input
          {...common}
          type="checkbox"
          checked={value === true}
          onChange={(event) => onChange(field.name, event.target.checked)}
        />
      );
      break;
    case 'select':
      control = (
        <select {...common} value={text} onChange={(event) => onChange(field.name, event.target.value)}>
          {field.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
      break;
    case 'integer':
      control = (
        <input
          {...common}
          type="number"
          inputMode="numeric"
          step={1}
          min={field.min}
          max={field.max}
          value={text}
          onChange={(event) => onChange(field.name, event.target.value)}
        />
      );
      break;
    case 'decimal':
    case 'list':
      control = (
        <input
          {...common}
          type="text"
          inputMode={field.kind === 'decimal' ? 'decimal' : 'text'}
          autoComplete="off"
          placeholder={field.placeholder}
          value={text}
          onChange={(event) => onChange(field.name, event.target.value)}
        />
      );
      break;
  }

  return (
    <div
      className={field.kind === 'boolean' ? 'field field-boolean' : 'field'}
      data-testid={`field-${field.name}`}
    >
      <label htmlFor={inputId}>{field.label}</label>
      {control}
      {error ? (
        <p className="field-error" id={errorId} role="alert" data-testid={`error-${field.name}`}>
          {error}
        </p>
      ) : null}
    </div>
  );
}
