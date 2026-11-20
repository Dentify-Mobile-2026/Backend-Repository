package com.platform.dentify.invoices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dentify.iam.domain.services.JwtService;
import com.platform.dentify.invoices.domain.exceptions.InvoiceDeletionNotAllowedException;
import com.platform.dentify.invoices.infrastructure.persistence.InvoiceSchemaVerifier;
import com.platform.dentify.patientattention.domain.model.commands.DeleteAppointmentCommand;
import com.platform.dentify.patientattention.domain.services.AppointmentCommandService;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abonos sobre MySQL real (Sprint 2): concurrencia, idempotencia, rollback, instantes UTC,
 * comprobantes históricos, protección de borrados y dashboard. Se ejecuta solo con DB_URL.
 */
@IsolatedTestDatabase(InvoicePaymentsIntegrationTest.DATABASE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class InvoicePaymentsIntegrationTest {

    static final String DATABASE = "dentify_it_invoice_payments";

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

    private long userId;
    private String token;
    private long patientId;

    @BeforeEach
    void createClinic() {
        String username = "pay" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username, company_name) VALUES (NOW(6), NOW(6), 'x', ?, 'Clinica IT')", username);
        userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
        token = jwtService.generateAccessToken(String.valueOf(userId));
        patientId = insertPatient(userId);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------- datos y llamadas ----------

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

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return headers;
    }

    private ResponseEntity<String> post(String path, String json) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(json, headers()), String.class);
    }

    private ResponseEntity<String> get(String path) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers()), String.class);
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody());
    }

    private JsonNode createInvoice(long appointmentId, String total, String initialPayment, Long methodId) throws Exception {
        String body = "{\"appointmentId\": " + appointmentId + ", \"amount\": \"" + total + "\""
                + ", \"paymentMethodId\": " + methodId
                + (initialPayment == null ? "" : ", \"initialPayment\": \"" + initialPayment + "\"") + "}";
        var response = post("/api/v1/invoices", body);
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        return json(response);
    }

    private ResponseEntity<String> pay(long invoiceId, String amount, long methodId, String requestId) {
        String body = "{\"amount\": \"" + amount + "\", \"paymentMethodId\": " + methodId
                + ", \"notes\": null, \"requestId\": \"" + requestId + "\"}";
        return post("/api/v1/invoices/" + invoiceId + "/payments", body);
    }

    private int paymentRows(long invoiceId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM invoice_payment WHERE invoice_id = ?", Integer.class, invoiceId);
    }

    /** Fila completa del comprobante, para comprobar que no se modificó. */
    private String invoiceRow(long invoiceId) {
        return jdbc.queryForObject("SELECT CONCAT_WS('|', id, appointment_id, amount, IFNULL(total_amount, '-'), "
                + "IFNULL(status, '-'), IFNULL(paymentmethod_id, '-'), created_at, updated_at) FROM invoice WHERE id = ?",
                String.class, invoiceId);
    }

    private BigDecimal paidInDatabase(long invoiceId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM invoice_payment WHERE invoice_id = ?",
                BigDecimal.class, invoiceId);
    }

    private static void assertMoney(String expected, JsonNode value) {
        assertEquals(0, new BigDecimal(expected).compareTo(value.decimalValue()), () -> "esperado " + expected + " y llegó " + value);
    }

    private List<HttpStatusCode> concurrently(List<Callable<ResponseEntity<String>>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
            for (Callable<ResponseEntity<String>> call : calls) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return call.call();
                }));
            }
            start.countDown();
            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<ResponseEntity<String>> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS).getStatusCode());
            }
            return statuses;
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- pruebas ----------

    @Test
    void partialInitialPaymentThenSettlementWithAnotherMethod() throws Exception {
        long appointmentId = insertAppointment(patientId);
        JsonNode invoice = createInvoice(appointmentId, "950.00", "350.00", methodId("Efectivo"));
        long invoiceId = invoice.get("id").asLong();
        assertEquals("PARTIAL", invoice.get("status").asText());
        assertMoney("350.00", invoice.get("paidAmount"));
        assertMoney("600.00", invoice.get("pendingBalance"));

        var settled = pay(invoiceId, "600.00", methodId("Transferencia"), UUID.randomUUID().toString());
        assertEquals(HttpStatus.CREATED, settled.getStatusCode(), settled.getBody());
        JsonNode body = json(settled);
        assertEquals("PAID", body.get("status").asText());
        assertMoney("0.00", body.get("pendingBalance"));
        assertEquals(2, body.get("paymentsCount").asInt());
        assertEquals("[\"Efectivo\",\"Transferencia\"]", body.get("paymentMethods").toString());
        assertTrue(body.get("paymentMethodName").isNull(), "con varios métodos no se atribuye uno solo");

        JsonNode payments = json(get("/api/v1/invoices/" + invoiceId + "/payments"));
        assertEquals(2, payments.size());
        assertMoney("350.00", payments.get(0).get("amount"));
        assertEquals("Transferencia", payments.get(1).get("paymentMethodName").asText());
        assertTrue(payments.get(0).get("paidAt").asText().endsWith("Z"), payments.toString());
        assertEquals(0, new BigDecimal("950.00").compareTo(paidInDatabase(invoiceId)));

        // Cada abono guarda su método; el comprobante nuevo no duplica ninguno.
        assertNull(jdbc.queryForObject("SELECT paymentmethod_id FROM invoice WHERE id = ?", Long.class, invoiceId));
        assertEquals(List.of(methodId("Efectivo"), methodId("Transferencia")), jdbc.queryForList(
                "SELECT payment_method_id FROM invoice_payment WHERE invoice_id = ? ORDER BY paid_at, id", Long.class, invoiceId));
    }

    @Test
    void zeroInitialPaymentCreatesAPendingInvoiceWithoutPaymentMethod() throws Exception {
        long appointmentId = insertAppointment(patientId);
        JsonNode invoice = createInvoice(appointmentId, "200.00", "0", null);
        long invoiceId = invoice.get("id").asLong();

        assertEquals("PENDING", invoice.get("status").asText());
        assertEquals(0, invoice.get("paymentsCount").asInt());
        assertEquals(0, paymentRows(invoiceId));
        assertNull(jdbc.queryForObject("SELECT paymentmethod_id FROM invoice WHERE id = ?", Long.class, invoiceId));

        assertTrue(get("/api/v1/invoices?status=PENDING").getBody().contains("\"id\":" + invoiceId));
        assertFalse(get("/api/v1/invoices?status=PAID").getBody().contains("\"id\":" + invoiceId));
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/invoices?status=FOO").getStatusCode());
    }

    @Test
    void initialPaymentAboveTheTotalIsRejected() {
        long appointmentId = insertAppointment(patientId);
        var response = post("/api/v1/invoices", "{\"appointmentId\": " + appointmentId
                + ", \"amount\": \"100.00\", \"paymentMethodId\": " + methodId("Efectivo") + ", \"initialPayment\": \"100.01\"}");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE appointment_id = ?", Integer.class, appointmentId));
    }

    @Test
    void overpaymentIsRejectedAndNothingIsWritten() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();

        var response = pay(invoiceId, "300.01", methodId("Efectivo"), UUID.randomUUID().toString());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("El abono (S/. 300.01) excede el saldo pendiente (S/. 300.00)", json(response).get("error").asText());
        assertEquals(0, paymentRows(invoiceId));
    }

    @Test
    void concurrentPaymentsCannotExceedTheBalance() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();
        long cash = methodId("Efectivo");

        var statuses = concurrently(List.of(
                () -> pay(invoiceId, "250.00", cash, UUID.randomUUID().toString()),
                () -> pay(invoiceId, "250.00", cash, UUID.randomUUID().toString())));

        assertTrue(statuses.contains(HttpStatus.CREATED) && statuses.contains(HttpStatus.CONFLICT), statuses.toString());
        assertEquals(1, paymentRows(invoiceId));
        assertEquals(0, new BigDecimal("250.00").compareTo(paidInDatabase(invoiceId)));
        assertEquals("PARTIAL", jdbc.queryForObject("SELECT status FROM invoice WHERE id = ?", String.class, invoiceId));
    }

    @Test
    void concurrentRetriesWithTheSameRequestIdCreateASinglePayment() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();
        long cash = methodId("Efectivo");
        String requestId = UUID.randomUUID().toString();

        var statuses = concurrently(List.of(
                () -> pay(invoiceId, "100.00", cash, requestId),
                () -> pay(invoiceId, "100.00", cash, requestId)));

        assertTrue(statuses.contains(HttpStatus.CREATED) && statuses.contains(HttpStatus.OK), statuses.toString());
        assertEquals(1, paymentRows(invoiceId));
        assertEquals(0, new BigDecimal("100.00").compareTo(paidInDatabase(invoiceId)));
    }

    @Test
    void sameRequestIdOnTwoInvoicesCreatesOnlyOnePayment() throws Exception {
        long first = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();
        long second = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();
        long cash = methodId("Efectivo");
        String requestId = UUID.randomUUID().toString();

        var statuses = concurrently(List.of(
                () -> pay(first, "100.00", cash, requestId),
                () -> pay(second, "100.00", cash, requestId)));

        assertTrue(statuses.contains(HttpStatus.CREATED) && statuses.contains(HttpStatus.CONFLICT), statuses.toString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM invoice_payment WHERE request_id = ?", Integer.class, requestId));
        assertEquals(1, paymentRows(first) + paymentRows(second));
    }

    @Test
    void sequentialReplayReturnsTheSameStateAndReuseWithOtherDataConflicts() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();
        long cash = methodId("Efectivo");
        String requestId = UUID.randomUUID().toString();

        var first = pay(invoiceId, "120.50", cash, requestId);
        var replay = pay(invoiceId, "120.50", cash, requestId);
        var reuse = pay(invoiceId, "50.00", cash, requestId);

        assertEquals(HttpStatus.CREATED, first.getStatusCode(), first.getBody());
        assertEquals(HttpStatus.OK, replay.getStatusCode(), replay.getBody());
        assertMoney("179.50", json(replay).get("pendingBalance"));
        assertEquals(HttpStatus.CONFLICT, reuse.getStatusCode());
        assertEquals(1, paymentRows(invoiceId));
    }

    @Test
    void failedPaymentLeavesNoPartialRecords() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "300.00", "0", null).get("id").asLong();

        var unknownMethod = pay(invoiceId, "100.00", 999_999, UUID.randomUUID().toString());
        var invalidAmount = pay(invoiceId, "10.001", methodId("Efectivo"), UUID.randomUUID().toString());

        assertEquals(HttpStatus.BAD_REQUEST, unknownMethod.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, invalidAmount.getStatusCode());
        assertEquals(0, paymentRows(invoiceId));
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM invoice WHERE id = ?", String.class, invoiceId));
    }

    @Test
    void paymentInstantIsStoredInUtc() throws Exception {
        Instant before = Instant.now();
        long invoiceId = createInvoice(insertAppointment(patientId), "80.00", null, methodId("Efectivo")).get("id").asLong();

        String raw = jdbc.queryForObject(
                "SELECT DATE_FORMAT(paid_at, '%Y-%m-%dT%H:%i:%s') FROM invoice_payment WHERE invoice_id = ?",
                String.class, invoiceId);
        Instant storedAsUtc = LocalDateTime.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toInstant(ZoneOffset.UTC);
        assertTrue(Duration.between(before, storedAsUtc).abs().compareTo(Duration.ofMinutes(2)) < 0,
                () -> "paid_at guardado " + raw + " no corresponde a UTC (ahora " + before + ")");

        String apiPaidAt = json(get("/api/v1/invoices/" + invoiceId + "/payments")).get(0).get("paidAt").asText();
        assertEquals(storedAsUtc, Instant.parse(apiPaidAt).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }

    @Test
    void historicalInvoiceIsReadAsPaidAndRejectsPayments() throws Exception {
        long appointmentId = insertAppointment(patientId);
        jdbc.update("INSERT INTO invoice (created_at, updated_at, amount, appointment_id) VALUES (NOW(6), NOW(6), 75.5, ?)", appointmentId);
        long invoiceId = jdbc.queryForObject("SELECT id FROM invoice WHERE appointment_id = ?", Long.class, appointmentId);
        String rowBefore = invoiceRow(invoiceId);

        JsonNode invoice = null;
        for (JsonNode candidate : json(get("/api/v1/invoices"))) {
            if (candidate.get("id").asLong() == invoiceId) {
                invoice = candidate;
            }
        }
        assertNotNull(invoice);
        assertTrue(invoice.get("legacy").asBoolean());
        assertEquals("PAID", invoice.get("status").asText());
        assertMoney("75.50", invoice.get("paidAmount"));
        assertMoney("0.00", invoice.get("pendingBalance"));
        assertEquals(0, invoice.get("paymentsCount").asInt());

        assertEquals(HttpStatus.CONFLICT, pay(invoiceId, "0.01", methodId("Efectivo"), UUID.randomUUID().toString()).getStatusCode());
        assertEquals("[]", get("/api/v1/invoices/" + invoiceId + "/payments").getBody());
        assertNull(jdbc.queryForObject("SELECT status FROM invoice WHERE id = ?", String.class, invoiceId),
                "leer un comprobante histórico no debe escribir datos");
        assertEquals(rowBefore, invoiceRow(invoiceId), "el comprobante histórico debe quedar intacto");
    }

    @Test
    void historicalInvoiceKeepsItsPaymentMethodAndIsNeverRewritten() throws Exception {
        long card = methodId("Tarjeta de Credito");
        long appointmentId = insertAppointment(patientId);
        jdbc.update("INSERT INTO invoice (created_at, updated_at, amount, appointment_id, paymentmethod_id) "
                + "VALUES ('2026-10-15 10:00:00', '2026-10-15 10:00:00', 120.5, ?, ?)", appointmentId, card);
        long invoiceId = jdbc.queryForObject("SELECT id FROM invoice WHERE appointment_id = ?", Long.class, appointmentId);
        String rowBefore = invoiceRow(invoiceId);

        JsonNode historical = null;
        for (JsonNode candidate : json(get("/api/v1/invoices"))) {
            if (candidate.get("id").asLong() == invoiceId) {
                historical = candidate;
            }
        }
        assertNotNull(historical);
        assertTrue(historical.get("legacy").asBoolean());
        assertEquals(card, historical.get("paymentMethodId").asLong());
        assertEquals("[\"Tarjeta de Credito\"]", historical.get("paymentMethods").toString());
        assertMoney("120.50", historical.get("totalAmount"));

        // Actividad nueva alrededor: un abono rechazado sobre el histórico y cobros en otro comprobante.
        assertEquals(HttpStatus.CONFLICT, pay(invoiceId, "1.00", card, UUID.randomUUID().toString()).getStatusCode());
        long other = createInvoice(insertAppointment(patientId), "300.00", "100.00", methodId("Efectivo")).get("id").asLong();
        assertEquals(HttpStatus.CREATED, pay(other, "50.00", card, UUID.randomUUID().toString()).getStatusCode());
        json(get("/api/v1/invoices?status=PAID"));
        json(get("/api/v1/dashboard/analytics"));

        assertEquals(rowBefore, invoiceRow(invoiceId), "el comprobante histórico no debe modificarse");
        assertEquals(0, paymentRows(invoiceId));
    }

    @Test
    void clinicsCannotSeeOrTouchEachOthersBilling() throws Exception {
        long cash = methodId("Efectivo");
        // Clínica A: una cita sin facturar y un comprobante con un abono.
        String tokenA = token;
        long freeAppointmentA = insertAppointment(patientId);
        long appointmentA = insertAppointment(patientId);
        long invoiceA = createInvoice(appointmentA, "500.00", "200.00", cash).get("id").asLong();
        String requestIdA = UUID.randomUUID().toString();
        assertEquals(HttpStatus.CREATED, pay(invoiceA, "50.00", cash, requestIdA).getStatusCode());
        String rowA = invoiceRow(invoiceA);

        // Clínica B, con su propio paciente y comprobante.
        String usernameB = "b" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username, company_name) VALUES (NOW(6), NOW(6), 'x', ?, 'Clinica B')", usernameB);
        long userB = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, usernameB);
        token = jwtService.generateAccessToken(String.valueOf(userB));
        long invoiceB = createInvoice(insertAppointment(insertPatient(userB)), "80.00", "0", null).get("id").asLong();

        // B no ve nada de A en listados, filtros ni dashboard.
        for (String path : List.of("/api/v1/invoices", "/api/v1/invoices?status=PARTIAL", "/api/v1/invoices?status=PENDING")) {
            JsonNode list = json(get(path));
            for (JsonNode item : list) {
                assertEquals(invoiceB, item.get("id").asLong(), () -> path + " expuso un comprobante ajeno: " + list);
            }
        }
        assertEquals(0.0, json(get("/api/v1/dashboard/analytics")).get("financials").get("totalCollected").asDouble());
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/invoices/" + appointmentA).getStatusCode());

        // B no puede registrar abonos, leerlos ni facturar citas de A.
        var foreignPayment = pay(invoiceA, "10.00", cash, UUID.randomUUID().toString());
        assertEquals(HttpStatus.NOT_FOUND, foreignPayment.getStatusCode());
        assertTrue(json(foreignPayment).has("error"), foreignPayment.getBody());
        assertFalse(json(foreignPayment).has("totalAmount"), foreignPayment.getBody());
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/invoices/" + invoiceA + "/payments").getStatusCode());
        var foreignInvoice = post("/api/v1/invoices",
                "{\"appointmentId\": " + freeAppointmentA + ", \"amount\": \"10.00\", \"paymentMethodId\": " + cash + "}");
        assertEquals(HttpStatus.NOT_FOUND, foreignInvoice.getStatusCode());

        // Reutilizar el requestId de A no repite su operación ni expone sus datos: es un conflicto.
        var reused = pay(invoiceB, "50.00", cash, requestIdA);
        assertEquals(HttpStatus.CONFLICT, reused.getStatusCode());
        assertTrue(json(reused).has("error"), reused.getBody());
        assertFalse(json(reused).has("pendingBalance"), reused.getBody());
        assertEquals(0, paymentRows(invoiceB));

        // Lo de A quedó intacto y A sigue viéndolo.
        token = tokenA;
        assertEquals(rowA, invoiceRow(invoiceA));
        assertEquals(2, paymentRows(invoiceA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE appointment_id = ?", Integer.class, freeAppointmentA));
        assertEquals(2, json(get("/api/v1/invoices/" + invoiceA + "/payments")).size());
        assertEquals(HttpStatus.OK, get("/api/v1/invoices/" + appointmentA).getStatusCode());
        assertEquals(250.0, json(get("/api/v1/dashboard/analytics")).get("financials").get("totalCollected").asDouble());
    }

    @Test
    void invoicesWithPaymentsCannotBeDeleted() throws Exception {
        long appointmentId = insertAppointment(patientId);
        long invoiceId = createInvoice(appointmentId, "500.00", "200.00", methodId("Efectivo")).get("id").asLong();

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM invoice WHERE id = ?", invoiceId));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM appointment WHERE id = ?", appointmentId));

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
        var jpaError = assertThrows(RuntimeException.class,
                () -> appointmentCommandService.handle(new DeleteAppointmentCommand(appointmentId)));
        assertTrue(IntegrationTestDatabase.causedBy(jpaError, InvoiceDeletionNotAllowedException.class), jpaError.toString());

        assertEquals(1, paymentRows(invoiceId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM invoice WHERE id = ?", Integer.class, invoiceId));
        schemaVerifier.verify().forEach(finding -> assertTrue(finding.safe(), finding.toString()));
    }

    @Test
    void paymentsOfAnotherClinicAreNotFound() throws Exception {
        long invoiceId = createInvoice(insertAppointment(patientId), "100.00", "0", null).get("id").asLong();
        String ownToken = token;
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username) VALUES (NOW(6), NOW(6), 'x', ?)",
                "o" + UUID.randomUUID().toString().substring(0, 12));
        token = jwtService.generateAccessToken(String.valueOf(jdbc.queryForObject("SELECT MAX(id) FROM user", Long.class)));

        assertEquals(HttpStatus.NOT_FOUND, pay(invoiceId, "10.00", methodId("Efectivo"), UUID.randomUUID().toString()).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, get("/api/v1/invoices/" + invoiceId + "/payments").getStatusCode());
        token = ownToken;
        assertEquals(0, paymentRows(invoiceId));
    }

    @Test
    void dashboardCountsCollectedMoneyAndKeepsTheBalance() throws Exception {
        createInvoice(insertAppointment(patientId), "1000.00", "200.00", methodId("Efectivo"));

        JsonNode financials = json(get("/api/v1/dashboard/analytics")).get("financials");

        assertEquals(200.0, financials.get("totalCollected").asDouble());
        assertEquals(800.0, financials.get("pendingBalance").asDouble());
        assertEquals(20.0, financials.get("collectionRate").asDouble());
    }
}
