package com.platform.dentify.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Bases MySQL aisladas para pruebas de integración.
 * <p>
 * Toma el servidor, usuario y parámetros de {@code DB_URL}, {@code DB_USERNAME} y
 * {@code DB_PASSWORD} (los mismos que usa la aplicación), pero cada clase de prueba trabaja
 * en su propia base, que se recrea vacía al inicio. Así las pruebas son reproducibles y no
 * dependen ni modifican otras bases del servidor.
 */
public final class IntegrationTestDatabase {

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

    private static String[] serverAndParams() {
        String base = System.getenv("DB_URL");
        if (base == null || base.isBlank()) {
            throw new IllegalStateException("DB_URL no está definido");
        }
        int hostStart = base.indexOf("//") + 2;
        int pathStart = base.indexOf('/', hostStart);
        int query = base.indexOf('?');
        String server = pathStart > 0 ? base.substring(0, pathStart) : base;
        String params = query >= 0 ? base.substring(query) : "";
        return new String[]{server, params};
    }

    /** URL JDBC del servidor de {@code DB_URL} apuntando a la base indicada. */
    public static String urlFor(String database) {
        String[] parts = serverAndParams();
        return parts[0] + "/" + database + parts[1];
    }

    /** Elimina y vuelve a crear la base indicada, vacía. */
    public static void recreate(String database) {
        String[] parts = serverAndParams();
        try (Connection connection = DriverManager.getConnection(parts[0] + "/" + parts[1], username(), password());
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            statement.execute("CREATE DATABASE `" + database + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        } catch (SQLException ex) {
            throw new IllegalStateException("No se pudo recrear la base " + database, ex);
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
