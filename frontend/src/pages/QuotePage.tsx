import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, createQuote, listProducts } from '../api';
import type { FieldError, Product, Quote } from '../api';
import { QuoteResult } from '../components/QuoteResult';
import { RiskInputField } from '../components/RiskInputField';
import { buildRiskInputs, fieldsFor, initialValues } from './riskFields';
import type { FormValues } from './riskFields';

/** Accept both `age` and `riskInputs.age` as the name of an input. */
function normaliseField(field: string): string {
  return field.startsWith('riskInputs.') ? field.slice('riskInputs.'.length) : field;
}

export function QuotePage() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [productCode, setProductCode] = useState('');
  const [values, setValues] = useState<FormValues>({});
  const [fieldErrors, setFieldErrors] = useState<FieldError[]>([]);
  const [requestError, setRequestError] = useState<string | null>(null);
  const [quote, setQuote] = useState<Quote | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const fields = useMemo(() => fieldsFor(productCode), [productCode]);

  useEffect(() => {
    let active = true;
    listProducts()
      .then((list) => {
        if (!active) return;
        setProducts(list);
        const first = list[0];
        if (first) {
          setProductCode(first.productCode);
          setValues(initialValues(fieldsFor(first.productCode)));
        }
      })
      .catch((error: unknown) => {
        if (active) setLoadError(error instanceof Error ? error.message : 'Could not load products');
      });
    return () => {
      active = false;
    };
  }, []);

  function clearOutcome() {
    setFieldErrors([]);
    setRequestError(null);
    setQuote(null);
  }

  function onProductChange(code: string) {
    setProductCode(code);
    setValues(initialValues(fieldsFor(code)));
    clearOutcome();
  }

  function onValueChange(name: string, value: string | boolean) {
    setValues((current) => ({ ...current, [name]: value }));
  }

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!productCode) return;
    clearOutcome();
    setSubmitting(true);
    try {
      const created = await createQuote({ productCode, riskInputs: buildRiskInputs(fields, values) });
      setQuote(created);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.fieldErrors.length > 0) {
        setFieldErrors(error.fieldErrors);
      } else if (error instanceof Error) {
        setRequestError(error.message);
      } else {
        setRequestError('The quote could not be created');
      }
    } finally {
      setSubmitting(false);
    }
  }

  const knownNames = new Set(fields.map((field) => field.name));
  const errorByField = new Map<string, string>();
  const unmatched: FieldError[] = [];
  for (const error of fieldErrors) {
    const name = normaliseField(error.field);
    if (knownNames.has(name)) {
      errorByField.set(name, errorByField.has(name) ? `${errorByField.get(name)}; ${error.message}` : error.message);
    } else {
      unmatched.push(error);
    }
  }

  return (
    <main className="page">
      <h1>Get a quote</h1>

      {loadError ? (
        <p className="banner banner-error" role="alert" data-testid="products-error">
          {loadError}
        </p>
      ) : null}

      <form className="quote-form" onSubmit={onSubmit} noValidate data-testid="quote-form">
        <div className="field">
          <label htmlFor="quote-product">Product</label>
          <select
            id="quote-product"
            data-testid="product-select"
            value={productCode}
            onChange={(event) => onProductChange(event.target.value)}
            disabled={products.length === 0}
          >
            {products.map((product) => (
              <option key={product.productCode} value={product.productCode}>
                {product.productName}
              </option>
            ))}
          </select>
        </div>

        {unmatched.length > 0 ? (
          <ul className="banner banner-error" role="alert" data-testid="form-errors">
            {unmatched.map((error, index) => (
              <li key={`${error.field}-${index}`}>
                {error.field}: {error.message}
              </li>
            ))}
          </ul>
        ) : null}

        <div className="fields">
          {fields.map((field) => (
            <RiskInputField
              key={`${productCode}-${field.name}`}
              field={field}
              value={values[field.name] ?? (field.kind === 'boolean' ? false : '')}
              error={errorByField.get(field.name)}
              onChange={onValueChange}
            />
          ))}
        </div>

        {requestError ? (
          <p className="banner banner-error" role="alert" data-testid="request-error">
            {requestError}
          </p>
        ) : null}

        <button type="submit" className="button" data-testid="submit-quote" disabled={submitting || !productCode}>
          {submitting ? 'Calculating...' : 'Get quote'}
        </button>
      </form>

      {quote ? <QuoteResult quote={quote} /> : null}
    </main>
  );
}
