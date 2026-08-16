package com.github.dropguard.summer.data.jdbc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.dropguard.summer.core.exception.DataAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for JdbcTemplate boundary cases.
 *
 * <p>Integration tests (CRUD flows) are in summer-tck/AbstractJdbcTemplateTCK. This class focuses
 * on edge cases and error wrapping that TCK doesn't cover.
 */
@ExtendWith(MockitoExtension.class)
class JdbcTemplateTest {

    @Mock private DataSource dataSource;
    @Mock private Connection connection;
    @Mock private PreparedStatement preparedStatement;
    @Mock private ResultSet resultSet;

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() throws SQLException {
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.registerMapper(
                TestRow.class, (rs, rowNum) -> new TestRow(rs.getInt("id"), rs.getString("name")));

        lenient().when(dataSource.getConnection()).thenReturn(connection);
        lenient().when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    }

    // ---- setParameters boundary cases ----

    @Nested
    @DisplayName("setParameters edge cases")
    class SetParametersTests {

        @Test
        @DisplayName("null args array should not throw")
        void nullArgsArrayDoesNotThrow() throws SQLException {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            // setParameters has if (args != null) guard
            assertDoesNotThrow(
                    () -> jdbcTemplate.update("INSERT INTO t VALUES (?)", (Object[]) null));

            verify(preparedStatement, never()).setObject(anyInt(), any());
        }

        @Test
        @DisplayName("empty args array should not throw")
        void emptyArgsArrayDoesNotThrow() throws SQLException {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            assertDoesNotThrow(() -> jdbcTemplate.update("INSERT INTO t VALUES (1)"));

            verify(preparedStatement, never()).setObject(anyInt(), any());
        }

        @Test
        @DisplayName("parameters set with 1-based index")
        void parametersSetWithOneBasedIndex() throws SQLException {
            when(preparedStatement.executeUpdate()).thenReturn(1);

            jdbcTemplate.update("INSERT INTO t (a, b) VALUES (?, ?)", "foo", 42);

            verify(preparedStatement).setObject(1, "foo");
            verify(preparedStatement).setObject(2, 42);
        }
    }

    // ---- Connection failure wrapping ----

    @Nested
    @DisplayName("Connection failure handling")
    class ConnectionFailureTests {

        @Test
        @DisplayName("getConnection failure wrapped as DataAccessException")
        void getConnectionFailureWrapped() throws SQLException {
            when(dataSource.getConnection()).thenThrow(new SQLException("Connection refused"));

            DataAccessException ex =
                    assertThrows(
                            DataAccessException.class,
                            () -> jdbcTemplate.update("INSERT INTO t VALUES (1)"));

            assertEquals("Error executing update", ex.getMessage());
            assertInstanceOf(SQLException.class, ex.getCause());
        }

        @Test
        @DisplayName("prepareStatement failure wrapped as DataAccessException")
        void prepareStatementFailureWrapped() throws SQLException {
            when(connection.prepareStatement(anyString()))
                    .thenThrow(new SQLException("Syntax error"));

            DataAccessException ex =
                    assertThrows(DataAccessException.class, () -> jdbcTemplate.update("BAD SQL"));

            assertEquals("Error executing update", ex.getMessage());
        }

        @Test
        @DisplayName("queryForList connection failure wrapped as DataAccessException")
        void queryConnectionFailureWrapped() throws SQLException {
            when(dataSource.getConnection()).thenThrow(new SQLException("Timeout"));

            assertThrows(
                    DataAccessException.class,
                    () -> jdbcTemplate.queryForList("SELECT * FROM t", TestRow.class));
        }
    }

    // ---- queryForObject edge cases ----

    @Nested
    @DisplayName("queryForObject edge cases")
    class QueryForObjectTests {

        @Test
        @DisplayName("multiple rows throws DataAccessException")
        void multipleRowsThrowsDataAccessException() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getInt("id")).thenReturn(1, 2);
            when(resultSet.getString("name")).thenReturn("Alice", "Bob");

