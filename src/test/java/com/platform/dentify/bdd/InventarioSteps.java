package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de inventario.feature (US20 a US23, US25 a US28, US57). */
public class InventarioSteps {

    private final ApiClient api;

    public InventarioSteps(ApiClient api) {
        this.api = api;
    }

    @Cuando("registro el insumo {string} con {int} unidades")
    public void registroElInsumo(String nombre, int unidades) {
        api.post("/api/v1/items", Map.of(
                "name", nombre,
                "price", 15.0,
                "stockQuantity", unidades,
                "category", "DISPOSABLE_SUPPLIES"));
        if (api.status() == 201) {
            api.remember("insumo", api.readId("$.id"));
        }
    }

    @Dado("que tengo el insumo {string} con {int} unidades")
    public void tengoElInsumo(String nombre, int unidades) {
        registroElInsumo(nombre, unidades);
        assertThat(api.status()).as(api.body()).isEqualTo(201);
    }

    @Cuando("registro el consumo de {int} unidades en esa atención")
    public void registroElConsumo(int unidades) {
        Map<String, Object> body = new HashMap<>();
        body.put("itemId", api.id("insumo"));
        body.put("consumedQuantity", unidades);
        body.put("invoiceId", api.id("comprobante"));
        api.post("/api/v1/log-entries", body);
    }

    @Entonces("el insumo queda con {int} unidades")
    public void elInsumoQuedaCon(int unidades) {
        api.get("/api/v1/items/" + api.id("insumo"));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        assertThat((Integer) api.read("$.stockQuantity")).isEqualTo(unidades);
    }

    @Entonces("el movimiento queda registrado de {int} a {int} unidades")
    public void elMovimientoQuedaRegistrado(int antes, int despues) {
        api.get("/api/v1/log-entries");
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        // El alta del insumo también deja su propio movimiento; aquí se busca el del consumo.
        List<Map<String, Object>> consumos = api.read("$[?(@.itemId == " + api.id("insumo")
                + " && @.previousStock == " + antes + " && @.newStock == " + despues + ")]");
        assertThat(consumos).hasSize(1);
        assertThat(consumos.get(0)).containsEntry("consumedQuantity", antes - despues);
    }
}
