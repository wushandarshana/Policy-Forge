package com.horizon.archfixtures.appendonly.clean.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** Allowed: immutable, no setters, no public fields. */
@Entity
@Immutable
public class QuoteEntity {
    private String status;

    public String status() {
        return status;
    }
}
