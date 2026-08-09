package com.github.dropguard.summer.issuetracker.security;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.github.dropguard.summer.test.SummerTestExtension;
import com.github.dropguard.summer.test.annotation.SummerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Behavior contract for the default JWT config: with no {@code SUMMER_JWT_SECRET} override the app
 * must still boot. The old default ("change-me-in-production", 23 bytes) was below the HS256
 * 256-bit minimum and JwtUtil threw WeakKeyException at construction — the demo ITs caught it only
 * at the E2E layer; this narrow test pins the default's usability at the component layer.
 */
@SummerTest
class JwtUtilDefaultConfigTest {

    @RegisterExtension
    static SummerTestExtension ext =
            SummerTestExtension.builder().beanClasses(JwtUtil.class, JwtProperties.class).build();

    private final JwtUtil jwtUtil;

    JwtUtilDefaultConfigTest(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Test
    void defaultSecretBuildsAUsableKey() {
        assertNotNull(jwtUtil.generateAccessToken(1L, "grace"));
    }
}
