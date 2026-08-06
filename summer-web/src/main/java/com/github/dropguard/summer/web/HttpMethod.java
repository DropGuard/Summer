package com.github.dropguard.summer.web;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD,
    OPTIONS,
    /** Any HTTP method not in the known set — handler should return 405. */
    UNKNOWN;
}
