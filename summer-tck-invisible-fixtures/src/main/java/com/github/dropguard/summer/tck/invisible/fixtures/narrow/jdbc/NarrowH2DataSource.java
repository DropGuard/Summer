package com.github.dropguard.summer.tck.invisible.fixtures.narrow.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Hand-rolled {@link DataSource} delegating to {@link DriverManager}. Invisible-fixtures is a
 * main-scope module and must not depend on H2 (the tck's test-scoped driver) — this delegates
 * instead, and the H2 driver is present on the tck's test classpath at runtime.
 */
public final class NarrowH2DataSource implements DataSource {

    private final String url;
    private final String user;
    private final String password;

    public NarrowH2DataSource(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    @Override
    public PrintWriter getLogWriter() {
        return null;
    }

    @Override
    public void setLogWriter(PrintWriter out) {}

    @Override
    public void setLoginTimeout(int seconds) {}

    @Override
    public int getLoginTimeout() {
        return 0;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        throw new SQLException("unwrap not supported");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return false;
    }
}
