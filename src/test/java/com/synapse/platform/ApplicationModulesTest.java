package com.synapse.platform;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

    @Test
    void verifyModuleStructure() {
        ApplicationModules.of(PlatformSvcApplication.class).verify();
    }
}
