package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de tratamientos.feature (US41, US52 a US54). */
public class TratamientosSteps {

    private final ApiClient api;
    private final ComunesSteps comunes;

    public TratamientosSteps(ApiClient api, ComunesSteps comunes) {
        this.api = api;
        this.comunes = comunes;
    }

    @Cuando("registro el tratamiento {string} de {string} soles y {int} minutos")
    public void registroElTratamiento(String nombre, String precio, int minutos) {
        api.post("/api/v1/treatments", Map.of(
                "name", nombre,
                "description", "Tratamiento de prueba",
                "category", "RESTORATIVE",
                "price", Double.valueOf(precio),
                "estimatedDurationMinutes", minutos));
        if (api.status() == 201) {
            api.remember("tratamiento", api.readId("$.id"));
            api.remember("precio", precio);
        }
    }

    @Dado("que tengo el tratamiento {string} de {string} soles y {int} minutos")
    public void tengoElTratamiento(String nombre, String precio, int minutos) {
        registroElTratamiento(nombre, precio, minutos);
        assertThat(api.status()).as(api.body()).isEqualTo(201);
    }

    @Cuando("armo un plan con ese tratamiento en las piezas {string} y {string}")
    public void armoUnPlan(String pieza1, String pieza2) {
        BigDecimal precio = new BigDecimal((String) api.value("precio"));
        api.post("/api/v1/patients/" + api.id("paciente") + "/treatment-plans", Map.of(
                "title", "Plan de restauraciones",
                "notes", "Plan de prueba",
                "items", List.of(
                        Map.of("treatmentId", api.id("tratamiento"), "toothNumber", pieza1, "agreedPrice", precio),
                        Map.of("treatmentId", api.id("tratamiento"), "toothNumber", pieza2, "agreedPrice", precio))));
        if (api.status() == 201) {
            api.remember("plan", api.readId("$.id"));
            api.remember("pieza", api.readId("$.items[0].id"));
        }
    }

    @Dado("que tengo un paciente con un plan aprobado de dos piezas")
    public void tengoUnPlanAprobado() {
        comunes.tengoUnPaciente();
        tengoElTratamiento("Resina simple", "120.50", 30);
        armoUnPlan("11", "21");
        assertThat(api.status()).as(api.body()).isEqualTo(201);
        api.put("/api/v1/patients/" + api.id("paciente") + "/treatment-plans/" + api.id("plan") + "/status",
                Map.of("status", "APPROVED"));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
    }

    @Cuando("marco la primera pieza como hecha")
    public void marcoLaPrimeraPieza() {
        api.put("/api/v1/treatment-plans/" + api.id("plan") + "/items/" + api.id("pieza") + "/status",
                Map.of("status", "DONE"));
    }

    @Entonces("el plan suma {string}")
    public void elPlanSuma(String total) {
        assertThat(new BigDecimal(api.read("$.totalAmount").toString())).isEqualByComparingTo(total);
    }

    @Entonces("el plan muestra un avance de {string} por ciento")
    public void elPlanMuestraUnAvance(String avance) {
        assertThat(new BigDecimal(api.read("$.progressPercentage").toString())).isEqualByComparingTo(avance);
    }
}
