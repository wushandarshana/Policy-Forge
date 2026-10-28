package com.horizon.policyforge.service;

/** Thrown by services when a requested resource does not exist. The web layer maps it to 404 problem+json. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
