package com.platform.dentify.invoices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dentify.iam.domain.services.JwtService;
import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.infrastructure.persistence.InvoiceSchemaVerifier;
import com.platform.dentify.patientattention.domain.model.commands.DeleteAppointmentCommand;
import com.platform.dentify.patientattention.domain.services.AppointmentCommandService;
import com.platform.dentify.support.IntegrationTestDatabase;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base existente con el esquema de dev ANTES del Sprint 1 (invoice.appointment_id
 * ON DELETE CASCADE) y datos históricos. Comprueba que el arranque solo verifica (no
 * modifica la FK), que la migración manual la corrige de forma idempotente y que, después,
 * ni JPA ni SQL pueden eliminar comprobantes al borrar citas o pacientes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvoicesLegacySchemaIntegrationTest {

    private static final String DATABASE = "dentify_it_invoices_legacy";
    private static final String MIGRATION = "db/migration-manual/V2026_11_20__invoice_fk_restrict.sql";
    private static final long USER_ID = 1L;
    private static final long PATIENT_ID = 1L;
    private static final long INVOICED_APPOINTMENT_ID = 1L;
    private static final long FREE_APPOINTMENT_ID = 2L;
    private static final long LEGACY_INVOICE_ID = 1L;

    @DynamicPropertySource
    static void legacyDatabase(DynamicPropertyRegistry registry) {
        IntegrationTestDatabase.recreate(DATABASE);
        IntegrationTestDatabase.runScript(DATABASE, "schema/legacy-dev-dda8241.sql");
        // Datos históricos: comprobante sin total_amount ni método de pago (posible en filas antiguas).
        IntegrationTestDatabase.execute(DATABASE,
                "INSERT INTO user (id, created_at, updated_at, password, username, company_name) VALUES (1, NOW(6), NOW(6), 'x', 'legacy_user', 'Clinica Legacy')",
                "INSERT INTO patient (id, created_at, updated_at, dni, first_name, last_name, email, user_id) VALUES (1, NOW(6), NOW(6), '40000001', 'Rosa', 'Diaz', 'rosa@dentify.test', 1)",
                "INSERT INTO appointment (id, created_at, updated_at, appointment_date, completed, duration, reason, status, patient_id) VALUES (1, NOW(6), NOW(6), NOW() - INTERVAL 3 DAY, b'1', '00:30:00', 'Profilaxis', 'COMPLETED', 1)",
                "INSERT INTO appointment (id, created_at, updated_at, appointment_date, completed, duration, reason, status, patient_id) VALUES (2, NOW(6), NOW(6), NOW() + INTERVAL 2 DAY, b'0', '00:30:00', 'Control', 'SCHEDULED', 1)",
                "INSERT INTO invoice (id, created_at, updated_at, amount, appointment_id, paymentmethod_id) VALUES (1, NOW(6), NOW(6), 120.5, 1, NULL)");
        registry.add("spring.datasource.url", () -> IntegrationTestDatabase.urlFor(DATABASE));
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvoiceSchemaVerifier schemaVerifier;
    @Autowired private AppointmentCommandService appointmentCommandService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private List<String> invoiceAppointmentDeleteRules() {
        return jdbc.queryForList("""
                SELECT rc.DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS rc
                  JOIN information_schema.KEY_COLUMN_USAGE k
                    ON k.CONSTRAINT_SCHEMA = rc.CONSTRAINT_SCHEMA AND k.CONSTRAINT_NAME = rc.CONSTRAINT_NAME AND k.TABLE_NAME = rc.TABLE_NAME
                 WHERE rc.CONSTRAINT_SCHEMA = DATABASE() AND rc.TABLE_NAME = 'invoice'
                   AND rc.REFERENCED_TABLE_NAME = 'appointment' AND k.COLUMN_NAME = 'appointment_id'
                """, String.class);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    @Order(1)
    void startupOnlyVerifiesAndReportsTheLegacyCascade() {
        assertTrue(invoiceAppointmentDeleteRules().contains("CASCADE"),
                () -> "El arranque no debe modificar la FK. Reglas: " + invoiceAppointmentDeleteRules());
        assertFalse(schemaVerifier.verify().get(0).safe());
    }

    @Test
    @Order(2)
    void legacyInvoiceIsReadWithDerivedExactTotalAndNoMethod() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtService.generateAccessToken(String.valueOf(USER_ID)));
        var response = rest.exchange("/api/v1/invoices", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        JsonNode invoice = objectMapper.readTree(response.getBody()).get(0);
        assertEquals(LEGACY_INVOICE_ID, invoice.get("id").asLong());
        assertEquals(120.5, invoice.get("amount").asDouble());
        assertEquals(0, new BigDecimal("120.50").compareTo(invoice.get("totalAmount").decimalValue()));
        assertTrue(invoice.get("paymentMethodName").isNull());
    }

    @Test
    @Order(3)
    void jpaDeletionIsBlockedEvenBeforeTheMigration() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));

        var error = assertThrows(RuntimeException.class,
                () -> appointmentCommandService.handle(new DeleteAppointmentCommand(INVOICED_APPOINTMENT_ID)));

        assertTrue(IntegrationTestDatabase.causedBy(error, InvoiceDeletionNotAllowedException.class), error.toString());
        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE id = ?", LEGACY_INVOICE_ID));
    }

    @Test
    @Order(4)
    void manualMigrationFixesTheForeignKeyAndIsIdempotent() {
        IntegrationTestDatabase.runScript(DATABASE, MIGRATION);
        assertEquals(List.of("RESTRICT"), invoiceAppointmentDeleteRules());
        assertTrue(schemaVerifier.verify().get(0).safe());

        IntegrationTestDatabase.runScript(DATABASE, MIGRATION);
        assertEquals(List.of("RESTRICT"), invoiceAppointmentDeleteRules());
        assertEquals(1, count("SELECT COUNT(*) FROM invoice"));
    }

    @Test
    @Order(5)
    void sqlDeletionIsBlockedAfterTheMigration() {
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM appointment WHERE id = ?", INVOICED_APPOINTMENT_ID));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM patient WHERE id = ?", PATIENT_ID));

        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE id = ?", LEGACY_INVOICE_ID));
        assertEquals(1, count("SELECT COUNT(*) FROM patient WHERE id = ?", PATIENT_ID));
    }

    @Test
    @Order(6)
    void appointmentWithoutInvoiceIsStillDeletableAfterTheMigration() {
        assertEquals(1, jdbc.update("DELETE FROM appointment WHERE id = ?", FREE_APPOINTMENT_ID));
    }
}
