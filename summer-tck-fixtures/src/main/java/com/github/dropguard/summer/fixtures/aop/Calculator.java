package com.github.dropguard.summer.fixtures.aop;

/**
 * Interface with primitive-returning methods for AOT proxy codegen coverage.
 * Each method exercises a different JVM primitive; the AOT proxy generator must
 * emit boxed casts ({@code (Integer) chain.proceed()}) rather than bare
 * primitive casts ({@code (int) chain.proceed()}), which are a compile error.
 */
public interface Calculator {

    int add(int a, int b);

    long factorial(int n);

    boolean isPositive(int n);

    double sqrt(int n);

    float half(int n);

    short negate(short n);

    byte inc(byte n);

    char firstChar(String s);
}
