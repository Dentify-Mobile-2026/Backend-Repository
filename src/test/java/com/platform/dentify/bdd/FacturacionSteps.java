package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Dado;
import io.cucumber.java.es.Entonces;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de facturacion.feature (US11, US38, US46 a US51). */
public class FacturacionSteps {

    private final ApiClient api;

    public FacturacionSteps(ApiClient api) {
        this.api = api;
    }

    private Long metodoDePago() {
        api.get("/api/v1/payment-methods");
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        return api.readId("$[0].id");
    }

    @Cuando("emito un comprobante de {string} con un abono inicial de {string}")
    public void emitoUnComprobante(String total, String abono) {
        Long metodo = metodoDePago();
        api.post("/api/v1/invoices", Map.of(
                "amount", new BigDecimal(total),
                "appointmentId", api.id("cita"),
                "paymentMethodId", metodo,
                "initialPayment", new BigDecimal(abono)));
        if (api.status() == 201) {
            api.remember("comprobante", api.readId("$.id"));
            api.remember("metodo", metodo);
        }
    }

    @Dado("que emití un comprobante de {string} con un abono inicial de {string}")
    public void emitiUnComprobante(String total, String abono) {
        emitoUnComprobante(total, abono);
        assertThat(api.status()).as(api.body()).isEqualTo(201);
    }

    @Cuando("registro un abono de {string}")
    public void registroUnAbono(String monto) {
        registraAbono(monto, UUID.randomUUID().toString());
    }

    @Cuando("registro dos veces el mismo abono de {string}")
    public void registroDosVecesElMismoAbono(String monto) {
        String requestId = UUID.randomUUID().toString();
        registraAbono(monto, requestId);
        assertThat(api.status()).as(api.body()).isEqualTo(201);
        registraAbono(monto, requestId);
    }

    private void registraAbono(String monto, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("amount", new BigDecimal(monto));
        body.put("paymentMethodId", api.id("metodo"));
        body.put("notes", "Abono de prueba");
        body.put("requestId", requestId);
        api.post("/api/v1/invoices/" + api.id("comprobante") + "/payments", body);
    }

    @Entonces("el comprobante queda en estado {string} con saldo {string}")
    public void elComprobanteQueda(String estado, String saldo) {
        assertThat((String) api.read("$.status")).isEqualTo(estado);
        assertThat(new BigDecimal(api.read("$.pendingBalance").toString())).isEqualByComparingTo(saldo);
    }

    @Entonces("el comprobante sigue en estado {string} con saldo {string}")
    public void elComprobanteSigue(String estado, String saldo) {
        api.get("/api/v1/invoices/" + api.id("cita"));
        assertThat(api.status()).as(api.body()).isEqualTo(200);
        elComprobanteQueda(estado, saldo);
    }
}
