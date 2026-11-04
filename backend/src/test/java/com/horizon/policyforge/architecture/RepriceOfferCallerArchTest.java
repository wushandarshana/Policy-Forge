package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.service.EndorsementService;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Only the endorsement service re-prices a pending renewal offer (NFR-08.1, AC-07.7; Sprint 4 security review MINOR-A).
 * {@code PolicyTermEntity.repriceOffer} is the one mutator an unpaid offer has besides {@code markPaid}, so a second
 * caller would be a second way to change what the customer is about to pay. The rule is a factory over the owner class
 * and the allowed caller, so the negative fixture proves it with a stand-in owner and a stand-in endorsement service.
 *
 * <p>Plan P-3 red step: the factory below is a STUB that allows every caller, so the negative-fixture test fails on its
 * assertion. The green commit replaces it with the real rule.
 */
@Tag("NFR-08")
class RepriceOfferCallerArchTest {

    static final String OWNER = PolicyTermEntity.class.getName();
    static final String ALLOWED_CALLER = EndorsementService.class.getName();
    static final String METHOD = "repriceOffer";

    static final String FIXTURE_OWNER = "com.horizon.archfixtures.reprice.repository.OfferTerm";
    static final String FIXTURE_ALLOWED_CALLER = "com.horizon.archfixtures.reprice.service.FixtureEndorsementService";

    /** STUB (red): every caller is allowed. */
    static ArchRule onlyCalledBy(String ownerClass, String allowedCaller) {
        return classes().should(new ArchCondition<JavaClass>("allow every caller (stub)") {
            @Override
            public void check(JavaClass item, ConditionEvents events) {
            }
        }).because("NFR-08.1: only " + allowedCaller + " may call " + ownerClass + "." + METHOD);
    }

    @Test
    @Tag("NFR-08.1")
    @DisplayName("NFR-08.1: in the production classes only EndorsementService calls PolicyTermEntity.repriceOffer")
    void onlyEndorsementServiceCallsRepriceOffer() {
        onlyCalledBy(OWNER, ALLOWED_CALLER).check(ArchSupport.production());
    }

    @Test
    @Tag("NFR-08.1")
    @DisplayName("NFR-08.1 negative fixture: a class other than the fixture's endorsement service that calls repriceOffer is rejected and named, and the fixture's endorsement service is not")
    void aCallerOtherThanTheEndorsementServiceIsRejected() {
        ArchSupport.assertRuleRejects(onlyCalledBy(FIXTURE_OWNER, FIXTURE_ALLOWED_CALLER),
                ArchSupport.fixtures("reprice"))
                .hasMessageContaining("RepriceCaller")
                .hasMessageContaining("NFR-08.1")
                .hasMessageNotContaining("FixtureEndorsementService.endorse")
                .hasMessageNotContaining("OtherCaller");
    }
}
