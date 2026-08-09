package com.github.dropguard.summer.core;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/**
 * Smoke test for the framework's test logging config: the logback-test.xml (test scope — the
 * framework's main classpath carries no logback.xml, so it can never race a consumer's own logging
 * config) must actually attach its console appender. The framework once shipped a config whose
 * appender was declared inline on the root (a legacy form logback 1.5.x silently skips — "Appender
 * named [STDOUT] not referenced") and every [Summer] diagnostic in the reactor went silent with no
 * one noticing — "configured" was not "working". This pins the working state: a broken config now
 * fails the build instead of silencing the logs.
 */
class LoggingConfigSmokeTest {

    @Test
    void logbackConfigAttachesTheConsoleAppender() {
        ch.qos.logback.classic.LoggerContext ctx =
                (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        assertNotNull(
                ctx.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("STDOUT"),
                "logback-test.xml must attach the STDOUT appender to the root");
    }
}
