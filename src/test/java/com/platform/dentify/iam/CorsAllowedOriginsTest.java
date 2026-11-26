package com.platform.dentify.iam;

import com.platform.dentify.iam.infrastructure.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Comprueba la lista de orígenes que {@code deploy-azure.ps1} pone por defecto en producción: la
 * web y la landing siguen funcionando y cualquier otro sitio queda bloqueado.
 */
class CorsAllowedOriginsTest {

    /** Mismo valor que usa deploy-azure.ps1 cuando no se pasa -CorsAllowedOrigins. */
    private static final String PRODUCTION_DEFAULT =
            "https://dentify-mobile-2026.github.io,https://dentify-api-upc.azurewebsites.net,"
                    + "http://localhost:[*],http://127.0.0.1:[*]";

    private static CorsConfiguration corsFor(String origins) {
        var config = new SecurityConfig(mock(com.platform.dentify.iam.infrastructure.jwt.JwtAuthFilter.class), origins);
        return config.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/patients"));
    }

    @Test
    void productionDefaultKeepsTheWebLandingSwaggerAndLocalDevelopment() {
        var cors = corsFor(PRODUCTION_DEFAULT);

        assertThat(cors.checkOrigin("https://dentify-mobile-2026.github.io")).isNotNull();
        assertThat(cors.checkOrigin("https://dentify-api-upc.azurewebsites.net")).isNotNull();
        assertThat(cors.checkOrigin("http://localhost:8081")).isNotNull();
        assertThat(cors.checkOrigin("http://127.0.0.1:5500")).isNotNull();
    }

    @Test
    void productionDefaultBlocksOtherSites() {
        var cors = corsFor(PRODUCTION_DEFAULT);

        assertThat(cors.checkOrigin("https://evil.example.com")).isNull();
        assertThat(cors.checkOrigin("https://dentify-mobile-2026.github.io.evil.com")).isNull();
    }

    @Test
    void withoutConfigurationEveryOriginIsAllowedAsBefore() {
        assertThat(corsFor("*").checkOrigin("https://any.example.com")).isNotNull();
        assertThat(corsFor("").checkOrigin("https://any.example.com")).isNotNull();
    }
}
