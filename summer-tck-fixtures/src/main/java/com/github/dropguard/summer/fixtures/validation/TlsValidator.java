package com.github.dropguard.summer.fixtures.validation;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.core.validation.Result;
import com.github.dropguard.summer.core.validation.Validator;

/** Test fixture: validates TLS config. Reports every missing piece in one pass. */
@Component
public class TlsValidator implements Validator<TlsConfig> {

    @Override
    public Class<TlsConfig> targetType() {
        return TlsConfig.class;
    }

    @Override
    public void validate(TlsConfig config, Result result) {
        if (config.enabled() == null || !config.enabled()) {
            return;
        }
        if (config.certChain() == null) {
            result.violate("certChain", "TLS enabled but cert-chain is required");
        }
        if (config.privateKey() == null) {
            result.violate("privateKey", "TLS enabled but private-key is required");
        }
    }
}
