package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/** Append-only access to underwriting decisions (NFR-02): {@code save} and finders only, no delete, no update. */
public interface UnderwritingDecisionRepository extends Repository<UnderwritingDecisionEntity, Long> {

    UnderwritingDecisionEntity save(UnderwritingDecisionEntity entity);

    /** Every decision of one application, oldest first. */
    List<UnderwritingDecisionEntity> findByApplicationIdOrderByIdAsc(String applicationId);

    /** The decisions of the given source (a {@code DecisionSource} name) for several applications, oldest first. */
    List<UnderwritingDecisionEntity> findByApplicationIdInAndSourceOrderByIdAsc(
            Collection<String> applicationIds, String source);
}
