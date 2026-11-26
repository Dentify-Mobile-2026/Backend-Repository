package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos que comparten varias características: sesión, paciente, cita atendida y respuestas. */
public class ComunesSteps {

    private final ApiClient api;

    public ComunesSteps(ApiClient api) {
        this.api = api;
    }

    // ---------- sesión

    @Cuando("registro un consultorio con un usuario nuevo")
    @Dado("que registré un consultorio con un usuario nuevo")
    public void registroUnConsultorio() {
        String suffix = ApiClient.unique();
        String username = "bdd_" + suffix;
        String password = "clave-" + suffix;
        api.remember("usuario", username);
        api.remember("clave", password);
        Map<String, Object> body = new HashMap<>();
        body.put("firstName", "Carla");
        body.put("lastName", "Prueba");
        body.put("email", username + "@example.test");
        body.put("companyName", "Consultorio " + suffix);
        body.put("username", username);
        body.put("password", password);
        body.put("trial", true);
        api.post("/api/auth/register", body);
    }

    @Dado("que inicié sesión como un consultorio nuevo")
    public void sesionComoConsultorioNuevo() {
        registroUnConsultorio();
        assertThat(api.status()).as(api.body()).isEqualTo(201);
        api.useToken(api.read("$.accessToken"));
    }

    // ---------- paciente

    @Dado("que tengo un paciente registrado")
    public void tengoUnPaciente() {
        registraPaciente("");
        assertThat(api.status()).as(api.body()).isEqualTo(201);
        api.remember("paciente", api.readId("$.id"));
    }

    /** Registra un paciente con un DNI aleatorio (el DNI es único en toda la base). */
    public void registraPaciente(String alergias) {
        String dni = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000));
        Map<String, Object> body = new HashMap<>();
        body.put("dni", dni);
        body.put("firstName", "Ana");
        body.put("lastName", "Paciente");
        body.put("email", "ana" + dni + "@example.test");
        body.put("homeAddress", "Av. Arequipa 123");
        body.put("birthday", "1990-05-10");
        body.put("phone", "987654321");
        body.put("allergies", alergias);
        body.put("systemicConditions", "");
        body.put("emergencyContactName", "Luis Paciente");
        body.put("emergencyContactPhone", "912345678");
        api.post("/api/v1/patients", body);
    }

    // ---------- cita atendida (facturación y panel)

    @Dado("que tengo una cita atendida de un paciente")
    public void tengoUnaCitaAtendida() {
        tengoUnPaciente();
        api.post("/api/v1/appointments", Map.of(
                "appointmentDate", "2027-03-02T10:00:00",
                "reason", "Restauración",
                "duration", "01:00",
                "patientId", api.id("paciente")));
        assertThat(api.status()).as(api.body()).isEqualTo(201);
        api.remember("cita", api.readId("$.id"));
        api.patch("/api/v1/appointments/" + api.id("cita") + "/status",
                Map.of("status", "COMPLETED", "clinicalNotes", "Restauración de la pieza 21"));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
    }

    // ---------- respuestas

    @Entonces("la respuesta es {int}")
    public void laRespuestaEs(int status) {
        assertThat(api.status()).as(api.body()).isEqualTo(status);
    }

    @Entonces("el mensaje de error es {string}")
    public void elMensajeDeErrorEs(String mensaje) {
        assertThat((String) api.read("$.error")).isEqualTo(mensaje);
    }

    @Entonces("el código de error es {string}")
    public void elCodigoDeErrorEs(String codigo) {
        assertThat((String) api.read("$.code")).isEqualTo(codigo);
    }
}
