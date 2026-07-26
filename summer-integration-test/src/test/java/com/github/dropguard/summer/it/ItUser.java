package com.github.dropguard.summer.it;

import java.time.LocalDateTime;

/** Minimal Redis value type for framework ITs on a real Redis. */
public record ItUser(String name, int age, LocalDateTime registeredAt) {
}
