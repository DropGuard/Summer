package com.github.dropguard.summer.aop;

/** Holds invocation context data and executes the interceptor chain. */
public interface InterceptorChain {

    Object getTarget();

    InterceptedMethod method();

    Object[] getArguments();

    Object proceed() throws Throwable;
}