            DataAccessException ex =
                    assertThrows(
                            DataAccessException.class,
                            () -> jdbcTemplate.queryForObject("SELECT * FROM t", TestRow.class));

            assertEquals("Query returned more than one row", ex.getMessage());
        }
    }

    // ---- RowMapper exception propagation ----

    @Nested
    @DisplayName("RowMapper exception handling")
    class RowMapperExceptionTests {

        @Test
        @DisplayName("SQLException from RowMapper propagated as DataAccessException")
        void rowMapperSqlExceptionWrapped() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);

            RowMapper<TestRow> failingMapper =
                    (rs, rowNum) -> {
                        throw new SQLException("Column not found");
                    };
            jdbcTemplate.registerMapper(TestRow.class, failingMapper);

            assertThrows(
                    DataAccessException.class,
                    () -> jdbcTemplate.queryForList("SELECT * FROM t", TestRow.class));
        }

        @Test
        @DisplayName("RuntimeException from RowMapper propagated directly")
        void rowMapperRuntimeExceptionPropagated() throws SQLException {
            when(preparedStatement.executeQuery()).thenReturn(resultSet);
            when(resultSet.next()).thenReturn(true);

            RowMapper<TestRow> failingMapper =
                    (rs, rowNum) -> {
                        throw new IllegalStateException("Bad state");
                    };
            jdbcTemplate.registerMapper(TestRow.class, failingMapper);

            assertThrows(
                    IllegalStateException.class,
                    () -> jdbcTemplate.queryForList("SELECT * FROM t", TestRow.class));
        }
    }

    // ---- Missing RowMapper ----

    @Nested
    @DisplayName("Missing RowMapper handling")
    class MissingRowMapperTests {

        @Test
        @DisplayName("unregistered type throws DataAccessException")
        void unregisteredTypeThrows() {
            record Unmapped(int x) {}

            DataAccessException ex =
                    assertThrows(
                            DataAccessException.class,
                            () -> jdbcTemplate.queryForList("SELECT * FROM t", Unmapped.class));

            assertTrue(ex.getMessage().contains("No RowMapper registered"));
        }
    }

    // ---- Explicit RowMapper ----

    @Nested
    @DisplayName("query with explicit RowMapper")
    class ExplicitRowMapperTests {

        @Test
        @DisplayName("custom mapper maps rows without registration")
        void customMapperMapsRows() throws SQLException {
            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getInt("id")).thenReturn(1, 2);
            when(resultSet.getString("name")).thenReturn("Ada", "Linus");
            when(preparedStatement.executeQuery()).thenReturn(resultSet);

            RowMapper<String> projection =
                    (rs, rowNum) -> rs.getInt("id") + ":" + rs.getString("name");

            // No registerMapper needed — the mapper travels with the call.
            java.util.List<String> rows = jdbcTemplate.queryForList("SELECT * FROM t", projection);
            assertEquals(java.util.List.of("1:Ada", "2:Linus"), rows);
        }

        @Test
        @DisplayName("queryForObject with custom mapper returns single row")
        void customMapperSingleRow() throws SQLException {
            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getInt("id")).thenReturn(7);
            when(resultSet.getString("name")).thenReturn("Grace");
            when(preparedStatement.executeQuery()).thenReturn(resultSet);

            RowMapper<TestRow> mapper =
                    (rs, rowNum) -> new TestRow(rs.getInt("id"), rs.getString("name"));
            assertEquals(
                    new TestRow(7, "Grace"),
                    jdbcTemplate.queryForObject("SELECT * FROM t", mapper));
        }
    }

    // ---- Post-assembly immutability (A33) ----

    @Test
    @DisplayName("registerMapper after seal() throws")
    void registerMapperAfterSealThrows() {
        jdbcTemplate.seal();

        assertThrows(
                IllegalStateException.class,
                () -> jdbcTemplate.registerMapper(TestRow.class, (rs, rowNum) -> null));
    }

    // ---- Helper types ----

    record TestRow(int id, String name) {}
}
