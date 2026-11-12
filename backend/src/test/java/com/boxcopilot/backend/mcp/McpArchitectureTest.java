package com.boxcopilot.backend.mcp;

import com.boxcopilot.backend.BoxCopilotApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The package boundary of section 2 of the design: only {@code mcp} uses {@code mcp}, and {@code mcp} uses
 * services but never controllers.
 *
 * <p>The first rule is what keeps the feature removable and keeps the flag honest: every MCP class is
 * conditional on {@code app.mcp.enabled}, so a reference from the rest of the application would either break
 * with the flag off or quietly drag MCP code into a path that is supposed to have none. Where the application
 * does have to talk to MCP - revoking connections when an account loses access, or the pending-authorization
 * cookie - it does so through neutral ports declared outside this package, which the second test names.
 *
 * <p>The second rule keeps the direction of the layering: the MCP tools reach the same data as the SPA through
 * the services, and never by borrowing an HTTP controller whose contract, validation and session semantics
 * belong to another chain.
 */
class McpArchitectureTest {

    private static final String MCP = "com.boxcopilot.backend.mcp..";
    private static final String ROOT = "com.boxcopilot.backend";

    /** Production classes only: the tests deliberately reach into {@code mcp} and are not part of the rule. */
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_JARS)
            .importPackagesOf(BoxCopilotApplication.class);

    @Test
    void theRestOfTheApplicationNeverReachesIntoTheMcpPackage() {
        assertThat(PRODUCTION_CLASSES).as("production classes were imported").isNotEmpty();

        noClasses().that().resideOutsideOfPackage(MCP)
                .and().resideInAPackage(ROOT + "..")
                .should().dependOnClassesThat().resideInAPackage(MCP)
                .because("MCP is an optional feature behind app.mcp.enabled; the application talks to it only "
                        + "through neutral ports declared outside this package")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void theMcpPackageUsesServicesAndNeverControllers() {
        noClasses().that().resideInAPackage(MCP)
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".controller..")
                .because("MCP reads through the services, not through the HTTP controllers of the application chain")
                .check(PRODUCTION_CLASSES);
    }

    /**
     * The permitted direction, asserted rather than assumed: without this the two rules above would also be
     * satisfied by an MCP package that talks to nothing at all, and the boundary would say nothing about the
     * layering the design asks for.
     */
    @Test
    void theMcpPackageActuallyReachesTheServicesRepositoriesAndDomain() {
        assertThat(PRODUCTION_CLASSES.stream()
                .filter(javaClass -> javaClass.getPackageName().startsWith(ROOT + ".mcp"))
                .flatMap(javaClass -> javaClass.getDirectDependenciesFromSelf().stream())
                .map(dependency -> dependency.getTargetClass().getPackageName())
                .distinct())
                .as("packages the MCP package reads from")
                .contains(ROOT + ".service", ROOT + ".repository", ROOT + ".domain");
    }
}
