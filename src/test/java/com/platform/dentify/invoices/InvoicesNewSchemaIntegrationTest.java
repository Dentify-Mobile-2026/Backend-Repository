package com.platform.dentify.invoices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dentify.iam.domain.services.JwtService;
import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.infrastructure.persistence.InvoiceSchemaVerifier;
import com.platform.dentify.patientattention.domain.model.commands.DeleteAppointmentCommand;
import com.platform.dentify.patientattention.domain.model.commands.DeletePatientCommand;
import com.platform.dentify.patientattention.domain.services.AppointmentCommandService;
import com.platform.dentify.patientattention.domain.services.PatientCommandService;
import com.platform.dentify.support.IntegrationTestDatabase;
import com.platform.dentify.support.IsolatedTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Módulo de facturación sobre una base nueva, con el esquema que genera Hibernate a partir
 * de las entidades actuales. Se ejecuta solo si DB_URL está definido (en CI hay un MySQL).
 */
@IsolatedTestDatabase(InvoicesNewSchemaIntegrationTest.DATABASE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class InvoicesNewSchemaIntegrationTest {

    static final String DATABASE = "dentify_it_invoices_new";

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        IntegrationTestDatabase.recreate(DATABASE);
        registry.add("spring.datasource.url", () -> IntegrationTestDatabase.urlFor(DATABASE));
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private InvoiceSchemaVerifier schemaVerifier;
    @Autowired private AppointmentCommandService appointmentCommandService;
    @Autowired private PatientCommandService patientCommandService;

    private long userId;
    private String token;
    private long patientId;

    @BeforeEach
    void createClinic() {
        String username = "it" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username, company_name) VALUES (NOW(6), NOW(6), 'x', ?, 'Clinica IT')", username);
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
        token = jwtService.generateAccessToken(String.valueOf(userId));
        patientId = insertPatient(userId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private long insertPatient(long ownerId) {
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        jdbc.update("INSERT INTO patient (created_at, updated_at, dni, first_name, last_name, email, user_id) "
                + "VALUES (NOW(6), NOW(6), ?, 'Lucia', 'Vargas', 'lucia@dentify.test', ?)", dni, ownerId);
        return jdbc.queryForObject("SELECT id FROM patient WHERE dni = ?", Long.class, dni);
    }

    private long insertAppointment(long ofPatient) {
        jdbc.update("INSERT INTO appointment (created_at, updated_at, appointment_date, completed, duration, reason, status, patient_id) "
                + "VALUES (NOW(6), NOW(6), NOW() + INTERVAL 1 DAY, b'0', '00:30:00', 'Control', 'SCHEDULED', ?)", ofPatient);
        return jdbc.queryForObject("SELECT MAX(id) FROM appointment WHERE patient_id = ?", Long.class, ofPatient);
    }

    private long methodId(String name) {
        return jdbc.queryForObject("SELECT id FROM payment_method WHERE name = ?", Long.class, name);
    }

    private ResponseEntity<String> post(String path, String json, String bearer) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearer);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers), String.class);
    }

    private ResponseEntity<String> get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String invoiceJson(long appointmentId, String amountJson, long methodId) {
        return "{\"appointmentId\": " + appointmentId + ", \"amount\": " + amountJson + ", \"paymentMethodId\": " + methodId + "}";
    }

    private long createInvoice(long appointmentId) throws Exception {
        var response = post("/api/v1/invoices", invoiceJson(appointmentId, "\"80.00\"", methodId("Efectivo")), token);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private void authenticateAsClinic() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void seederLeavesExactlyTheFourPaymentMethods() throws Exception {
        var response = get("/api/v1/payment-methods");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        JsonNode methods = objectMapper.readTree(response.getBody());
        assertEquals(List.of("Efectivo", "Tarjeta de Credito", "Tarjeta de Debito", "Transferencia"),
                List.of(methods.get(0).get("name").asText(), methods.get(1).get("name").asText(),
                        methods.get(2).get("name").asText(), methods.get(3).get("name").asText()));
        assertEquals(4, methods.size());
    }

    @Test
    void newSchemaHasNoCascadeOnInvoiceAppointment() {
        var findings = schemaVerifier.verify();

        assertFalse(findings.isEmpty());
        findings.forEach(finding ->
                assertTrue(finding.safe(), () -> finding.foreignKey() + " tiene reglas " + finding.rules()));
    }

    @Test
    void createsInvoiceWithExactDecimalChosenMethodAndKeepsAppointmentState() throws Exception {
        long appointmentId = insertAppointment(patientId);
        long card = methodId("Tarjeta de Credito");

        var response = post("/api/v1/invoices", invoiceJson(appointmentId, "\"120.50\"", card), token);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        JsonNode body = objectMapper.readTree(response.getBody());
        assertEquals(0, new BigDecimal("120.50").compareTo(body.get("totalAmount").decimalValue()));
        assertEquals(card, body.get("paymentMethodId").asLong());
        assertEquals("Tarjeta de Credito", body.get("paymentMethodName").asText());

        long invoiceId = body.get("id").asLong();
        assertEquals(0, new BigDecimal("120.50").compareTo(
                jdbc.queryForObject("SELECT total_amount FROM invoice WHERE id = ?", BigDecimal.class, invoiceId)));
        assertEquals(120.5, jdbc.queryForObject("SELECT amount FROM invoice WHERE id = ?", Double.class, invoiceId));
        // Desde los abonos (Sprint 2) el método elegido se guarda en el cobro y no se duplica en el comprobante.
        assertNull(jdbc.queryForObject("SELECT paymentmethod_id FROM invoice WHERE id = ?", Long.class, invoiceId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM invoice_payment WHERE invoice_id = ?", Integer.class, invoiceId));
        assertEquals(card, jdbc.queryForObject("SELECT payment_method_id FROM invoice_payment WHERE invoice_id = ?", Long.class, invoiceId));
        assertEquals(0, new BigDecimal("120.50").compareTo(
                jdbc.queryForObject("SELECT amount FROM invoice_payment WHERE invoice_id = ?", BigDecimal.class, invoiceId)));
        // Facturar no cambia el estado clínico de la cita.
        assertEquals("SCHEDULED", jdbc.queryForObject("SELECT status FROM appointment WHERE id = ?", String.class, appointmentId));
        assertFalse(jdbc.queryForObject("SELECT completed FROM appointment WHERE id = ?", Boolean.class, appointmentId));
    }

    @Test
    void acceptsNumericAmountsToo() throws Exception {
        long appointmentId = insertAppointment(patientId);

        var response = post("/api/v1/invoices", invoiceJson(appointmentId, "99.99", methodId("Efectivo")), token);

        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        assertEquals(0, new BigDecimal("99.99").compareTo(objectMapper.readTree(response.getBody()).get("totalAmount").decimalValue()));
    }

    @Test
    void invalidRequestsGetExplicitStatusesAndCreateNothing() throws Exception {
        long appointmentId = insertAppointment(patientId);
        long cash = methodId("Efectivo");

        assertEquals(HttpStatus.BAD_REQUEST, post("/api/v1/invoices", invoiceJson(appointmentId, "\"120.555\"", cash), token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/v1/invoices", invoiceJson(appointmentId, "0", cash), token).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, post("/api/v1/invoices", invoiceJson(appointmentId, "50", 999_999), token).getStatusCode());
        var malformed = post("/api/v1/invoices", "{not-json", token);
        assertEquals(HttpStatus.BAD_REQUEST, malformed.getStatusCode());
        assertEquals("El cuerpo de la solicitud no es válido", objectMapper.readTree(malformed.getBody()).get("error").asText());
        assertEquals(0, count("SELECT COUNT(*) FROM invoice WHERE appointment_id = ?", appointmentId));

        // Cita de otro consultorio: se responde como inexistente.
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username) VALUES (NOW(6), NOW(6), 'x', ?)", "o" + UUID.randomUUID().toString().substring(0, 12));
        long otherUser = jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class);
        long foreignAppointment = insertAppointment(insertPatient(otherUser));
        var foreign = post("/api/v1/invoices", invoiceJson(foreignAppointment, "50", cash), token);
        assertEquals(HttpStatus.NOT_FOUND, foreign.getStatusCode());
        assertTrue(objectMapper.readTree(foreign.getBody()).get("error").asText().contains("no existe"));

        createInvoice(appointmentId);
        var duplicate = post("/api/v1/invoices", invoiceJson(appointmentId, "50", cash), token);
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE appointment_id = ?", appointmentId));
    }

    @Test
    void deletingAnAppointmentWithInvoiceIsBlockedThroughJpaAndSql() throws Exception {
        long appointmentId = insertAppointment(patientId);
        long invoiceId = createInvoice(appointmentId);
        authenticateAsClinic();

        var jpaError = assertThrows(RuntimeException.class,
                () -> appointmentCommandService.handle(new DeleteAppointmentCommand(appointmentId)));
        assertTrue(IntegrationTestDatabase.causedBy(jpaError, InvoiceDeletionNotAllowedException.class), jpaError.toString());

        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM appointment WHERE id = ?", appointmentId));

        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE id = ?", invoiceId));
        assertEquals(1, count("SELECT COUNT(*) FROM appointment WHERE id = ?", appointmentId));
    }

    @Test
    void deletingAPatientWithInvoicesIsBlockedThroughJpaAndSql() throws Exception {
        long appointmentId = insertAppointment(patientId);
        long invoiceId = createInvoice(appointmentId);
        authenticateAsClinic();

        var jpaError = assertThrows(RuntimeException.class,
                () -> patientCommandService.handle(new DeletePatientCommand(patientId)));
        assertTrue(jpaError.getMessage().contains("comprobante") || IntegrationTestDatabase.causedBy(jpaError, InvoiceDeletionNotAllowedException.class),
                jpaError.toString());

        // MySQL: patient -> appointment es ON DELETE CASCADE, pero la FK RESTRICT de invoice bloquea toda la sentencia.
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM patient WHERE id = ?", patientId));

        assertEquals(1, count("SELECT COUNT(*) FROM invoice WHERE id = ?", invoiceId));
        assertEquals(1, count("SELECT COUNT(*) FROM patient WHERE id = ?", patientId));
        assertEquals(1, count("SELECT COUNT(*) FROM appointment WHERE id = ?", appointmentId));
    }

    @Test
    void appointmentWithoutInvoiceCanStillBeDeleted() {
        long appointmentId = insertAppointment(patientId);
        authenticateAsClinic();

        appointmentCommandService.handle(new DeleteAppointmentCommand(appointmentId));

        assertEquals(0, count("SELECT COUNT(*) FROM appointment WHERE id = ?", appointmentId));
    }
}
