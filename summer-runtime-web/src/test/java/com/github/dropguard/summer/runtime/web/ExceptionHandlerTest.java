package com.github.dropguard.summer.runtime.web;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.dropguard.summer.web.ExceptionRegistry;
import com.github.dropguard.summer.web.Handler;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.exception.ExceptionHandlerConflictException;
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

    @Test
    void subclassExceptionMatchesAncestorHandler() {
        // The registry walks the inheritance chain — a handler for a base type
        // acts as an implicit catch-all for its subclasses.
        ExceptionRegistry registry = new ExceptionRegistry();
        Handler baseHandler = ctx -> ctx.text(HttpStatus.BAD_REQUEST, "bad");
        registry.register(Exception.class, baseHandler);

        assertSame(baseHandler, registry.getHandler(new IllegalStateException("sub")));
        assertSame(baseHandler, registry.getHandler(new RuntimeException("sub")));
    }

    @Test
    void duplicateRegistrationThrowsInsteadOfOverwriting() {
        ExceptionRegistry registry = new ExceptionRegistry();
        registry.register(IllegalArgumentException.class, ctx -> {});

        assertThrows(
                ExceptionHandlerConflictException.class,
                () -> registry.register(IllegalArgumentException.class, ctx -> {}));
    }
}
