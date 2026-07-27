package com.github.dropguard.summer.runtime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpStatus;
import org.junit.jupiter.api.Test;

class ExceptionHandlerTest {

    @Test
    void registryResolvesByExceptionType() {
        ExceptionRegistry registry = new ExceptionRegistry();
        registry.register(
                IllegalArgumentException.class,
                ctx -> {
                    ctx.text(HttpStatus.BAD_REQUEST, "bad request");
                });

        Handler handler = registry.getHandler(new IllegalArgumentException("test"));
        assertNotNull(handler);
    }

    @Test
    void registryReturnsNullForUnknown() {
        ExceptionRegistry registry = new ExceptionRegistry();
        Handler handler = registry.getHandler(new RuntimeException("unknown"));
        assertNull(handler);
    }
}
