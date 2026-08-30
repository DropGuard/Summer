package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.validation.ConfigValidationException;
import com.github.dropguard.summer.core.validation.Validator;

/** Test validator for ServiceInterface. */
@Component
public class ServiceInterfaceValidator implements Validator<ServiceInterface> {

    @Override
    public Class<ServiceInterface> targetType() {
        return ServiceInterface.class;
    }

    @Override
    public void validate(ServiceInterface service) {
        if (service == null) {
            throw new ConfigValidationException("Service cannot be null");
        }
        if (service.getName() == null || service.getName().isEmpty()) {
            throw new ConfigValidationException("Service name cannot be empty");
        }
    }
}
