package com.platform.dentify.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * La limpieza de bases temporales nunca puede alcanzar {@code dentify} ni la base de
 * {@code DB_URL}. No usa ningún servidor real: los nombres rechazados se validan antes de
 * conectarse, y el conector falla la prueba si se llegara a invocar.
 */
class IntegrationTestDatabaseSafetyTest {

    private static final String WORK_URL =
            "jdbc:mysql://localhost:3306/dentify?createDatabaseIfNotExist=true&useSSL=false";
    private static final String TEST_URL =
            "jdbc:mysql://127.0.0.1:3306/dentify_it?sslMode=DISABLED&allowPublicKeyRetrieval=true&useSSL=false";

    private final AtomicInteger connections = new AtomicInteger();

    private IntegrationTestDatabase.ServerConnector mustNotConnect() {
        return () -> {
            connections.incrementAndGet();
            throw new AssertionError("no debe abrirse ninguna conexión para un nombre rechazado");
        };
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dentify", "DENTIFY", "Dentify", " dentify", "dentify ",
            "mysql", "sys", "information_schema", "performance_schema",
            "dentify_it", "dentify_it_", "dentify_itx", "dentify_it_A", "DENTIFY_IT_X",
            "dentify_it_x`; DROP DATABASE dentify; -- ", "dentify_it_../dentify", "dentify_it_x y",
            "dentify_it_x-y", "dentify_it_abcdefghijabcdefghijabcdefghijabcdefghijk", "", " "
    })
    void forbiddenNamesAreRejectedBeforeConnecting(String name) {
        for (String configured : new String[]{WORK_URL, TEST_URL}) {
            assertThrows(IllegalArgumentException.class,
                    () -> IntegrationTestDatabase.drop(name, configured, mustNotConnect()), name);
            assertThrows(IllegalArgumentException.class,
                    () -> IntegrationTestDatabase.recreate(name, configured, mustNotConnect()), name);
        }
        assertEquals(0, connections.get());
    }

    @Test
    void nullNameIsRejectedBeforeConnecting() {
        assertThrows(IllegalArgumentException.class,
                () -> IntegrationTestDatabase.drop(null, TEST_URL, mustNotConnect()));
        assertEquals(0, connections.get());
    }

    @Test
    void theWorkDatabaseIsProtectedWithAnExplicitMessage() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> IntegrationTestDatabase.drop("dentify", WORK_URL, mustNotConnect()));
        assertTrue(error.getMessage().contains("protegida"), error.getMessage());
    }

    @Test
    void theDatabaseConfiguredInDbUrlIsNeverDropped() {
        String configured = "jdbc:mysql://127.0.0.1:3306/dentify_it_main?useSSL=false";
        assertThrows(IllegalArgumentException.class,
                () -> IntegrationTestDatabase.drop("dentify_it_main", configured, mustNotConnect()));
        assertThrows(IllegalArgumentException.class,
                () -> IntegrationTestDatabase.recreate("dentify_it_main", configured, mustNotConnect()));
        assertEquals(0, connections.get());
    }

    @Test
    void databaseNameIsReadFromTheJdbcUrl() {
        assertEquals("dentify", IntegrationTestDatabase.databaseOf(WORK_URL));
        assertEquals("dentify_it", IntegrationTestDatabase.databaseOf(TEST_URL));
        assertEquals("dentify", IntegrationTestDatabase.databaseOf("jdbc:mysql://h:3306/DENTIFY"));
        assertNull(IntegrationTestDatabase.databaseOf("jdbc:mysql://h:3306/?useSSL=false"));
        assertNull(IntegrationTestDatabase.databaseOf("jdbc:mysql://h:3306"));
    }

    @Test
    void temporaryDatabaseIsDroppedOnlyAfterItsSessionsCloseAndIsVerifiedGone() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement sessions = mock(PreparedStatement.class);
        PreparedStatement schemata = mock(PreparedStatement.class);
        ResultSet openSessions = mock(ResultSet.class);
        ResultSet noSessions = mock(ResultSet.class);
        ResultSet gone = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(contains("PROCESSLIST"))).thenReturn(sessions);
        when(connection.prepareStatement(contains("SCHEMATA"))).thenReturn(schemata);
        when(sessions.executeQuery()).thenReturn(openSessions, noSessions);
        when(openSessions.next()).thenReturn(true);
        when(openSessions.getInt(1)).thenReturn(2);
        when(noSessions.next()).thenReturn(true);
        when(noSessions.getInt(1)).thenReturn(0);
        when(schemata.executeQuery()).thenReturn(gone);
        when(gone.next()).thenReturn(true);
        when(gone.getInt(1)).thenReturn(0);

        IntegrationTestDatabase.drop("dentify_it_invoice_payments", WORK_URL, () -> connection);

        InOrder order = inOrder(sessions, statement, schemata);
        order.verify(sessions, times(2)).executeQuery();
        order.verify(statement).execute("SET SESSION lock_wait_timeout = 30");
        order.verify(statement).execute("DROP DATABASE IF EXISTS `dentify_it_invoice_payments`");
        order.verify(schemata).executeQuery();
        verify(sessions).setString(1, "dentify_it_invoice_payments");
        verify(statement, times(2)).execute(anyString());
    }

    @Test
    void aDropThatLeavesTheDatabaseBehindFails() throws Exception {
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        PreparedStatement query = mock(PreparedStatement.class);
        ResultSet noSessions = mock(ResultSet.class);
        ResultSet stillThere = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(statement);
        when(connection.prepareStatement(anyString())).thenReturn(query);
        when(query.executeQuery()).thenReturn(noSessions, stillThere);
        when(noSessions.next()).thenReturn(true);
        when(noSessions.getInt(1)).thenReturn(0);
        when(stillThere.next()).thenReturn(true);
        when(stillThere.getInt(1)).thenReturn(1);

        assertThrows(IllegalStateException.class,
                () -> IntegrationTestDatabase.drop("dentify_it_invoices_new", WORK_URL, () -> connection));
    }
}
