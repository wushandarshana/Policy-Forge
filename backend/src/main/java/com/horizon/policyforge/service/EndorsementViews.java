package com.horizon.policyforge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.horizon.policyforge.domain.endorsement.Address;
import com.horizon.policyforge.domain.endorsement.EndorsementCommand;
import com.horizon.policyforge.domain.endorsement.Nominee;
import com.horizon.policyforge.repository.EndorsementEntity;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * The two ways an endorsement's payload is turned into text and back (P-9): the normalised payload that is stored, and
 * the {@code changes} object of the response, which is built per type from an allow-list and never copied from the
 * stored JSON. The stored payload holds the address or the nominee (personal data) and is never logged.
 */
@Component
class EndorsementViews {

    /** The wire keys of a sum-insured change, in the order they are written. */
    private static final List<String> SUM_INSURED_KEYS =
            List.of("newSumInsured", "newBuildingSumInsured", "newContentsSumInsured");

    private final ObjectMapper mapper;

    EndorsementViews(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** The normalised payload of a validated command, as stored: the wire keys of its type only. */
    String payloadJson(EndorsementCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        switch (command.type()) {
            case CHANGE_ADDRESS -> {
                Map<String, Object> address = new LinkedHashMap<>();
                address.put("line1", command.address().line1());
                address.put("city", command.address().city());
                address.put("postcode", command.address().postcode());
                payload.put("address", address);
            }
            case ADD_NOMINEE -> {
                Map<String, Object> nominee = new LinkedHashMap<>();
                nominee.put("name", command.nominee().name());
                nominee.put("relationship", command.nominee().relationship());
                nominee.put("sharePercent", command.nominee().sharePercent());
                payload.put("nominee", nominee);
            }
            case CHANGE_SUM_INSURED -> {
                Map<String, String> byWireKey = new LinkedHashMap<>();
                command.newSumInsured().forEach((component, amount) ->
                        byWireKey.put(wireKey(component), amount.toPlainString()));
                SUM_INSURED_KEYS.stream().filter(byWireKey::containsKey)
                        .forEach(key -> payload.put(key, byWireKey.get(key)));
            }
        }
        try {
            return mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise an endorsement payload", e);
        }
    }

    /** The response view of a stored endorsement, with its {@code changes} taken from the allow-list for its type. */
    PolicyDetail.EndorsementView view(EndorsementEntity endorsement) {
        return new PolicyDetail.EndorsementView(endorsement.getSequenceNo(), endorsement.getType().name(),
                changes(endorsement), endorsement.getBeforeVersionNo(), endorsement.getAfterVersionNo(),
                endorsement.getNewAnnualPremium(), endorsement.getAdjustment(), endorsement.getEffectiveDate(),
                endorsement.getActorId(), endorsement.getCreatedAt());
    }

    private Map<String, Object> changes(EndorsementEntity endorsement) {
        JsonNode payload = read(endorsement.getPayloadJson());
        Map<String, Object> changes = new LinkedHashMap<>();
        switch (endorsement.getType()) {
            case CHANGE_ADDRESS -> {
                JsonNode address = payload.path("address");
                changes.put("address", new Address(address.path("line1").asText(), address.path("city").asText(),
                        address.path("postcode").asText()));
            }
            case ADD_NOMINEE -> {
                JsonNode nominee = payload.path("nominee");
                changes.put("nominee", new Nominee(nominee.path("name").asText(),
                        nominee.path("relationship").asText(), nominee.path("sharePercent").asInt()));
            }
            case CHANGE_SUM_INSURED -> SUM_INSURED_KEYS.stream().filter(payload::has)
                    .forEach(key -> changes.put(key, new BigDecimal(payload.get(key).asText()).toPlainString()));
        }
        return changes;
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored endorsement data cannot be read", e);
        }
    }

    /** The risk-input name of a sum-insured component, as the request key that replaces it. */
    private static String wireKey(String component) {
        return switch (component) {
            case "buildingSumInsured" -> "newBuildingSumInsured";
            case "contentsSumInsured" -> "newContentsSumInsured";
            default -> "newSumInsured";
        };
    }
}
