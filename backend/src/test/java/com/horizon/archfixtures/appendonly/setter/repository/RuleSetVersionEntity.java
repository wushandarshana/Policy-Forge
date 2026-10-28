package com.horizon.archfixtures.appendonly.setter.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** VIOLATION: a public setter on an append-only entity. */
@Entity
@Immutable
public class RuleSetVersionEntity {
    private String payload;

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
