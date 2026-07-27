package com.github.dropguard.summer.fixtures.web.dummy;

import com.github.dropguard.summer.core.Component;
import com.github.dropguard.summer.web.HttpContext;
import com.github.dropguard.summer.web.HttpStatus;
import com.github.dropguard.summer.web.annotation.ExceptionHandler;

@Component
public class MyExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public void handleIllegalArgument(IllegalArgumentException ex, HttpContext ctx) {
        ctx.text(HttpStatus.BAD_REQUEST, "error_caught:" + ex.getMessage());
    }
}
