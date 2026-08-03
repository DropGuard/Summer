package com.github.dropguard.summer.web;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class OriginPolicyTest {

    // ── Same-origin (default: no allowed list) ──────────────────────────

    @Test
    void sameOriginWhenHostAndPortMatch() {
        assertTrue(OriginPolicy.isAllowed("http://localhost:8080", null, "localhost:8080"));
        assertTrue(OriginPolicy.isAllowed("https://example.com:8443", null, "example.com:8443"));
    }

    @Test
    void sameOriginWhenDefaultPortsMatch() {
        // http:// → default port 80, https:// → default port 443
        assertTrue(OriginPolicy.isAllowed("http://example.com", null, "example.com:80"));
        assertTrue(OriginPolicy.isAllowed("https://example.com", null, "example.com:443"));
    }

    @Test
    void notSameOriginWhenHostsDiffer() {
        assertFalse(OriginPolicy.isAllowed("http://attacker.com:8080", null, "example.com:8080"));
    }

    @Test
    void notSameOriginWhenPortsDiffer() {
        assertFalse(OriginPolicy.isAllowed("http://localhost:8080", null, "localhost:9090"));
    }

    @Test
    void notSameOriginWhenSchemeImpliesDifferentDefaultPorts() {
        // http:// defaults to 80, Host says 443 → mismatch
        assertFalse(OriginPolicy.isAllowed("http://example.com", null, "example.com:443"));
    }

    // ── Java 25 opaque-URI: bare "host:port" Host header ─────────────────

    @Test
    void bareHostPortParsedCorrectly() {
        // URI.create("localhost:8080") is opaque in Java 25 (scheme=localhost, host=null).
        // hostOf must fall through to manual parsing rather than returning null.
        assertTrue(OriginPolicy.isAllowed("http://localhost:8080", null, "localhost:8080"));
        assertTrue(OriginPolicy.isAllowed("http://localhost", null, "localhost:80"));
    }

    @Test
    void bareHostWithoutPortDefaultsTo80() {
        // Host: localhost (no port) → port 80
        assertTrue(OriginPolicy.isAllowed("http://localhost", null, "localhost"));
        assertTrue(OriginPolicy.isAllowed("http://localhost:80", null, "localhost"));
    }

    // ── Wildcard ─────────────────────────────────────────────────────────

    @Test
    void wildcardAllowsAnyOrigin() {
        assertTrue(OriginPolicy.isAllowed("http://anything.com:9999", List.of("*"), "localhost:8080"));
    }

    @Test
    void wildcardAllowsNullOrigin() {
        assertTrue(OriginPolicy.isAllowed(null, List.of("*"), "localhost:8080"));
    }

    // ── Explicit allow-list ──────────────────────────────────────────────

    @Test
    void explicitAllowListMatchesExactOrigin() {
        List<String> allowed = List.of("http://app.example.com:8443");
        assertTrue(OriginPolicy.isAllowed("http://app.example.com:8443", allowed, "localhost:8080"));
    }

    @Test
    void explicitAllowListRejectsDifferentOrigin() {
        List<String> allowed = List.of("http://app.example.com:8443");
        assertFalse(OriginPolicy.isAllowed("http://evil.example.com:8443", allowed, "localhost:8080"));
    }

    @Test
    void explicitAllowListRejectsNullOrigin() {
        assertFalse(OriginPolicy.isAllowed(null, List.of("http://example.com"), "localhost:8080"));
    }

    @Test
    void explicitAllowListMultipleEntries() {
        List<String> allowed = List.of("http://a.com", "https://b.com:443");
        // Exact string match — the allowed list must mirror the browser's Origin header.
        assertTrue(OriginPolicy.isAllowed("http://a.com", allowed, "irrelevant:80"));
        assertTrue(OriginPolicy.isAllowed("https://b.com:443", allowed, "irrelevant:80"));
        assertFalse(OriginPolicy.isAllowed("http://c.com", allowed, "irrelevant:80"));
    }

    // ── Null inputs ──────────────────────────────────────────────────────

    @Test
    void nullOriginWithNoAllowedListReturnsFalse() {
        assertFalse(OriginPolicy.isAllowed(null, null, "localhost:8080"));
        assertFalse(OriginPolicy.isAllowed(null, List.of(), "localhost:8080"));
    }

    @Test
    void nullRequestHostWithNoAllowedListReturnsFalse() {
        assertFalse(OriginPolicy.isAllowed("http://localhost:8080", null, null));
    }

    // ── isSameOrigin — bare port parsing ─────────────────────────────────

    @Test
    void isSameOriginHandlesBareHostPortManually() {
        assertTrue(OriginPolicy.isSameOrigin("http://example.com:8080", "example.com:8080"));
        assertTrue(OriginPolicy.isSameOrigin("https://example.com", "example.com:443"));
        assertFalse(OriginPolicy.isSameOrigin("http://example.com:8080", "example.com:9090"));
        assertFalse(OriginPolicy.isSameOrigin("http://a.com", "b.com"));
    }

    @Test
    void isSameOriginNullInputsReturnFalse() {
        assertFalse(OriginPolicy.isSameOrigin(null, "localhost:8080"));
        assertFalse(OriginPolicy.isSameOrigin("http://localhost", null));
        assertFalse(OriginPolicy.isSameOrigin(null, null));
    }
}
