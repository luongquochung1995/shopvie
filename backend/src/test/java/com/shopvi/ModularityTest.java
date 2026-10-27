package com.shopvi;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(ShopviApplication.class);

    @Test
    void modulesRespectBoundaries() {
        modules.verify();
    }
}
