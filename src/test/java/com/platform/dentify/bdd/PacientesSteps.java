package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de pacientes.feature (US12 a US19, US39, US40, US44). */
public class PacientesSteps {

    private final ApiClient api;
    private final ComunesSteps comunes;

    public PacientesSteps(ApiClient api, ComunesSteps comunes) {
        this.api = api;
        this.comunes = comunes;
    }

    @Cuando("registro un paciente alérgico a {string}")
    public void registroUnPacienteAlergico(String alergia) {
        comunes.registraPaciente(alergia);
        if (api.status() == 201) {
            api.remember("paciente", api.readId("$.id"));
        }
    }

    @Entonces("el paciente muestra la alergia {string}")
    public void elPacienteMuestraLaAlergia(String alergia) {
        api.get("/api/v1/patients/" + api.id("paciente"));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        assertThat((String) api.read("$.allergies")).isEqualTo(alergia);
    }

    @Cuando("consulto la ficha del paciente")
    public void consultoLaFicha() {
        api.get("/api/v1/patients/" + api.id("paciente"));
    }

    @Cuando("registro en su historia clínica el diagnóstico {string}")
    public void registroEnSuHistoria(String diagnostico) {
        api.post("/api/v1/patients/" + api.id("paciente") + "/medical-histories", Map.of(
                "description", "Consulta",
                "record", "Evaluación de la pieza 21",
                "diagnosis", diagnostico,
                "medication", "Ninguna"));
        if (api.status() == 201) {
            api.remember("historia", api.readId("$.id"));
        }
    }

    @Dado("que tengo un paciente con una entrada en su historia clínica")
    public void tengoUnaEntradaEnLaHistoria() {
        comunes.tengoUnPaciente();
        registroEnSuHistoria("Caries en pieza 21");
        assertThat(api.status()).as(api.body()).isEqualTo(201);
    }

    @Cuando("anulo la entrada con el motivo {string}")
    public void anuloConMotivo(String motivo) {
        api.patch(urlAnulacion(), Map.of("reason", motivo));
    }

    @Cuando("anulo la entrada sin motivo")
    public void anuloSinMotivo() {
        Map<String, Object> body = new HashMap<>();
        body.put("reason", "");
        api.patch(urlAnulacion(), body);
    }

    private String urlAnulacion() {
        return "/api/v1/patients/" + api.id("paciente") + "/medical-histories/" + api.id("historia") + "/annul";
    }

    @Entonces("la entrada queda anulada y sigue visible en la historia")
    public void quedaAnuladaYVisible() {
        assertThat((Boolean) api.read("$.annulled")).isTrue();
        api.get("/api/v1/patients/" + api.id("paciente") + "/medical-histories");
        assertThat(api.status()).isEqualTo(200);
        List<Boolean> anuladas = api.read("$[?(@.id == " + api.id("historia") + ")].annulled");
        assertThat(anuladas).containsExactly(true);
    }
}
