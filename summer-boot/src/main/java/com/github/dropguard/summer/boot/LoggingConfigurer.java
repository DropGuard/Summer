package com.github.dropguard.summer.boot;

import com.github.dropguard.summer.core.Internal;
import org.slf4j.LoggerFactory;

/**
 * Programmatic default logging (the Spring Boot {@code LogbackLoggingSystem} model). When the
 * classpath has no {@code logback.xml} / {@code logback-test.xml}, logback falls back to its
 * BasicConfigurator (a DEBUG-level console — noise). Summer instead applies its own defaults (root
 * INFO + a console appender with the framework pattern) so an app with no logging config still gets
 * sane diagnostics. If the app ships its own config, logback's auto-config already ran and this is
 * a no-op — the app's config wins.
 */
@Internal
public final class LoggingConfigurer {

    private LoggingConfigurer() {}

    /** Applies the framework's default logging when the classpath carries no logback config. */
    public static void configureDefaults() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl.getResource("logback.xml") != null || cl.getResource("logback-test.xml") != null) {
            return;
        }
        if (!(LoggerFactory.getILoggerFactory()
                instanceof ch.qos.logback.classic.LoggerContext ctx)) {
            return;
        }
        ctx.reset();
        ch.qos.logback.classic.encoder.PatternLayoutEncoder encoder =
                new ch.qos.logback.classic.encoder.PatternLayoutEncoder();
        encoder.setContext(ctx);
        encoder.setPattern("%d{HH:mm:ss.SSS} %-5level %logger{36} -- %msg%n");
        encoder.start();
        ch.qos.logback.core.ConsoleAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.ConsoleAppender<>();
        appender.setContext(ctx);
        appender.setEncoder(encoder);
        appender.start();
        ch.qos.logback.classic.Logger root =
                ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
        root.setLevel(ch.qos.logback.classic.Level.INFO);
        root.addAppender(appender);
    }
}
