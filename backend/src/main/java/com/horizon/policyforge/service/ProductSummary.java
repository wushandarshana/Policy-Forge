package com.horizon.policyforge.service;

/** One catalog entry (contract C1): a product and the version of it that is in force today. */
public record ProductSummary(String productCode, String productName, String ratingModel, int activeVersion) {
}
