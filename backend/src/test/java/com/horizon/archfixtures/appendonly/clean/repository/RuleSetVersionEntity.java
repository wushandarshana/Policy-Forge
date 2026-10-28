package com.horizon.archfixtures.appendonly.clean.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** Allowed: immutable, no setters, no public fields. */
@Entity
@Immutable
public class RuleSetVersionEntity {
    private String payload;

    public String payload() {
        return payload;
    }
}
