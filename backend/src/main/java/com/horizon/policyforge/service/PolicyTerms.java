package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.renewal.RenewalPolicy;
import com.horizon.policyforge.domain.renewal.TermPeriod;
import com.horizon.policyforge.repository.PolicyTermEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Which term of a policy is which (plan G-5), decided by the domain {@link RenewalPolicy} and mapped back onto the
 * stored terms. The current paid term is the paid term with the highest number: term 1 until a renewal is paid, and the
 * renewal term from the moment it is paid, even before it starts. An unpaid offer changes nothing. The policy header,
 * the endorsement window and basis, the renewal cycle and the renewal payment all use it.
 */
@Component
class PolicyTerms {

    private final RenewalPolicy renewal;

    PolicyTerms(RenewalPolicy renewal) {
        this.renewal = renewal;
    }

    static TermPeriod period(PolicyTermEntity term) {
        return new TermPeriod(term.getTermNo(), term.getStartDate(), term.getEndDate(), term.isPaid());
    }

    List<TermPeriod> periods(Collection<PolicyTermEntity> terms) {
        return terms.stream().map(PolicyTerms::period).toList();
    }

    /** The paid term with the highest number, if any. */
    Optional<PolicyTermEntity> currentPaid(Collection<PolicyTermEntity> terms) {
        return renewal.currentPaidTerm(periods(terms)).flatMap(period -> find(terms, period));
    }

    /** The current paid term; every policy has one, because term 1 is created paid at issuance. */
    PolicyTermEntity requireCurrentPaid(Collection<PolicyTermEntity> terms, String policyNumber) {
        return currentPaid(terms).orElseThrow(() -> new IllegalStateException("Policy has no paid term: policyNumber="
                + policyNumber));
    }

    /** The lowest-numbered unpaid term above the current paid term: the offer waiting to be paid, if any. */
    Optional<PolicyTermEntity> pending(Collection<PolicyTermEntity> terms) {
        return renewal.pendingTerm(periods(terms)).flatMap(period -> find(terms, period));
    }

    /** The last day the pending renewal can be paid: the current paid term's end plus its grace days. */
    LocalDate payableUntil(PolicyTermEntity currentPaid, int graceDays) {
        return renewal.payableUntil(currentPaid.getEndDate(), graceDays);
    }

    private static Optional<PolicyTermEntity> find(Collection<PolicyTermEntity> terms, TermPeriod period) {
        return terms.stream().filter(term -> term.getTermNo() == period.termNo()).findFirst();
    }
}
