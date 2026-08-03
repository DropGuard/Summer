package com.github.dropguard.summer.fixtures.aop;

import com.github.dropguard.summer.core.Component;

@Component
public class CalculatorService implements Calculator {

    @Logged
    @Override
    public int add(int a, int b) {
        return a + b;
    }

    @Logged
    @Override
    public long factorial(int n) {
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    @Logged
    @Override
    public boolean isPositive(int n) {
        return n > 0;
    }

    @Logged
    @Override
    public double sqrt(int n) {
        return Math.sqrt(n);
    }

    @Logged
    @Override
    public float half(int n) {
        return n / 2.0f;
    }

    @Logged
    @Override
    public short negate(short n) {
        return (short) -n;
    }

    @Logged
    @Override
    public byte inc(byte n) {
        return (byte) (n + 1);
    }

    @Logged
    @Override
    public char firstChar(String s) {
        return s.isEmpty() ? '\0' : s.charAt(0);
    }
}
