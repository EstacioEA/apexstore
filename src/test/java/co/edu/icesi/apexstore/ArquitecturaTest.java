package co.edu.icesi.apexstore;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Verifica las dependencias estructurales y la ausencia de clientes financieros externos. */
@AnalyzeClasses(packages = "co.edu.icesi.apexstore")
class ArquitecturaTest {
    @ArchTest
    static final ArchRule pasarelasNoConocenPersistencia =
            noClasses().that().resideInAnyPackage("..pasarelas..")
                    .should().dependOnClassesThat().resideInAnyPackage("..backend..", "..db..");

    @ArchTest
    static final ArchRule backendNoConocePasarelasNiDb =
            noClasses().that().resideInAnyPackage("..backend..")
                    .should().dependOnClassesThat().resideInAnyPackage("..pasarelas..", "..db..");

    @ArchTest
    static final ArchRule dbNoConoceBackendNiPasarelas =
            noClasses().that().resideInAnyPackage("..db..")
                    .should().dependOnClassesThat().resideInAnyPackage("..backend..", "..pasarelas..");

}
