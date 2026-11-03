package com.horizon.policyforge.service;

import com.horizon.policyforge.repository.PolicyNumberSequenceEntity;
import com.horizon.policyforge.repository.PolicyNumberSequenceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the counter of a product and issue year the first time a policy is issued for it. It runs in its own
 * transaction so that two first issues racing for the same counter cannot poison the issuing transaction: the loser's
 * insert fails on the unique key inside this transaction only, and {@link PolicyNumberGenerator} then reads the winner's
 * row. Normal issues never reach it, because the counter already exists.
 */
@Component
public class PolicyNumberSequenceInitializer {

    private final PolicyNumberSequenceRepository sequences;

    public PolicyNumberSequenceInitializer(PolicyNumberSequenceRepository sequences) {
        this.sequences = sequences;
    }

    /**
     * Inserts a counter starting at zero and commits it at once.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when another transaction created it first
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(String productCode, int year) {
        sequences.saveAndFlush(PolicyNumberSequenceEntity.startAt(productCode, year));
    }
}
