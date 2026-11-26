package com.platform.dentify.bdd;

import io.cucumber.java.es.Cuando;
import io.cucumber.java.es.Entonces;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Pasos de acceso.feature (US01, US02, US29, US30, US59). */
public class AccesoSteps {

    private final ApiClient api;

    public AccesoSteps(ApiClient api) {
        this.api = api;
    }

    @Cuando("inicio sesión con mi usuario y mi contraseña")
    public void inicioSesion() {
        api.post("/api/auth/login", Map.of("username", api.value("usuario"), "password", api.value("clave")));
    }

    @Cuando("inicio sesión con mi usuario y una contraseña incorrecta")
    public void inicioSesionConClaveIncorrecta() {
        api.post("/api/auth/login", Map.of("username", api.value("usuario"), "password", "no-es-mi-clave"));
    }

    @Cuando("consulto la lista de pacientes sin iniciar sesión")
    public void consultoPacientesSinSesion() {
        api.clearToken();
        api.get("/api/v1/patients");
    }

    @Cuando("consulto mi perfil")
    public void consultoMiPerfil() {
        api.get("/api/v1/profile");
    }

    @Entonces("recibo un token de acceso")
    public void reciboUnToken() {
        assertThat((String) api.read("$.accessToken")).isNotBlank();
    }

    @Entonces("el perfil muestra mi nombre de usuario")
    public void elPerfilMuestraMiUsuario() {
        assertThat((String) api.read("$.username")).isEqualTo(api.value("usuario"));
    }
}
