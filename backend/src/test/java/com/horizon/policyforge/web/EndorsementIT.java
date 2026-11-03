package com.horizon.policyforge.web;

import static com.horizon.policyforge.support.ApplicationApi.errorFields;
import static com.horizon.policyforge.support.ApplicationApi.fieldNames;
import static com.horizon.policyforge.support.EndorsementApi.CREATED_FIELDS;
import static com.horizon.policyforge.support.EndorsementApi.ENDORSEMENT_FIELDS;
import static com.horizon.policyforge.support.EndorsementApi.NEW_CITY;
import static com.horizon.policyforge.support.EndorsementApi.NEW_LINE1;
import static com.horizon.policyforge.support.EndorsementApi.NEW_POSTCODE;
import static com.horizon.policyforge.support.EndorsementApi.NOMINEE_NAME;
import static com.horizon.policyforge.support.EndorsementApi.addressBody;
import static com.horizon.policyforge.support.EndorsementApi.nomineeBody;
import static com.horizon.policyforge.support.EndorsementApi.newAddressBody;
import static com.horizon.policyforge.support.EndorsementApi.sumInsuredBody;
import static com.horizon.policyforge.support.PolicyApi.HEADER_FIELDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.horizon.policyforge.support.ApplicationFixtures;
import com.horizon.policyforge.support.EndorsementRows;
import com.horizon.policyforge.support.PolicyRows;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Endorsements through the API (AC-06.1, AC-06.4, AC-06.5, AC-06.6, AC-06.7): {@code POST
 * /api/policies/{number}/endorsements} for an in-force policy, the request-shape and check-order rules (P-5), the
 * history it leaves and the guarantee that nothing can change or delete it. Every policy is issued on 2026-11-01, so its
 * term runs to 2027-10-31.
 */
