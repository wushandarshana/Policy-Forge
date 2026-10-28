# Feature Spec — Renewal, Lapse & Cancellation

**Parent:** [`app_spec.md`](app_spec.md) · **ACs:** AC-07, AC-08 (plus AC-10 for state) · **NFR:** NFR-01, NFR-02
**Domain classes:** `RenewalPolicy`, `GracePeriodPolicy`, `ProRataRefundCalculator`, `RefundQuote`

## Renewal cycle (AC-07)

`RenewalScheduler` runs end of day (`@Scheduled(cron = "0 0 23 * * *")`), and on demand through
`POST /api/admin/jobs/renewal-cycle?date=YYYY-MM-DD` (ADMIN) for testing. The job is idempotent for a given date.
For each in-force policy with a current term:

1. **Offer.** If `runDate >= termEnd − renewalOfferDaysBeforeExpiry` and no next term exists, create a `PolicyTerm`
   (`start = termEnd + 1`, `end = start + termMonths − 1 day`, `paid = false`) with a **refreshed premium**: re-quote the
   current risk inputs with the **currently active** rule version. The renewal term records that version.
2. **Lapse.** If the next term is unpaid and `runDate > termEnd + gracePeriodDays`, transition the policy to **LAPSED**
   (reason `GRACE_PERIOD_EXPIRED`, actor `system:renewal-scheduler`).

Paying: `POST /api/policies/{number}/renewal/pay` (owner) marks the pending term paid (a simulated payment record) and
transitions the policy to **RENEWED**. Payment is allowed from offer creation until `termEnd + gracePeriodDays`
inclusive. After that, 409.

`GracePeriodPolicy.isLapsed(termEnd, graceDays, runDate, paid)` is a pure function:
lapsed ⇔ `!paid && runDate.isAfter(termEnd.plusDays(graceDays))`.

## Cancellation (AC-08)

`POST /api/policies/{number}/cancel` (owner), body `{ "cancellationDate": "2027-02-09", "reason": "NO_LONGER_NEEDED" }`.
The date is optional and defaults to today, and must be within the current paid term.

`ProRataRefundCalculator.calculate(premium, termStart, termEnd, cancellationDate, policyEffectiveDate, firstTerm, coolingOffDays, cancellationFee)`:

- `termDays = DAYS.between(termStart, termEnd + 1)`
- `unexpiredDays = DAYS.between(cancellationDate, termEnd + 1)`
- If `firstTerm && DAYS.between(policyEffectiveDate, cancellationDate) < coolingOffDays`: `refund = premium` (full,
  no fee). Renewal terms never get cooling-off.
- Otherwise: `refund = max(0, premium × unexpiredDays / termDays − cancellationFee)`, intermediate scale 10, final
  HALF_UP 2 dp.
- `premium` is the current term's annual premium plus any endorsement adjustments in that term.

Effects, in one transaction: append a `RefundRecord` (policyNumber, termNo, amount, unexpiredDays, termDays, fee,
formula string, createdAt, actorId) and the transition → **CANCELLED**. Refund records are append-only.

## Acceptance Criteria

- **AC-07.1** Given an ACTIVE TERM_LIFE policy with term 2026-11-01..2027-10-31 and `renewalOfferDaysBeforeExpiry = 30`,
  when the renewal cycle runs on 2027-10-01, then a renewal term 2027-11-01..2028-10-31 is created, unpaid, with
  premium from the active rule version.
- **AC-07.2** Given the AC-07.1 offer, when the cycle runs again on 2027-10-01, then no duplicate term is created
  (idempotent).
- **AC-07.3** Given TERM_LIFE v2 active with higher rates when the offer is generated, then the renewal premium uses v2
  (refreshed) while the current term keeps v1.
- **AC-07.4** Given an unpaid renewal and grace 30 days, when the cycle runs on 2027-11-30 (termEnd + 30), then the
  policy is still in force. When it runs on 2027-12-01, then the policy is **LAPSED** with reason
  `GRACE_PERIOD_EXPIRED`.
- **AC-07.5** Given an offered renewal, when the owner pays on 2027-11-10 (within grace), then the term is paid and the
  status is **RENEWED**. Paying on 2027-12-01 gives 409.
- **AC-07.6** Grace periods differ by product: MOTOR and HOUSEHOLD lapse on termEnd + 16, TERM_LIFE on termEnd + 31.
- **AC-08.1** Given TERM_LIFE premium 6850.00, term 2026-11-01..2027-10-31 (365 days), cancellation on 2027-02-09
  (unexpired 265 days), fee 500.00, then the refund is **4473.29**.
- **AC-08.2** Given cancellation within the cooling-off period (TERM_LIFE, 10 days after effective date), then the
  refund equals the full premium 6850.00 and no fee is applied.
- **AC-08.3** Given cancellation on the last day of term (unexpired 1 day) with a fee greater than the pro-rata amount,
  then the refund is **0.00**, never negative.
- **AC-08.4** Given a successful cancellation, then the status is CANCELLED, exactly one `RefundRecord` exists, and a
  second cancel request gives 409 with no second refund record.
- **AC-08.5** Refund calculation uses only `BigDecimal`. A property test across 1,000 random dates shows
  `0 ≤ refund ≤ premium` with scale exactly 2.

## UI
Policy detail has **Renew** (when an offer is pending: shows the new premium, then Pay) and **Cancel** (shows a refund
preview from `GET /api/policies/{number}/cancel/preview?date=`, then confirm).
