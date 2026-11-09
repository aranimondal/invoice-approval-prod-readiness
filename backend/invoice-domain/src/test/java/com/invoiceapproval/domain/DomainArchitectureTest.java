package com.invoiceapproval.domain;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Structural rule the target design depends on (docs/architecture/as-built.md, ADR-001).
 *
 * <p>The domain holds business rules only. It may use the core JDK value types and nothing else:
 * no Spring, no Jakarta, no JDBC, no java.net, no AWS or Azure SDK, no adapter code.</p>
 *
 * <p>Why an allow-list and not a deny-list: a deny-list only blocks what someone thought of last
 * quarter. And why ArchUnit at all when Maven already keeps Spring off the domain's classpath:
 * Maven can't stop {@code java.net.http.HttpClient} or {@code java.sql.Connection}. Both ship with
 * the JDK. Scope: this rule keeps transport out of the domain. It would not have caught INC-2041,
 * whose per-call HttpClient lived in the PayEx adapter; that is guarded by the single shared
 * client bean in ADR-001.</p>
 *
 * <p>Run: {@code ./backend/mvnw -f backend/pom.xml -pl invoice-domain test -Dtest=DomainArchitectureTest}</p>
 */
@AnalyzeClasses(packages = "com.invoiceapproval.domain", importOptions = ImportOption.DoNotIncludeTests.class)
class DomainArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnlyOnItselfAndCoreJdkTypes =
            classes().that().resideInAPackage("com.invoiceapproval.domain..")
                    .should().onlyDependOnClassesThat().resideInAnyPackage(
                            "com.invoiceapproval.domain..",
                            "java.lang..",
                            "java.util..",
                            "java.math..",
                            "java.time..")
                    .because("business rules must stay free of web, storage and transport code; "
                            + "put the dependency behind a port in the domain and implement it in invoice-api");
}
