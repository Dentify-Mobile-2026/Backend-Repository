package com.platform.dentify.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bases MySQL aisladas para pruebas de integración.
 * <p>
 * Toma el servidor, usuario y parámetros de {@code DB_URL}, {@code DB_USERNAME} y
 * {@code DB_PASSWORD} (los mismos que usa la aplicación), pero cada clase de prueba trabaja
 * en su propia base temporal: se recrea vacía al inicio y se elimina al terminar la clase
 * (ver {@link IsolatedTestDatabase}).
 * <p>
 * <b>Seguridad.</b> Antes de abrir cualquier conexión, {@link #requireTemporary} exige que el
 * nombre sea una base temporal ({@code dentify_it_<sufijo>}, en minúsculas) y rechaza
 * {@code dentify}, las bases del sistema y la base configurada en {@code DB_URL}. Así ningún
 * DROP de estas utilidades puede alcanzar la base de trabajo.
 */
public final class IntegrationTestDatabase {

    /** Prefijo obligatorio de toda base que estas utilidades pueden crear o borrar. */
    public static final String TEMPORARY_PREFIX = "dentify_it_";

    private static final Pattern TEMPORARY_NAME = Pattern.compile("dentify_it_[a-z0-9_]{1,40}");

    /** Bases que nunca se crean ni se borran desde las pruebas, aunque el patrón cambiara. */
    private static final Set<String> PROTECTED = Set.of(
            "dentify", "mysql", "sys", "information_schema", "performance_schema");

    private static final Duration SESSIONS_TIMEOUT = Duration.ofSeconds(15);

    /** Abre una conexión al servidor, sin base seleccionada. */
    @FunctionalInterface
    interface ServerConnector {
        Connection connect() throws SQLException;
    }

    private IntegrationTestDatabase() {
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String username() {
        return env("DB_USERNAME", "root");
    }

    public static String password() {
        return env("DB_PASSWORD", "root");
    }

    private static String configuredUrl() {
        String base = System.getenv("DB_URL");
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("DB_URL no está definido");
        }
        return base;
    }

    private static String[] serverAndParams(String url) {
        int hostStart = url.indexOf("//") + 2;
        int pathStart = url.indexOf('/', hostStart);
        int query = url.indexOf('?');
        String server = pathStart > 0 ? url.substring(0, pathStart) : (query >= 0 ? url.substring(0, query) : url);
        String params = query >= 0 ? url.substring(query) : "";
        return new String[]{server, params};
    }

    /** Nombre de la base de una URL JDBC, en minúsculas, o {@code null} si no tiene. */
    static String databaseOf(String url) {
        if (url == null) {
            return null;
        }
        int hostStart = url.indexOf("//");
        if (hostStart < 0) {
            return null;
        }
        int pathStart = url.indexOf('/', hostStart + 2);
        if (pathStart < 0) {
            return null;
        }
        int query = url.indexOf('?', pathStart);
        String name = query >= 0 ? url.substring(pathStart + 1, query) : url.substring(pathStart + 1);
        return name.isBlank() ? null : name.toLowerCase(Locale.ROOT);
    }

    /**
     * Comprueba que {@code database} sea una base temporal de pruebas. Lanza
     * {@link IllegalArgumentException} si es {@code dentify}, una base del sistema, la base de
     * {@code configuredUrl} o cualquier nombre fuera del patrón {@code dentify_it_[a-z0-9_]{1,40}}.
     */
    static void requireTemporary(String database, String configuredUrl) {
        if (database == null) {
            throw new IllegalArgumentException("Falta el nombre de la base temporal");
        }
        String normalized = database.strip().toLowerCase(Locale.ROOT);
        if (PROTECTED.contains(normalized)) {
            throw new IllegalArgumentException("La base '" + database + "' está protegida: las pruebas nunca la crean ni la borran");
        }
        if (!TEMPORARY_NAME.matcher(database).matches()) {
            throw new IllegalArgumentException("'" + database + "' no es una base temporal de pruebas ("
                    + TEMPORARY_PREFIX + "<a-z0-9_>, hasta 40 caracteres de sufijo)");
        }
        if (normalized.equals(databaseOf(configuredUrl))) {
            throw new IllegalArgumentException("La base '" + database + "' es la configurada en DB_URL: no se borra");
        }
    }

    /** URL JDBC del servidor de {@code DB_URL} apuntando a la base temporal indicada. */
    public static String urlFor(String database) {
        String url = configuredUrl();
        requireTemporary(database, url);
        String[] parts = serverAndParams(url);
        return parts[0] + "/" + database + parts[1];
    }

    private static ServerConnector serverConnector(String url) {
        String[] parts = serverAndParams(url);
        return () -> DriverManager.getConnection(parts[0] + "/" + parts[1], username(), password());
    }

    /** Elimina y vuelve a crear la base temporal indicada, vacía. */
    public static void recreate(String database) {
        String url = configuredUrl();
        recreate(database, url, serverConnector(url));
    }

    static void recreate(String database, String configuredUrl, ServerConnector connector) {
        requireTemporary(database, configuredUrl);
        try (Connection connection = connector.connect();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo recrear la base " + database, ex);
        }
    }

    /**
     * Elimina la base temporal indicada y verifica que ya no exista. Antes espera a que no
     * queden sesiones conectadas a ella (el contexto de Spring debe estar cerrado); si siguen
     * abiertas, falla en lugar de borrar una base en uso.
     */
    public static void drop(String database) {
        String url = configuredUrl();
        drop(database, url, serverConnector(url));
    }

    static void drop(String database, String configuredUrl, ServerConnector connector) {
        requireTemporary(database, configuredUrl);
        try (Connection connection = connector.connect();
             Statement statement = connection.createStatement()) {
            awaitNoSessions(connection, database);
            statement.execute("SET SESSION lock_wait_timeout = 30");
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            if (exists(connection, database)) {
                throw new IllegalStateException("La base " + database + " sigue existiendo después del DROP");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo eliminar la base " + database, ex);
        }
    }

    /** Indica si la base existe en el servidor de {@code DB_URL}. */
    public static boolean exists(String database) {
        String url = configuredUrl();
        try (Connection connection = serverConnector(url).connect()) {
            return exists(connection, database);
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo consultar la base " + database, ex);
        }
    }

    private static boolean exists(Connection connection, String database) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = ?")) {
            query.setString(1, database);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }

    private static void awaitNoSessions(Connection connection, String database) throws SQLException {
        long deadline = System.nanoTime() + SESSIONS_TIMEOUT.toNanos();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB = ? AND ID <> CONNECTION_ID()")) {
            query.setString(1, database);
            while (true) {
                int sessions;
                try (ResultSet result = query.executeQuery()) {
                    sessions = result.next() ? result.getInt(1) : 0;
                }
                if (sessions == 0) {
                    return;
                }
                if (System.nanoTime() > deadline) {
                    throw new IllegalStateException("La base " + database + " sigue en uso por " + sessions
                            + " sesiones: el contexto de Spring debe cerrarse antes de borrarla");
                }
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrumpido esperando a que se libere " + database, ex);
                }
            }
        }
    }

    /** Ejecuta un script SQL del classpath en una única conexión (conserva variables de sesión). */
    public static void runScript(String database, String classpathLocation) {
        try (Connection connection = DriverManager.getConnection(urlFor(database), username(), password())) {
            ScriptUtils.executeSqlScript(connection,
                    new EncodedResource(new ClassPathResource(classpathLocation), StandardCharsets.UTF_8));
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo ejecutar " + classpathLocation, ex);
        }
    }

    /** Ejecuta sentencias sueltas en la base indicada. */
    public static void execute(String database, String... statements) {
        try (Connection connection = DriverManager.getConnection(urlFor(database), username(), password());
             Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo preparar la base " + database, ex);
        }
    }

    /** Indica si {@code type} aparece en la cadena de causas de {@code error}. */
    public static boolean causedBy(Throwable error, Class<? extends Throwable> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
    }
}
