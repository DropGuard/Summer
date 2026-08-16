package com.github.dropguard.summer.tck.invisible.fixtures.narrow.nested;

/** Hosts a nested interface — the AOT codegen must reference it as {@code NestedHolder.Router}. */
public class NestedHolder {

    public interface Router {}
}
