package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.underwriting.ReasonCode;
import java.util.List;

/**
 * Reads and writes the {@code reasons_json} column of an underwriting decision: a JSON array of {@code {code,
 * description}}, in the order of the reason codes (U-8, U-9). One place for the format, shared by the use cases that
 * append and read decision rows.
 */
final class ReasonsJson {

    private static final TypeReference<List<ReasonCode>> REASONS = new TypeReference<>() {
    };

    private ReasonsJson() {
    }

    static String write(ObjectMapper mapper, List<ReasonCode> reasons) {
        try {
            return mapper.writeValueAsString(reasons);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise decision reasons", e);
        }
    }

    static List<ReasonCode> read(ObjectMapper mapper, String json) {
        try {
            return mapper.readValue(json, REASONS);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored decision reasons cannot be read", e);
        }
    }
}
