package com.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class PlatformModuleStructureTest {

    ApplicationModules modules = ApplicationModules.of(PlatformApplication.class);

    @Test
    void modulesAreCompliant() {
        modules.verify();
    }
}
