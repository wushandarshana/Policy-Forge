package com.horizon.policyforge.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/** Append-only access to stored quotes (NFR-02): {@code save} and finders only, no delete, no update. */
public interface QuoteRepository extends Repository<QuoteEntity, Long> {

    QuoteEntity save(QuoteEntity entity);

    Optional<QuoteEntity> findByQuoteId(String quoteId);

    /** The quotes with any of the given IDs, in no particular order. */
    List<QuoteEntity> findByQuoteIdIn(Collection<String> quoteIds);
}
