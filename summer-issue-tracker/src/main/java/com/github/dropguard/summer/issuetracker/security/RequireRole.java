package com.github.dropguard.summer.issuetracker.security;

import com.github.dropguard.summer.aop.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@code IssueService} method as requiring request-scoped RBAC. The {@link
 * RbacInterceptor} is bound to this annotation and enforces the coarse-grained gate (tenant
 * isolation + project membership / manager-or-lead) before the method runs. Fine-grained rules ("a
 * member may only mutate issues they reported or are assigned to") stay in the service, which reads
 * the current user from {@link com.github.dropguard.summer.web.RequestContextHolder} — no {@code
 * actorId} is threaded through the signature.
 *
 * <p>This is the demo's proof that Summer's AOP can do method-level, request-aware authorization
 * now that the framework exposes {@link com.github.dropguard.summer.web.RequestContextHolder}.
 */
@InterceptorBinding
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {}
