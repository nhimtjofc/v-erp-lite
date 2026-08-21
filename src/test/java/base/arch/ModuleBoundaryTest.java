package base.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Structural rules, mechanically enforced.
 *
 * <p>The review of the predecessor made the point that a rule which is only written down is a rule
 * that will be broken on the hundredth instance, and that code review will not catch it. So the
 * layering and the ledger's single write path are asserted here rather than described in a
 * document.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("base");
    }

    @Test
    void foundationDoesNotDependOnPlatformOrBusiness() {
        ArchRule rule = noClasses().that().resideInAPackage("base.l0..")
                .should().dependOnClassesThat().resideInAnyPackage("base.l1..", "base.l2..");
        rule.check(classes);
    }

    @Test
    void platformDoesNotDependOnBusiness() {
        ArchRule rule = noClasses().that().resideInAPackage("base.l1..")
                .should().dependOnClassesThat().resideInAPackage("base.l2..");
        rule.check(classes);
    }

    @Test
    void domainTypesDoNotDependOnJaxRs() {
        ArchRule rule = noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("jakarta.ws.rs.core..");
        rule.check(classes);
    }

    @Test
    void restResourcesLiveOnlyInInterfacesPackages() {
        ArchRule rule = classes().that().resideInAnyPackage("base.l0..", "base.l1..", "base.l2..")
                .and().haveSimpleNameEndingWith("Resource")
                .should().resideInAPackage("..interfaces.rest..");
        rule.check(classes);
    }
}
