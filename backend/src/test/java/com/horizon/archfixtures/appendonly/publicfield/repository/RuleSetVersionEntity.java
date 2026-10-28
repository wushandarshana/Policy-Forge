package com.horizon.archfixtures.appendonly.publicfield.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** VIOLATION: a public mutable field on an append-only entity. */
@Entity
@Immutable
public class RuleSetVersionEntity {
    public String payload;
}
