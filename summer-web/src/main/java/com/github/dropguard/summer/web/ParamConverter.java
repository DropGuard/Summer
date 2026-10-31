package com.github.dropguard.summer.web;

import com.github.dropguard.summer.core.Internal;
import com.github.dropguard.summer.core.config.TypeConverter;
import com.github.dropguard.summer.core.exception.ConfigurationException;
import com.github.dropguard.summer.web.exception.BadRequestException;

/**
 * Path/query parameter conversion with client-error semantics. {@link TypeConverter} is the
 * conversion truth shared with config binding, but its conversion failures surface as raw {@code
 * NumberFormatException} (numerics) or {@code ConfigurationException} (enum mismatch, unsupported
 * target) — which the exception mapper treats as an unmapped server error (500). A parameter the
 * CLIENT formatted wrongly is a request problem: wrap it as a 400 naming the parameter, the target
 * type and the offending value.
 */
@Internal
final class ParamConverter {

    private ParamConverter() {}

    static Object convert(String raw, Class<?> targetType, String name, String source) {
        try {
            return TypeConverter.convert(raw, targetType);
        } catch (ConfigurationException | IllegalArgumentException e) {
            throw new BadRequestException(
                    source
                            + " parameter '"
                            + name
                            + "' must be a "
                            + targetType.getSimpleName()
                            + " but was '"
                            + raw
                            + "'");
        }
    }
}
