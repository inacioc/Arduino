package com.example.ordermanagement;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Generates the Spring Modulith documentation of the application modules (order, customer,
 * product, notification, shared, infrastructure) from the code itself: an overall component
 * diagram, one diagram per module (PlantUML) and one module canvas per module (AsciiDoc).
 * <p>
 * Output goes to {@code target/spring-modulith-docs} of this Maven module. A plain unit test
 * (runs under Surefire): it only analyses the compiled classes and starts no Spring context.
 * <p>
 * Deliberately does not call {@code modules.verify()}: some classes still use another module's
 * internal ports (see the migration status in CLAUDE.md), so verification is not enforced yet.
 */
class ModulithDocumentationTest {

    private final ApplicationModules modules = ApplicationModules.of(WebApplication.class);

    @Test
    void writeDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
