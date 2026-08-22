package com.github.dropguard.summer.benchmark;

import io.avaje.jsonb.Json;

@Json
public record User(String id, String name, String email) {}
