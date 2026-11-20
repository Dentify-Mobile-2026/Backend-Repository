package com.platform.dentify.invoices.infrastructure.persistence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Verifica al arrancar que las claves foráneas que protegen los registros financieros no
 * tengan borrado en cascada.
 * <p>
 * Solo lee {@code information_schema}: nunca ejecuta DDL. Si encuentra una regla peligrosa
 * (por ejemplo {@code invoice.appointment_id ON DELETE CASCADE} en bases creadas antes del
 * Sprint 1), registra un ERROR con la migración manual que la corrige.
 */
@Component
public class InvoiceSchemaVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InvoiceSchemaVerifier.class);

    public static final String MIGRATION_SCRIPT =
            "src/main/resources/db/migration-manual/V2026_11_20__invoice_fk_restrict.sql";

    private static final Set<String> SAFE_DELETE_RULES = Set.of("RESTRICT", "NO ACTION");

    static final String FOREIGN_KEY_RULES_SQL = """
            SELECT rc.CONSTRAINT_NAME, rc.DELETE_RULE
              FROM information_schema.REFERENTIAL_CONSTRAINTS rc
              JOIN information_schema.KEY_COLUMN_USAGE k
                ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA
               AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME
               AND k.TABLE_NAME = rc.TABLE_NAME
             WHERE rc.CONSTRAINT_SCHEMA = DATABASE()
               AND rc.TABLE_NAME = ?
               AND rc.REFERENCED_TABLE_NAME = ?
               AND k.COLUMN_NAME = ?
            """;

    /**
     * Relación que debe impedir el borrado de la fila referenciada mientras existan hijos.
     * {@code remedy} indica qué hacer si la regla es peligrosa.
     */
    public record ProtectedForeignKey(String table, String column, String referencedTable, String remedy) {
        @Override
        public String toString() {
            return table + "." + column + " -> " + referencedTable;
        }
    }

    public record ForeignKeyRule(String constraintName, String deleteRule) {}

    public record Finding(ProtectedForeignKey foreignKey, List<ForeignKeyRule> rules, boolean safe) {}

    static final List<ProtectedForeignKey> PROTECTED_FOREIGN_KEYS = List.of(
            // Borrar una cita o un paciente no debe eliminar sus comprobantes.
            new ProtectedForeignKey("invoice", "appointment_id", "appointment",
                    "Ejecute la migración manual " + MIGRATION_SCRIPT),
            // Borrar un comprobante no debe eliminar sus abonos (tabla nueva: Hibernate la crea sin cascada).
            new ProtectedForeignKey("invoice_payment", "invoice_id", "invoice",
                    "Revise la FK fk_invoice_payment_invoice: debe ser ON DELETE RESTRICT o NO ACTION")
    );

    private final JdbcTemplate jdbcTemplate;

    public InvoiceSchemaVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        verify();
    }

    /** Devuelve el resultado de cada relación protegida y lo registra en el log. */
    public List<Finding> verify() {
        try {
            List<Finding> findings = PROTECTED_FOREIGN_KEYS.stream().map(this::check).toList();
            for (Finding finding : findings) {
                if (finding.safe()) {
                    log.info("Protección financiera OK: {} {}", finding.foreignKey(), finding.rules());
                } else {
                    log.error("PROTECCIÓN FINANCIERA INCOMPLETA: {} tiene reglas {}. Un borrado puede eliminar "
                                    + "registros financieros en cascada. {}",
                            finding.foreignKey(), finding.rules(), finding.foreignKey().remedy());
                }
            }
            return findings;
        } catch (DataAccessException ex) {
            log.warn("No se pudo verificar las claves foráneas financieras: {}", ex.getMessage());
            return List.of();
        }
    }

    private Finding check(ProtectedForeignKey foreignKey) {
        List<ForeignKeyRule> rules = jdbcTemplate.query(
                FOREIGN_KEY_RULES_SQL,
                (rs, rowNum) -> new ForeignKeyRule(rs.getString(1), rs.getString(2)),
                foreignKey.table(), foreignKey.referencedTable(), foreignKey.column());
        boolean safe = !rules.isEmpty() && rules.stream()
                .allMatch(rule -> SAFE_DELETE_RULES.contains(rule.deleteRule().toUpperCase(Locale.ROOT)));
        return new Finding(foreignKey, rules, safe);
    }
}
