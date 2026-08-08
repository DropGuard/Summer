package com.github.dropguard.summer.issuetracker.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares what the first argument of an {@link IssueService} method refers to, so {@link
 * RbacInterceptor} can resolve the owning project without depending on the method name or argument
 * position.
 *
 * <p>Before this annotation existed the interceptor switched on {@code method.name()}
 * ("deleteIssue" / "createIssue" / "search") and assumed the first argument was always the resource
 * id — a fragile contract that broke silently on rename or signature change. Annotating the
 * contract makes the resource kind explicit and compile-checked.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ResourceScope {

    /** What the method's first argument identifies. */
    enum Kind {
        PROJECT,
        ISSUE
    }

    Kind value();
}
