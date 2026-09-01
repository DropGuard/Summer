package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;

/** Test validator for ServiceInterface. */
@Component
public class ServiceInterfaceValidator implements Validator<ServiceInterface> {

    @Override
    public Class<ServiceInterface> targetType() {
        return ServiceInterface.class;
    }

    @Override
    public void validate(ServiceInterface service, Result result) {
        if (service == null) {
            result.violate("", "Service cannot be null");
        } else {
            if (service.getName() == null || service.getName().isEmpty()) {
                result.violate("", "Service name cannot be empty");
            }
        }
    }
}