@Tag("AC-06")
class EndorsementIT extends EndorsementTestSupport {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private static Map<String, Object> body(Object... keysAndValues) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            body.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return body;
    }

    private static Map<String, Object> address(Object line1, Object city, Object postcode) {
        return body("line1", line1, "city", city, "postcode", postcode);
    }

    private static Map<String, Object> nominee(Object name, Object relationship, Object share) {
        return body("name", name, "relationship", relationship, "sharePercent", share);
    }

    // ---- AC-06.1 ----

    @Test
    @DisplayName("AC-06.1: a CHANGE_ADDRESS on an ACTIVE policy gives one endorsement with before version 1 and after version 2; version 2 has the new address, version 1 still has the old one, and the status is ENDORSED")
    void changeAddressAppendsVersionTwoAndEndorsesThePolicy() throws Exception {
        String number = issued("TERM_LIFE");

        MvcResult result = endorsements.endorse("customer1", number, newAddressBody(null));

        assertThat(result.getResponse().getStatus()).as(result.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        JsonNode created = endorsements.bodyOf(result);
        assertThat(fieldNames(created)).as("the C4 key set, and nothing else").isEqualTo(CREATED_FIELDS);
        JsonNode endorsement = created.get("endorsement");
        assertThat(fieldNames(endorsement)).as("the C3 endorsement key set").isEqualTo(ENDORSEMENT_FIELDS);
        assertThat(endorsement.get("sequenceNo").asInt()).isEqualTo(1);
        assertThat(endorsement.get("type").asText()).isEqualTo("CHANGE_ADDRESS");
        assertThat(endorsement.get("beforeVersionNo").asInt()).isEqualTo(1);
        assertThat(endorsement.get("afterVersionNo").asInt()).isEqualTo(2);
        assertThat(endorsement.get("newAnnualPremium").asText()).isEqualTo("6850.00");
        assertThat(endorsement.get("adjustment").asText()).isEqualTo("0.00");
        assertThat(endorsement.get("effectiveDate").asText()).as("the default is today").isEqualTo(TERM_START);
        assertThat(endorsement.get("actorId").asText()).isEqualTo("customer1");
        assertThat(fieldNames(endorsement.get("changes"))).containsExactly("address");
        assertThat(endorsement.get("changes").get("address").get("line1").asText()).isEqualTo(NEW_LINE1);
        JsonNode policy = created.get("policy");
        assertThat(fieldNames(policy)).isEqualTo(HEADER_FIELDS);
        assertThat(policy.get("policyNumber").asText()).isEqualTo(number);
        assertThat(policy.get("status").asText()).isEqualTo("ENDORSED");
        assertThat(policy.get("currentVersionNo").asInt()).isEqualTo(2);
        assertThat(policy.get("endorsementCount").asInt()).isEqualTo(1);
        assertThat(policy.get("effectiveDate").asText()).as("the term does not move").isEqualTo(TERM_START);
        assertThat(policy.get("expiryDate").asText()).isEqualTo(TERM_END);
        assertThat(policy.get("annualPremium").asText()).isEqualTo("6850.00");

        List<Map<String, Object>> rows = EndorsementRows.endorsements(jdbc, number);
        assertThat(rows).as("exactly one endorsement record").hasSize(1);
        assertThat(((Number) rows.get(0).get("BEFORE_VERSION_NO")).intValue()).isEqualTo(1);
        assertThat(((Number) rows.get(0).get("AFTER_VERSION_NO")).intValue()).isEqualTo(2);
        List<Map<String, Object>> versions = PolicyRows.versions(jdbc, number);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0)).as("version 1 still has the old address")
                .containsEntry("ADDRESS_LINE1", ApplicationFixtures.LINE1)
                .containsEntry("ADDRESS_CITY", ApplicationFixtures.CITY)
                .containsEntry("ADDRESS_POSTCODE", ApplicationFixtures.POSTCODE);
        assertThat(versions.get(1)).as("version 2 has the new address")
                .containsEntry("ADDRESS_LINE1", NEW_LINE1)
                .containsEntry("ADDRESS_CITY", NEW_CITY)
                .containsEntry("ADDRESS_POSTCODE", NEW_POSTCODE);
        assertThat((BigDecimal) versions.get(1).get("SUM_INSURED")).isEqualByComparingTo("5000000.00");
        assertThat((BigDecimal) versions.get(1).get("ANNUAL_PREMIUM")).isEqualByComparingTo("6850.00");
        Map<String, Object> policyRow = PolicyRows.policy(jdbc, number);
        assertThat(policyRow).containsEntry("STATUS", "ENDORSED");
        assertThat(((Number) policyRow.get("CURRENT_VERSION_NO")).intValue()).isEqualTo(2);
        List<Map<String, Object>> transitions = PolicyRows.transitions(jdbc, number);
        assertThat(transitions).hasSize(2);
        assertThat(transitions.get(1)).containsEntry("FROM_STATUS", "ACTIVE").containsEntry("TO_STATUS", "ENDORSED")
                .containsEntry("REASON", "CHANGE_ADDRESS").containsEntry("ACTOR_ID", "customer1");
        assertThat(PolicyRows.terms(jdbc, number)).as("term 1 is never changed by an endorsement").hasSize(1);
    }

    @Test
    @DisplayName("AC-06.1: the new version takes the endorsement's effective date and keeps the expiry date, and the policy detail lists both versions with their addresses")
    void versionTwoTakesTheEndorsementDate() throws Exception {
        String number = issued("TERM_LIFE");

        endorsements.endorseOk(number, addressBody("9 Hill Lane", "Nagpur", "440001", "2027-01-15"));

        JsonNode detail = detail(number);
        JsonNode versions = detail.get("versions");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).get("effectiveDate").asText()).isEqualTo(TERM_START);
        assertThat(versions.get(0).get("address").get("line1").asText()).isEqualTo(ApplicationFixtures.LINE1);
        assertThat(versions.get(1).get("versionNo").asInt()).isEqualTo(2);
        assertThat(versions.get(1).get("effectiveDate").asText()).isEqualTo("2027-01-15");
        assertThat(versions.get(1).get("expiryDate").asText()).isEqualTo(TERM_END);
        assertThat(versions.get(1).get("address").get("line1").asText()).isEqualTo("9 Hill Lane");
        assertThat(versions.get(1).get("sumInsured").asText()).isEqualTo("5000000.00");
        assertThat(versions.get(1).get("annualPremium").asText()).isEqualTo("6850.00");
        assertThat(detail.get("endorsements")).hasSize(1);
        assertThat(detail.get("endorsements").get(0).get("effectiveDate").asText()).isEqualTo("2027-01-15");
        assertThat(detail.get("policy").get("status").asText()).isEqualTo("ENDORSED");
        assertThat(detail.get("policy").get("endorsementCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("AC-06.1: a postcode that is not 6 digits gets 400 POSTCODE_INVALID naming address.postcode and writes nothing")
    void badPostcodeIsRefused() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);

        MvcResult result = endorsements.endorse("customer1", number, addressBody("9 Hill Lane", "Nagpur", "4400", null));

        JsonNode problem = problem(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo("POSTCODE_INVALID");
        assertThat(errorFields(problem)).containsExactly("address.postcode");
        assertThat(result.getResponse().getContentAsString()).as("the submitted value is never echoed")
                .doesNotContain("9 Hill Lane").doesNotContain("4400");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.1: a request with an unknown key, a wrong type, a missing payload or a malformed value gets 400 naming the field, with no business code, and writes nothing")
    void shapeErrorsAreRefusedByName() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);
        Map<String, Object> goodAddress = address("1 A Street", "Pune", "411001");
        Map<String, Map<String, Object>> cases = new LinkedHashMap<>();
        cases.put("type", Map.of());
        cases.put("type ", body("type", "SYNTHETIC_TYPE_ALPHA"));
        cases.put("address", body("type", "CHANGE_ADDRESS"));
        cases.put("address.postcode", body("type", "CHANGE_ADDRESS", "address", body("line1", "1 A", "city", "Pune")));
        cases.put("address.line1", body("type", "CHANGE_ADDRESS", "address", address("   ", "Pune", "411001")));
        cases.put("address.city", body("type", "CHANGE_ADDRESS", "address", address("1 A", 7, "411001")));
        cases.put("address.country",
                body("type", "CHANGE_ADDRESS", "address", body("line1", "1 A", "city", "Pune", "postcode", "411001",
                        "country", "IN")));
        cases.put("status", body("type", "CHANGE_ADDRESS", "address", goodAddress, "status", "CANCELLED"));
        cases.put("effectiveDate", body("type", "CHANGE_ADDRESS", "address", goodAddress, "effectiveDate", "01/02/2027"));
        cases.put("nominee", body("type", "ADD_NOMINEE"));
        cases.put("nominee.sharePercent", body("type", "ADD_NOMINEE", "nominee", nominee("Nom One", "SPOUSE", new BigDecimal("25.5"))));
        cases.put("nominee.sharePercent ", body("type", "ADD_NOMINEE", "nominee", nominee("Nom One", "SPOUSE", "25")));
        cases.put("nominee.name", body("type", "ADD_NOMINEE", "nominee", nominee("", "SPOUSE", 25)));
        cases.put("newSumInsured", body("type", "CHANGE_SUM_INSURED"));
        cases.put("newSumInsured ", body("type", "CHANGE_SUM_INSURED", "newSumInsured", 7000000));
        cases.put("newSumInsured  ", body("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.123"));
        cases.put("newBuildingSumInsured",
                body("type", "CHANGE_SUM_INSURED", "newSumInsured", "7000000.00", "newBuildingSumInsured", "1.00"));

        for (Map.Entry<String, Map<String, Object>> entry : cases.entrySet()) {
            MvcResult result = endorsements.endorse("customer1", number, entry.getValue());

            JsonNode problem = problem(result, 400);
            assertThat(errorFields(problem)).as(entry.getValue().toString()).containsExactly(entry.getKey().trim());
            assertThat(problem.has("code")).as("a shape error has no business code").isFalse();
            assertThat(result.getResponse().getContentAsString()).doesNotContain("SYNTHETIC_TYPE_ALPHA");
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.1: a request for a product's other sum-insured key (a building key on TERM_LIFE, newSumInsured on HOUSEHOLD) gets 400 naming it, and writes nothing")
    void otherProductsSumInsuredKeysAreRefused() throws Exception {
        String termLife = issued("TERM_LIFE");
        String household = issued("HOUSEHOLD");
        EndorsementRows.Snapshot before = snapshot(termLife);

        MvcResult onTermLife = endorsements.endorse("customer1", termLife,
                body("type", "CHANGE_SUM_INSURED", "newBuildingSumInsured", "7000000.00"));
        MvcResult onHousehold = endorsements.endorse("customer1", household, sumInsuredBody("7000000.00", null));

        assertThat(errorFields(problem(onTermLife, 400))).containsExactly("newBuildingSumInsured", "newSumInsured");
        assertThat(errorFields(problem(onHousehold, 400))).contains("newSumInsured");
        assertThat(snapshot(termLife)).as("nothing written").isEqualTo(before);
    }

    @Test
    @DisplayName("AC-06.1: the checks run in the P-5 order: a shape error is 400 even for an unknown policy, then an unknown, malformed or another customer's policy is 404 with the fixed detail")
    void checksRunInTheDocumentedOrder() throws Exception {
        String number = issued("TERM_LIFE");
        EndorsementRows.Snapshot before = snapshot(number);

        MvcResult shapeOnUnknown = endorsements.endorse("customer1", "PF-TL-2026-999999", body());
        MvcResult unknown = endorsements.endorse("customer1", "PF-TL-2026-999999", newAddressBody(null));
        MvcResult malformed = endorsements.endorse("customer1", "NOT-A-POLICY", newAddressBody(null));
        MvcResult otherCustomer = endorsements.endorse("customer2", number, newAddressBody(null));
        MvcResult shapeOnOther = endorsements.endorse("customer2", number, body());

        assertThat(errorFields(problem(shapeOnUnknown, 400))).containsExactly("type");
        assertThat(errorFields(problem(shapeOnOther, 400))).as("a shape error reveals nothing about the policy")
                .containsExactly("type");
        for (MvcResult notFound : List.of(unknown, malformed, otherCustomer)) {
            JsonNode problem = problem(notFound, 404);
            assertThat(problem.get("detail").asText()).isEqualTo(NOT_FOUND_DETAIL);
            assertThat(notFound.getResponse().getContentAsString()).doesNotContain(number);
        }
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
    }

    // ---- AC-06.4 and AC-06.5 ----

    @Test
    @DisplayName("AC-06.4: ADD_NOMINEE on a MOTOR policy gets 400 NOMINEE_NOT_SUPPORTED, and nothing is written")
    void nomineeIsNotSupportedOnMotor() throws Exception {
        String number = issued("MOTOR");
        EndorsementRows.Snapshot before = snapshot(number);

        MvcResult result = endorsements.endorse("customer1", number, nomineeBody(NOMINEE_NAME, "SPOUSE", 50, null));

        JsonNode problem = problem(result, 400);
        assertThat(problem.get("code").asText()).isEqualTo("NOMINEE_NOT_SUPPORTED");
        assertThat(errorFields(problem)).containsExactly("nominee");
        assertThat(result.getResponse().getContentAsString()).doesNotContain(NOMINEE_NAME);
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        assertThat(EndorsementRows.endorsements(jdbc, number)).isEmpty();
    }

    @Test
    @DisplayName("AC-06.5: with nominees totalling 80% from two earlier ADD_NOMINEE endorsements, adding 30% gets 400 NOMINEE_SHARE_EXCEEDED and writes nothing, while 10% is accepted and a fourth nominee gets NOMINEE_LIMIT_REACHED")
    void nomineeShareAndCountAreLimited() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, nomineeBody("Nominee One", "SPOUSE", 50, null));
        endorsements.endorseOk(number, nomineeBody("Nominee Two", "CHILD", 30, null));
        EndorsementRows.Snapshot beforeTooMuch = snapshot(number);

        MvcResult tooMuch = endorsements.endorse("customer1", number, nomineeBody("Nominee Three", "PARENT", 30, null));

        JsonNode problem = problem(tooMuch, 400);
        assertThat(problem.get("code").asText()).isEqualTo("NOMINEE_SHARE_EXCEEDED");
        assertThat(errorFields(problem)).containsExactly("nominee.sharePercent");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(beforeTooMuch);

        JsonNode third = endorsements.endorseOk(number, nomineeBody("Nominee Three", "PARENT", 10, null));
        assertThat(third.get("endorsement").get("sequenceNo").asInt()).isEqualTo(3);
        assertThat(third.get("endorsement").get("changes").get("nominee").get("sharePercent").asInt()).isEqualTo(10);
        EndorsementRows.Snapshot beforeFourth = snapshot(number);

        MvcResult fourth = endorsements.endorse("customer1", number, nomineeBody("Nominee Four", "SIBLING", 5, null));

        JsonNode limit = problem(fourth, 400);
        assertThat(limit.get("code").asText()).isEqualTo("NOMINEE_LIMIT_REACHED");
        assertThat(errorFields(limit)).containsExactly("nominee");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(beforeFourth);

        JsonNode versions = detail(number).get("versions");
        assertThat(versions).as("versions 1 to 4").hasSize(4);
        assertThat(versions.get(0).get("nominees")).isEmpty();
        assertThat(versions.get(1).get("nominees")).hasSize(1);
        assertThat(versions.get(3).get("nominees")).as("the nominees accumulate version by version").hasSize(3);
        assertThat(versions.get(3).get("nominees").get(2).get("name").asText()).isEqualTo("Nominee Three");
    }

    @Test
    @DisplayName("AC-06.5: a nominee that brings the total to exactly 100% is accepted, and a relationship outside the five values gets NOMINEE_RELATIONSHIP_INVALID")
    void totalOfOneHundredIsAllowedAndRelationshipsAreChecked() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, nomineeBody("Nominee One", "SPOUSE", 80, null));

        MvcResult badRelationship = endorsements.endorse("customer1", number,
                nomineeBody("Nominee Two", "NEIGHBOUR", 10, null));
        JsonNode exact = endorsements.endorseOk(number, nomineeBody("Nominee Two", "OTHER", 20, null));

        JsonNode problem = problem(badRelationship, 400);
        assertThat(problem.get("code").asText()).isEqualTo("NOMINEE_RELATIONSHIP_INVALID");
        assertThat(errorFields(problem)).containsExactly("nominee.relationship");
        assertThat(exact.get("endorsement").get("sequenceNo").asInt()).isEqualTo(2);
    }

    // ---- AC-06.6 ----

    @Test
    @DisplayName("AC-06.6: three endorsements get sequenceNo 1, 2 and 3, and the detail returns them in order linked to before versions 1, 2, 3 and after versions 2, 3, 4 with versions 1 to 4 listed")
    void threeEndorsementsAreNumberedAndLinked() throws Exception {
        String number = issued("TERM_LIFE");

        JsonNode first = endorsements.endorseOk(number, addressBody("1 First Road", "Pune", "411001", "2027-01-01"));
        JsonNode second = endorsements.endorseOk(number, nomineeBody("Nominee One", "SPOUSE", 40, "2027-01-01"));
        JsonNode third = endorsements.endorseOk(number, addressBody("3 Third Road", "Pune", "411001", "2027-02-01"));

        assertThat(List.of(first, second, third).stream()
                .map(created -> created.get("endorsement").get("sequenceNo").asInt()).toList())
                .containsExactly(1, 2, 3);
        assertThat(third.get("policy").get("endorsementCount").asInt()).isEqualTo(3);
        assertThat(third.get("policy").get("currentVersionNo").asInt()).isEqualTo(4);
        JsonNode detail = detail(number);
        List<Integer> sequence = new ArrayList<>();
        List<Integer> before = new ArrayList<>();
        List<Integer> after = new ArrayList<>();
        detail.get("endorsements").forEach(endorsement -> {
            sequence.add(endorsement.get("sequenceNo").asInt());
            before.add(endorsement.get("beforeVersionNo").asInt());
            after.add(endorsement.get("afterVersionNo").asInt());
        });
        assertThat(sequence).containsExactly(1, 2, 3);
        assertThat(before).containsExactly(1, 2, 3);
        assertThat(after).containsExactly(2, 3, 4);
        List<Integer> versionNumbers = new ArrayList<>();
        detail.get("versions").forEach(version -> versionNumbers.add(version.get("versionNo").asInt()));
        assertThat(versionNumbers).containsExactly(1, 2, 3, 4);
        List<String> statuses = new ArrayList<>();
        detail.get("transitions").forEach(transition -> statuses.add(transition.get("toStatus").asText()));
        assertThat(statuses).containsExactly("ACTIVE", "ENDORSED", "ENDORSED", "ENDORSED");
        assertThat(detail.get("endorsements").get(1).get("changes").get("nominee").get("name").asText())
                .isEqualTo("Nominee One");
        assertThat(fieldNames(detail.get("endorsements").get(0))).isEqualTo(ENDORSEMENT_FIELDS);
    }

    @Test
    @DisplayName("AC-06.6: an effective date before the latest endorsement's gets 400 EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT, a date outside the term gets EFFECTIVE_DATE_OUT_OF_TERM, both naming effectiveDate, and nothing is written")
    void effectiveDateWindowIsChecked() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, addressBody("1 First Road", "Pune", "411001", "2027-02-01"));
        EndorsementRows.Snapshot before = snapshot(number);

        MvcResult beforeLast = endorsements.endorse("customer1", number, newAddressBody("2027-01-31"));
        MvcResult afterTerm = endorsements.endorse("customer1", number, newAddressBody("2027-11-01"));
        MvcResult beforeTerm = endorsements.endorse("customer1", number, newAddressBody("2026-10-31"));

        JsonNode last = problem(beforeLast, 400);
        assertThat(last.get("code").asText()).isEqualTo("EFFECTIVE_DATE_BEFORE_LAST_ENDORSEMENT");
        assertThat(errorFields(last)).containsExactly("effectiveDate");
        JsonNode outAfter = problem(afterTerm, 400);
        assertThat(outAfter.get("code").asText()).isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");
        assertThat(errorFields(outAfter)).containsExactly("effectiveDate");
        JsonNode outBefore = problem(beforeTerm, 400);
        assertThat(outBefore.get("code").asText()).as("the first violation in E-order").isEqualTo("EFFECTIVE_DATE_OUT_OF_TERM");
        assertThat(errorFields(outBefore)).as("every violation is listed").containsExactly("effectiveDate", "effectiveDate");
        assertThat(snapshot(number)).as("nothing written").isEqualTo(before);
        assertThat(endorsements.endorseOk(number, newAddressBody("2027-02-01")).get("endorsement").get("sequenceNo")
                .asInt()).as("the same date as the latest endorsement is allowed").isEqualTo(2);
    }

    // ---- AC-06.7 ----

    @Test
    @DisplayName("AC-06.7: no PUT, PATCH or DELETE handler mapping exists for any /api path, and no mapping answers every method")
    void noMutatingVerbIsMappedUnderApi() {
        Set<String> verbs = Set.of("PUT", "PATCH", "DELETE");
        List<String> offenders = new ArrayList<>();
        int endorsementPosts = 0;
        for (RequestMappingInfo info : handlerMapping.getHandlerMethods().keySet()) {
            if (info.getPathPatternsCondition() == null) {
                continue;
            }
            Set<String> patterns = info.getPathPatternsCondition().getPatternValues();
            if (patterns.stream().noneMatch(pattern -> pattern.startsWith("/api"))) {
                continue;
            }
            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty() || methods.stream().anyMatch(method -> verbs.contains(method.name()))) {
                offenders.add(info.toString());
            }
            if (methods.contains(RequestMethod.POST) && patterns.stream().anyMatch(p -> p.contains("/endorsements"))) {
                endorsementPosts++;
            }
        }

        assertThat(offenders).as("mappings that could change or delete a record").isEmpty();
        assertThat(endorsementPosts).as("the endorse and preview POST mappings exist").isEqualTo(2);
    }

    @Test
    @DisplayName("AC-06.7: the owner's PUT, PATCH and DELETE requests to the endorsement paths do not succeed (405 or 404), and the stored endorsement is unchanged")
    void endorsementsCannotBeChangedOrDeleted() throws Exception {
        String number = issued("TERM_LIFE");
        endorsements.endorseOk(number, newAddressBody(null));
        EndorsementRows.Snapshot before = snapshot(number);
        List<Map<String, Object>> rowsBefore = EndorsementRows.endorsements(jdbc, number);

        List<String> paths = List.of("/api/policies/" + number + "/endorsements",
                "/api/policies/" + number + "/endorsements/preview", "/api/policies/" + number + "/endorsements/1",
                "/api/policies/" + number);
        for (String path : paths) {
            for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
                MvcResult result = endorsements.send(method, path, "customer1", newAddressBody(null));

                assertThat(result.getResponse().getStatus()).as(method + " " + path).isIn(404, 405);
            }
        }

        assertThat(snapshot(number)).as("nothing changed").isEqualTo(before);
        assertThat(EndorsementRows.endorsements(jdbc, number)).as("the endorsement row is identical")
                .isEqualTo(rowsBefore);
    }
}
