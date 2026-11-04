package com.horizon.policyforge.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.horizon.policyforge.repository.PolicyTermEntity;
import com.horizon.policyforge.service.EndorsementService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Only the endorsement service re-prices a pending renewal offer (NFR-08.1, AC-07.7; Sprint 4 security review MINOR-A).
 * {@code PolicyTermEntity.repriceOffer} is the one mutator an unpaid offer has besides {@code markPaid}, so a second
 * caller would be a second way to change what the customer is about to pay. The rule is a factory over the owner class
 * and the allowed caller, so the negative fixture proves it with a stand-in owner and a stand-in endorsement service.
 *
 * <p>The production classes are imported through {@link ArchSupport#production()}, which drops test classes exactly as
 * {@code @AnalyzeClasses(... DoNotIncludeTests)} does. {@code PolicyTermEntityTest} calls {@code repriceOffer} too, and
 * is not production code.
 */
@Tag("NFR-08")
class RepriceOfferCallerArchTest {

    static final String OWNER = PolicyTermEntity.class.getName();
    static final String ALLOWED_CALLER = EndorsementService.class.getName();
    static final String METHOD = "repriceOffer";

    static final String FIXTURE_OWNER = "com.horizon.archfixtures.reprice.repository.OfferTerm";
    static final String FIXTURE_ALLOWED_CALLER = "com.horizon.archfixtures.reprice.service.FixtureEndorsementService";

    /** The classes that may call: the allowed caller and the classes nested in it (a lambda is its own method). */
    private static DescribedPredicate<JavaClass> otherThan(String allowedCaller) {
        return DescribedPredicate.describe("are not " + allowedCaller + " or its nested classes",
                candidate -> !candidate.getName().equals(allowedCaller)
                        && !candidate.getName().startsWith(allowedCaller + "$"));
    }

    /** A call of {@code repriceOffer} on the owner class, or on a subclass of it. */
    private static DescribedPredicate<JavaCall<?>> repricesAnOfferOf(String ownerClass) {
        DescribedPredicate<JavaCall<?>> named = JavaCall.Predicates.target(HasName.Predicates.name(METHOD));
        DescribedPredicate<JavaCall<?>> ownedBy = JavaCall.Predicates.target(HasOwner.Predicates.With
                .owner(JavaClass.Predicates.assignableTo(ownerClass)));
        return named.and(ownedBy).as("call " + ownerClass + "." + METHOD);
    }

    static ArchRule onlyCalledBy(String ownerClass, String allowedCaller) {
        return noClasses().that(otherThan(allowedCaller)).should().callMethodWhere(repricesAnOfferOf(ownerClass))
                .because("NFR-08.1: only " + allowedCaller + " may call " + ownerClass + "." + METHOD
                        + ", the one mutator an unpaid renewal offer has besides markPaid (AC-07.7)");
    }

    /** The rule must not pass for want of a call: the allowed caller really calls the one declared method. */
    static final ArchRule THE_CALL_EXISTS = classes().that().haveFullyQualifiedName(ALLOWED_CALLER)
            .should().callMethodWhere(repricesAnOfferOf(OWNER)).allowEmptyShould(false)
            .because("NFR-08.1: a rename of repriceOffer must not leave the caller rule passing on nothing");

    static final ArchRule THE_METHOD_IS_DECLARED_ONCE = methods().that().haveName(METHOD)
            .should().beDeclaredIn(OWNER).allowEmptyShould(false)
            .because("NFR-08.1: repriceOffer is declared on the term entity and nowhere else in production");

    @Test
    @Tag("NFR-08.1")
    @DisplayName("NFR-08.1: in the production classes only EndorsementService calls PolicyTermEntity.repriceOffer")
    void onlyEndorsementServiceCallsRepriceOffer() {
        onlyCalledBy(OWNER, ALLOWED_CALLER).check(ArchSupport.production());
    }

    @Test
    @Tag("NFR-08.1")
    @DisplayName("NFR-08.1: negative fixture: a class other than the fixture's endorsement service that calls repriceOffer is rejected and named, and the fixture's endorsement service is not")
    void aCallerOtherThanTheEndorsementServiceIsRejected() {
        ArchSupport.assertRuleRejects(onlyCalledBy(FIXTURE_OWNER, FIXTURE_ALLOWED_CALLER),
                ArchSupport.fixtures("reprice"))
                .hasMessageContaining("RepriceCaller")
                .hasMessageContaining("NFR-08.1")
                .hasMessageNotContaining("FixtureEndorsementService.endorse")
                .hasMessageNotContaining("OtherCaller");
    }

    @Test
    @Tag("NFR-08.1")
    @DisplayName("NFR-08.1: the rule is not vacuous, because EndorsementService calls repriceOffer and only PolicyTermEntity declares it")
    void theRuleHasSomethingToGuard() {
        THE_CALL_EXISTS.check(ArchSupport.production());
        THE_METHOD_IS_DECLARED_ONCE.check(ArchSupport.production());
    }
}
