package com.github.dropguard.summer.web.jsonb;

import io.avaje.jsonb.Json;

@Json
public record PersonDto(String name, int age, String email) {}
