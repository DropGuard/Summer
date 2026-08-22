package com.github.dropguard.summer.web;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class Response {
    HttpStatus status;
    byte[] body;
    Object resultObject;
    BodyConverter converter;
    private Map<String, String> headers;

    Map<String, String> headers() {
        return headers != null ? headers : Collections.emptyMap();
    }

    void setHeader(String name, String value) {
        if (headers == null) {
            headers = new HashMap<>(4);
        }
        headers.put(name, value);
    }
}
