package com.horizon.policyforge.service;

import com.horizon.policyforge.domain.policy.PolicyNumberFormat;
import com.horizon.policyforge.repository.PolicyNumberSequenceEntity;
import com.horizon.policyforge.repository.PolicyNumberSequenceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Allocates policy numbers (AC-05.1, AC-05.2): the next sequence for a product and issue year, formatted by the domain
 * {@link PolicyNumberFormat}. It must run inside the issuing transaction: it locks the counter row until that
 * transaction ends, so concurrent issues take their numbers one at a time, and if the issue rolls back the number goes
 * back with it. Numbers of one product and year are therefore unique and contiguous.
 */
@Service
public class PolicyNumberGenerator {

    private final PolicyNumberSequenceRepository sequences;
    private final PolicyNumberSequenceInitializer initializer;

    public PolicyNumberGenerator(PolicyNumberSequenceRepository sequences, PolicyNumberSequenceInitializer initializer) {
        this.sequences = sequences;
        this.initializer = initializer;
    }

    /**
     * The next policy number of {@code productCode} in {@code year} (the issue date's year, P-13).
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException when no transaction is running
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String next(String productCode, int year) {
        PolicyNumberSequenceEntity sequence = sequences.findByProductCodeAndYear(productCode, year)
                .orElseGet(() -> createThenLock(productCode, year));
        return PolicyNumberFormat.format(productCode, year, sequence.allocateNext());
    }

    /** The first issue of a product in a year: create the counter in its own transaction, then lock it. */
    private PolicyNumberSequenceEntity createThenLock(String productCode, int year) {
        try {
            initializer.create(productCode, year);
        } catch (DataIntegrityViolationException createdByAnotherIssue) {
            // Another issue created the counter at the same moment; its committed row is read below.
        }
        return sequences.findByProductCodeAndYear(productCode, year).orElseThrow(
                () -> new IllegalStateException("The policy number counter could not be created"));
    }
}
