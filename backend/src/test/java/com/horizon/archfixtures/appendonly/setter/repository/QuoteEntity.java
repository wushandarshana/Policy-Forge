package com.horizon.archfixtures.appendonly.setter.repository;

import jakarta.persistence.Entity;
import org.hibernate.annotations.Immutable;

/** VIOLATION: a public setter on QuoteEntity (J11). */
@Entity
@Immutable
public class QuoteEntity {
    private String status;

    public void setStatus(String status) {
        this.status = status;
    }
}
