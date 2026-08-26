package com.github.dropguard.summer.tck.di;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.validation.TlsService;
import com.github.dropguard.summer.fixtures.validation.TlsValidator;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

@SummerTest
public class ValidationBehaviorTest {

    @DualEngine
    void testValidationPassesWhenTlsEnabledWithCerts(BeanContainer context) {
        TlsService service = context.getBean(TlsService.class);
        assertNotNull(service, "TlsService should be created when validation passes");
        assertTrue(service.getConfig().enabled(), "TLS should be enabled");
        assertNotNull(service.getConfig().certChain(), "cert-chain should be bound from YAML");
    }

    @DualEngine
    void testValidationPassesWhenTlsDisabled(BeanContainer context) {
        assertNotNull(context, "Context should be created even when TLS is disabled");
    }

    @DualEngine
    void testValidatorIsRegisteredAsBean(BeanContainer context) {
        TlsValidator validator = context.getBean(TlsValidator.class);
        assertNotNull(validator, "Validator should be registered as a bean");
    }
}
