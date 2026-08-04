package com.github.dropguard.summer.tck.aop;

import static org.junit.jupiter.api.Assertions.*;

import com.github.dropguard.summer.core.BeanContainer;
import com.github.dropguard.summer.fixtures.aop.Calculator;
import com.github.dropguard.summer.test.annotation.DualEngine;
import com.github.dropguard.summer.test.annotation.SummerTest;

/**
 * Verifies that AOT proxy codegen handles primitive return types correctly. Without the boxed-cast
 * fix, the AOT engine emits {@code (int) chain.proceed()} which is a compile error.
 */
@SummerTest
class AopPrimitiveReturnTest {

    private final BeanContainer context;

    AopPrimitiveReturnTest(BeanContainer context) {
        this.context = context;
    }

    @DualEngine
    void testIntReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals(7, calc.add(3, 4));
    }

    @DualEngine
    void testLongReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals(120L, calc.factorial(5));
    }

    @DualEngine
    void testBooleanReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertTrue(calc.isPositive(42));
        assertFalse(calc.isPositive(-1));
    }

    @DualEngine
    void testDoubleReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals(3.0, calc.sqrt(9), 0.001);
    }

    @DualEngine
    void testFloatReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals(2.5f, calc.half(5), 0.001f);
    }

    @DualEngine
    void testShortReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals((short) -7, calc.negate((short) 7));
    }

    @DualEngine
    void testByteReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals((byte) 6, calc.inc((byte) 5));
    }

    @DualEngine
    void testCharReturn() {
        Calculator calc = context.getBean(Calculator.class);
        assertEquals('A', calc.firstChar("ABC"));
        assertEquals('\0', calc.firstChar(""));
    }
}
