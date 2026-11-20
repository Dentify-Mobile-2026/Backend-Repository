package com.platform.dentify.invoices;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.dentify.iam.domain.services.JwtService;
import com.platform.dentify.shared.domain.model.valueobjects.BusinessTime;
import com.platform.dentify.support.IntegrationTestDatabase;
import com.platform.dentify.support.IsolatedTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fechas de cobro de punta a punta con MySQL: el instante se guarda en UTC y el mes contable
 * se decide en America/Lima, con independencia de la zona de la JVM (UTC en el CI, Lima en
 * local). El reloj de negocio se fija en el 30/11/2026 a las 23:30 de Lima.
 */
@IsolatedTestDatabase(InvoiceBusinessTimeIntegrationTest.DATABASE)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class InvoiceBusinessTimeIntegrationTest {

    static final String DATABASE = "dentify_it_business_time";

    /** 30/11/2026 23:30 en Lima (UTC−5) = 01/12/2026 04:30 UTC. */
    private static final Instant NOVEMBER_30_2330_LIMA = Instant.parse("2026-12-01T04:30:00Z");

    @TestConfiguration
    static class FixedBusinessClock {
        @Bean
        @Primary
        Clock fixedBusinessClock() {
            return Clock.fixed(NOVEMBER_30_2330_LIMA, BusinessTime.ZONE);
        }
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        IntegrationTestDatabase.recreate(DATABASE);
        registry.add("spring.datasource.url", () -> IntegrationTestDatabase.urlFor(DATABASE));
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private ObjectMapper objectMapper;

    private String token;
    private long patientId;

    @BeforeEach
    void createClinic() {
        String username = "tz" + UUID.randomUUID().toString().substring(0, 12);
        jdbc.update("INSERT INTO user (created_at, updated_at, password, username, company_name) VALUES (NOW(6), NOW(6), 'x', ?, 'Clinica TZ')", username);
        long userId = jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
        token = jwtService.generateAccessToken(String.valueOf(userId));
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        jdbc.update("INSERT INTO patient (created_at, updated_at, dni, first_name, last_name, email, user_id) "
                + "VALUES (NOW(6), NOW(6), ?, 'Rosa', 'Diaz', 'rosa@dentify.test', ?)", dni, userId);
        patientId = jdbc.queryForObject("SELECT id FROM patient WHERE dni = ?", Long.class, dni);
    }

    private long insertAppointment() {
        jdbc.update("INSERT INTO appointment (created_at, updated_at, appointment_date, completed, duration, reason, status, patient_id) "
                + "VALUES (NOW(6), NOW(6), '2026-11-18 10:00:00', b'0', '00:30:00', 'Control', 'SCHEDULED', ?)", patientId);
        return jdbc.queryForObject("SELECT MAX(id) FROM appointment WHERE patient_id = ?", Long.class, patientId);
    }

    private long methodId(String name) {
        return jdbc.queryForObject("SELECT id FROM payment_method WHERE name = ?", Long.class, name);
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(json, headers), String.class);
    }

    private JsonNode get(String path) throws Exception {
        var response = exchange(HttpMethod.GET, path, null);
        assertEquals(HttpStatus.OK, response.getStatusCode(), response.getBody());
        return objectMapper.readTree(response.getBody());
    }

    private long pendingInvoice(String total) throws Exception {
        var response = exchange(HttpMethod.POST, "/api/v1/invoices", "{\"appointmentId\": " + insertAppointment()
                + ", \"amount\": \"" + total + "\", \"paymentMethodId\": null, \"initialPayment\": \"0\"}");
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
        return objectMapper.readTree(response.getBody()).get("id").asLong();
    }

    private void payNow(long invoiceId, String amount) {
        var response = exchange(HttpMethod.POST, "/api/v1/invoices/" + invoiceId + "/payments",
                "{\"amount\": \"" + amount + "\", \"paymentMethodId\": " + methodId("Efectivo")
                        + ", \"notes\": null, \"requestId\": \"" + UUID.randomUUID() + "\"}");
        assertEquals(HttpStatus.CREATED, response.getStatusCode(), response.getBody());
    }

    /** Abono con un instante exacto, escrito en crudo en UTC como lo guarda la aplicación. */
    private void insertPaymentAtUtc(long invoiceId, String amount, String utcDateTime) {
        jdbc.update("INSERT INTO invoice_payment (created_at, updated_at, amount, paid_at, invoice_id, payment_method_id) "
                + "VALUES (NOW(6), NOW(6), ?, ?, ?, ?)", new java.math.BigDecimal(amount), utcDateTime, invoiceId, methodId("Efectivo"));
    }

    private double totalCollected(JsonNode analytics) {
        return analytics.get("financials").get("totalCollected").asDouble();
    }

    @Test
    void paymentAt2330InLimaOnNovember30IsStoredInUtcAndReportedInNovember() throws Exception {
        long invoiceId = pendingInvoice("1000.00");

        payNow(invoiceId, "300.00");

        assertEquals("2026-12-01 04:30:00", jdbc.queryForObject(
                "SELECT DATE_FORMAT(paid_at, '%Y-%m-%d %H:%i:%s') FROM invoice_payment WHERE invoice_id = ?",
                String.class, invoiceId), "paid_at debe guardarse en UTC");
        String apiPaidAt = get("/api/v1/invoices/" + invoiceId + "/payments").get(0).get("paidAt").asText();
        assertEquals(NOVEMBER_30_2330_LIMA, Instant.parse(apiPaidAt), apiPaidAt);

        JsonNode analytics = get("/api/v1/dashboard/analytics");
        assertEquals("2026-11", analytics.get("month").asText(), "el mes contable se decide en Lima");
        assertEquals(300.0, totalCollected(analytics));

        JsonNode recent = get("/api/v1/dashboard").get("recentPayments");
        assertEquals(1, recent.size());
        assertEquals(LocalDateTime.of(2026, 11, 30, 23, 30), LocalDateTime.parse(recent.get(0).get("createdAt").asText()),
                "se muestra en hora de Lima");
        assertEquals(300.0, recent.get(0).get("amount").asDouble());
    }

    @Test
    void monthBoundariesFollowMidnightInLima() throws Exception {
        long invoiceId = pendingInvoice("1000.00");
        insertPaymentAtUtc(invoiceId, "100.00", "2026-11-01 04:59:59"); // 31/10 23:59:59 en Lima → octubre
        insertPaymentAtUtc(invoiceId, "200.00", "2026-11-01 05:00:00"); // 01/11 00:00:00 en Lima → noviembre
        payNow(invoiceId, "300.00");                                     // 30/11 23:30 en Lima → noviembre

        assertEquals(500.0, totalCollected(get("/api/v1/dashboard/analytics")));
        JsonNode invoice = get("/api/v1/invoices?status=PARTIAL").get(0);
        assertEquals(0, new java.math.BigDecimal("600.00").compareTo(invoice.get("paidAmount").decimalValue()));
    }

    @Test
    void historicalInvoicesCountOnTheirIssueDateInTheBusinessMonth() throws Exception {
        jdbc.update("INSERT INTO invoice (created_at, updated_at, amount, appointment_id) VALUES ('2026-11-15 12:00:00', '2026-11-15 12:00:00', 75.5, ?)",
                insertAppointment());
        jdbc.update("INSERT INTO invoice (created_at, updated_at, amount, appointment_id) VALUES ('2026-10-20 12:00:00', '2026-10-20 12:00:00', 40.0, ?)",
                insertAppointment());

        JsonNode analytics = get("/api/v1/dashboard/analytics");
        assertEquals(75.5, totalCollected(analytics), "solo el histórico emitido en noviembre cuenta en noviembre");
        assertEquals(0.0, analytics.get("financials").get("pendingBalance").asDouble(), "un histórico no deja saldo");

        for (JsonNode invoice : get("/api/v1/invoices")) {
            assertTrue(invoice.get("legacy").asBoolean());
            assertEquals("PAID", invoice.get("status").asText());
        }
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM invoice i JOIN appointment a ON a.id = i.appointment_id "
                + "WHERE a.patient_id = ? AND i.status IS NULL AND i.total_amount IS NULL", Integer.class, patientId),
                "leer históricos no les escribe datos");
    }
}
