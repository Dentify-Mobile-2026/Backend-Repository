package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de panel.feature (US24, US58). */
public class PanelSteps {

    private final ApiClient api;

    public PanelSteps(ApiClient api) {
        this.api = api;
    }

    @Cuando("abro el panel")
    public void abroElPanel() {
        api.get("/api/v1/dashboard");
    }

    @Cuando("consulto la analítica del mes")
    public void consultoLaAnalitica() {
        api.get("/api/v1/dashboard/analytics");
    }

    @Entonces("lo cobrado en el mes es {string}")
    public void loCobradoEnElMesEs(String monto) {
        assertThat(new BigDecimal(api.read("$.financials.totalCollected").toString())).isEqualByComparingTo(monto);
    }
}
