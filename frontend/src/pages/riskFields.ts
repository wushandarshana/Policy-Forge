import type { RiskInputs } from '../api';

/**
 * Per-product risk input definitions (judgment call J13). The API exposes no input schema, so these lists
 * mirror the "Inputs" of each product in specs/quote-engine_spec.md: TERM_LIFE 6, MOTOR 6, HOUSEHOLD 7.
 * The names are the exact JSON property names of the request's `riskInputs`.
 */

export interface SelectOption {
  value: string;
  label: string;
}

interface BaseField {
  name: string;
  label: string;
}

/** Whole number sent as a JSON number (ages, years, counts). Never money. */
export interface IntegerField extends BaseField {
  kind: 'integer';
  min?: number;
  max?: number;
}

/** Decimal sent as the string the user typed (sum insured, vehicle value, bmi). Never converted. */
export interface DecimalField extends BaseField {
  kind: 'decimal';
  placeholder: string;
}

/** Enumeration. `asInteger` sends the chosen option as a JSON number (termYears, noClaimBonusYears). */
export interface SelectField extends BaseField {
  kind: 'select';
  options: SelectOption[];
  asInteger?: boolean;
}

export interface BooleanField extends BaseField {
  kind: 'boolean';
}

/** Comma-separated codes sent as a string array. May be empty. */
export interface ListField extends BaseField {
  kind: 'list';
  placeholder: string;
}

export type RiskField = IntegerField | DecimalField | SelectField | BooleanField | ListField;

function options(...values: string[]): SelectOption[] {
  return values.map((value) => ({ value, label: value }));
}

const TERM_LIFE_FIELDS: RiskField[] = [
  { kind: 'integer', name: 'age', label: 'Age (18 to 65)', min: 18, max: 65 },
  { kind: 'boolean', name: 'smoker', label: 'Smoker' },
  { kind: 'decimal', name: 'sumInsured', label: 'Sum insured', placeholder: '5000000.00' },
  { kind: 'select', name: 'termYears', label: 'Term (years)', options: options('10', '20', '30'), asInteger: true },
  { kind: 'decimal', name: 'bmi', label: 'BMI', placeholder: '24.5' },
  { kind: 'list', name: 'healthDeclarations', label: 'Health declarations — upper-case codes, comma separated, max 10 (e.g. DIABETES)', placeholder: 'e.g. DIABETES' },
];

const MOTOR_FIELDS: RiskField[] = [
  { kind: 'decimal', name: 'vehicleValue', label: 'Vehicle value (sum insured)', placeholder: '800000.00' },
  { kind: 'integer', name: 'vehicleAgeYears', label: 'Vehicle age (years)', min: 0 },
  { kind: 'integer', name: 'driverAge', label: 'Driver age', min: 18, max: 99 },
  { kind: 'integer', name: 'claimsLast3Years', label: 'Claims in the last 3 years', min: 0 },
  {
    kind: 'select',
    name: 'noClaimBonusYears',
    label: 'No-claim bonus years',
    options: options('0', '1', '2', '3', '4', '5'),
    asInteger: true,
  },
  { kind: 'select', name: 'vehicleUse', label: 'Vehicle use', options: options('PRIVATE', 'COMMERCIAL') },
];

const HOUSEHOLD_FIELDS: RiskField[] = [
  { kind: 'decimal', name: 'buildingSumInsured', label: 'Building sum insured', placeholder: '6000000.00' },
  { kind: 'decimal', name: 'contentsSumInsured', label: 'Contents sum insured', placeholder: '1000000.00' },
  { kind: 'integer', name: 'propertyAgeYears', label: 'Property age (years)', min: 0 },
  { kind: 'select', name: 'constructionType', label: 'Construction type', options: options('BRICK', 'CONCRETE', 'TIMBER') },
  { kind: 'select', name: 'floodZone', label: 'Flood zone', options: options('LOW', 'MEDIUM', 'HIGH') },
  { kind: 'boolean', name: 'hasSecuritySystem', label: 'Has a security system' },
  { kind: 'boolean', name: 'hasFloodDefences', label: 'Has flood defences' },
];

const FIELDS_BY_PRODUCT: Record<string, RiskField[]> = {
  TERM_LIFE: TERM_LIFE_FIELDS,
  MOTOR: MOTOR_FIELDS,
  HOUSEHOLD: HOUSEHOLD_FIELDS,
};

export function fieldsFor(productCode: string): RiskField[] {
  return FIELDS_BY_PRODUCT[productCode] ?? [];
}

/** Raw form state: text for text-like controls, a boolean for checkboxes. */
export type FormValues = Record<string, string | boolean>;

export function initialValues(fields: RiskField[]): FormValues {
  const values: FormValues = {};
  for (const field of fields) {
    if (field.kind === 'boolean') values[field.name] = false;
    else if (field.kind === 'select') values[field.name] = field.options[0]?.value ?? '';
    else values[field.name] = '';
  }
  return values;
}

const WHOLE_NUMBER = /^-?\d+$/;

/**
 * Build the `riskInputs` JSON object. A blank or malformed whole number is left out so the server reports it as
 * missing or invalid by field name. Decimal text is passed through untouched as a string.
 */
export function buildRiskInputs(fields: RiskField[], values: FormValues): RiskInputs {
  const inputs: Record<string, unknown> = {};
  for (const field of fields) {
    const raw = values[field.name];
    switch (field.kind) {
      case 'boolean':
        inputs[field.name] = raw === true;
        break;
      case 'integer': {
        const text = typeof raw === 'string' ? raw.trim() : '';
        if (WHOLE_NUMBER.test(text)) inputs[field.name] = parseInt(text, 10);
        break;
      }
      case 'decimal': {
        const text = typeof raw === 'string' ? raw.trim() : '';
        if (text !== '') inputs[field.name] = text;
        break;
      }
      case 'select': {
        const text = typeof raw === 'string' ? raw : '';
        if (text === '') break;
        inputs[field.name] = field.asInteger ? parseInt(text, 10) : text;
        break;
      }
      case 'list': {
        const text = typeof raw === 'string' ? raw : '';
        inputs[field.name] = text
          .split(',')
          .map((code) => code.trim().toUpperCase().replace(/[\s-]+/g, '_'))
          .filter((code) => code !== '');
        break;
      }
    }
  }
  return inputs as RiskInputs;
}
