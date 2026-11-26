package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de citas.feature (US04 a US09, US42, US43, US45, US55, US56). */
public class CitasSteps {

    private final ApiClient api;
    private final List<Map<String, Object>> horario = new ArrayList<>();

    public CitasSteps(ApiClient api) {
        this.api = api;
    }

    @Cuando("agendo una cita el {string} a las {string} por {string}")
    public void agendoUnaCita(String fecha, String hora, String duracion) {
        api.post("/api/v1/appointments", Map.of(
                "appointmentDate", fecha + "T" + hora + ":00",
                "reason", "Control",
                "duration", duracion,
                "patientId", api.id("paciente")));
        if (api.status() == 201) {
            api.remember("cita", api.readId("$.id"));
        }
    }

    @Dado("que tengo una cita el {string} a las {string} por {string}")
    public void tengoUnaCita(String fecha, String hora, String duracion) {
        agendoUnaCita(fecha, hora, duracion);
        assertThat(api.status()).as(api.body()).isEqualTo(201);
    }

    @Dado("que el consultorio atiende de lunes a viernes de {string} a {string}")
    public void atiendeDeLunesAViernes(String abre, String cierra) {
        horario.clear();
        for (DayOfWeek dia : DayOfWeek.values()) {
            boolean abierto = dia.getValue() <= 5;
            Map<String, Object> d = new HashMap<>();
            d.put("dayOfWeek", dia.name());
            d.put("open", abierto);
            d.put("opensAt", abierto ? abre : null);
            d.put("closesAt", abierto ? cierra : null);
            horario.add(d);
        }
        guardarHorario();
    }

    @Cuando("cambio el horario de lunes a viernes a {string} hasta {string}")
    public void cambioElHorario(String abre, String cierra) {
        atiendeDeLunesAViernes(abre, cierra);
    }

    @Dado("que el lunes es un día no laborable")
    public void elLunesNoSeAtiende() {
        horario.stream().filter(d -> "MONDAY".equals(d.get("dayOfWeek"))).forEach(d -> {
            d.put("open", false);
            d.put("opensAt", null);
            d.put("closesAt", null);
        });
        guardarHorario();
    }

    private void guardarHorario() {
        api.put("/api/v1/business-hours", Map.of("days", horario));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        assertThat((Boolean) api.read("$.configured")).isTrue();
    }

    @Cuando("marco la cita como atendida con la nota {string}")
    public void marcoComoAtendida(String nota) {
        api.patch("/api/v1/appointments/" + api.id("cita") + "/status",
                Map.of("status", "COMPLETED", "clinicalNotes", nota));
    }

    @Entonces("la cita queda en estado {string}")
    public void laCitaQuedaEnEstado(String estado) {
        assertThat((String) api.read("$.status")).isEqualTo(estado);
    }
}
