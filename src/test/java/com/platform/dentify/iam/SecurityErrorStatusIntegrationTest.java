package com.platform.dentify.iam;

import com.platform.dentify.iam.interfaces.rest.resources.RegisterRequestResource;
import com.platform.dentify.iam.interfaces.rest.resources.RegisterResponseResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the HTTP status codes produced by the security filter chain, including the
 * container ERROR dispatch to /error. A real servlet container is required because
 * MockMvc does not perform the ERROR dispatch.
 */
// Needs a real database: runs only when DB_URL is set.
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityErrorStatusIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    private String accessToken;

    @BeforeAll
    void registerUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String username = "itsec_" + suffix; // User.username allows at most 20 characters
        var request = new RegisterRequestResource(
                "Integration", "Security", username + "@example.test", "Dentify IT",
                username, "it-pass-" + suffix, true);

        ResponseEntity<RegisterResponseResource> response =
                rest.postForEntity("/api/auth/register", request, RegisterResponseResource.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        accessToken = response.getBody().accessToken();
        assertThat(accessToken).isNotBlank();
    }

    @Test
    void unknownRouteWithValidTokenReturns404() {
        ResponseEntity<String> response = get("/api/v1/route-that-does-not-exist", bearer(accessToken));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() {
        ResponseEntity<String> response = get("/api/v1/patients", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpointWithInvalidTokenReturns401() {
        ResponseEntity<String> response = get("/api/v1/patients", bearer("abc.def.ghi"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void malformedJsonWithValidTokenReturns400() {
        HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.exchange(
                "/api/v1/invoices", HttpMethod.POST, new HttpEntity<>("{not-json", headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertNoInternalDetails(response.getBody());
    }

    @Test
    void healthWithoutTokenReturns200() {
        ResponseEntity<String> response = get("/health", new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void authenticatedEndpointWithValidTokenStillWorks() {
        // GET /api/v1/patients answers 404 when the user has no patients, so create one first.
        // Patient.dni is globally unique, so use a random one to keep the test re-runnable.
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
        HttpHeaders headers = bearer(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, Object> patient = Map.ofEntries(
                Map.entry("dni", dni),
                Map.entry("firstName", "Ana"),
                Map.entry("lastName", "Prueba"),
                Map.entry("email", "ana.prueba@example.test"),
                Map.entry("homeAddress", "Av. Test 123"),
                Map.entry("birthday", "1990-05-10"),
                Map.entry("phone", "999888777"),
                Map.entry("allergies", "none"),
                Map.entry("systemicConditions", "none"),
                Map.entry("emergencyContactName", "Luis Prueba"),
                Map.entry("emergencyContactPhone", "999111222"));

        ResponseEntity<String> created = rest.exchange(
                "/api/v1/patients", HttpMethod.POST, new HttpEntity<>(patient, headers), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> response = get("/api/v1/patients", bearer(accessToken));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Ana");
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static void assertNoInternalDetails(String body) {
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("\"trace\"", "Exception", "at com.");
    }
}
