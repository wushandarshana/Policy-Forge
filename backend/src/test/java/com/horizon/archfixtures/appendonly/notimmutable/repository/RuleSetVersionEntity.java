package com.horizon.archfixtures.appendonly.notimmutable.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** VIOLATION: an append-only entity that is not @Immutable. */
@Entity

public class RuleSetVersionEntity {
    private String payload;

    public String payload() {
        return payload;
    }
}
