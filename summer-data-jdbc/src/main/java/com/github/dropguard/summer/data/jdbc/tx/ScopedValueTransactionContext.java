package com.github.dropguard.summer.data.jdbc.tx;

import java.sql.Connection;

public class ScopedValueTransactionContext {
    public static final ScopedValue<Connection> CONNECTION = ScopedValue.newInstance();

    public static Connection getCurrentConnection() {
        return CONNECTION.isBound() ? CONNECTION.get() : null;
    }
}
