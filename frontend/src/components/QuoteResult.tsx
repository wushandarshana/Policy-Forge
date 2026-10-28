import type { Quote } from '../api';

/**
 * Shows a quote exactly as received. Every money value is rendered from the API's decimal string with no
 * conversion, so "6850.00" is shown as "6850.00".
 */
export function QuoteResult({ quote }: { quote: Quote }) {
  return (
    <section className="result" aria-labelledby="quote-result-heading" data-testid="quote-result">
      <h2 id="quote-result-heading">Your quote</h2>

      <dl className="summary">
        <div>
          <dt>Annual premium</dt>
          <dd className="premium" data-testid="quote-premium">
            {quote.annualPremium}
          </dd>
        </div>
        <div>
          <dt>Sum insured</dt>
          <dd data-testid="quote-sum-insured">{quote.sumInsured}</dd>
        </div>
        <div>
          <dt>Rule version</dt>
          <dd data-testid="quote-rule-version">{quote.ruleVersion}</dd>
        </div>
        <div>
          <dt>Valid until</dt>
          <dd data-testid="quote-valid-until">{quote.validUntil}</dd>
        </div>
        <div>
          <dt>Quote ID</dt>
          <dd className="quote-id" data-testid="quote-id">
            {quote.quoteId}
          </dd>
        </div>
      </dl>

      <h3>How this premium was calculated</h3>
      <ul className="breakdown" data-testid="quote-breakdown">
        {quote.breakdown.map((item, index) => (
          <li key={`${item.factor}-${index}`} className="breakdown-row" data-testid={`breakdown-row-${item.factor}`}>
            <span className="breakdown-factor">{item.factor}</span>
            <span className="breakdown-value" data-testid={`breakdown-value-${item.factor}`}>
              {item.value}
            </span>
          </li>
        ))}
      </ul>

      <a
        className="button apply"
        data-testid="quote-apply"
        data-quote-id={quote.quoteId}
        href={`#/apply?quoteId=${encodeURIComponent(quote.quoteId)}`}
      >
        Apply
      </a>
    </section>
  );
}
