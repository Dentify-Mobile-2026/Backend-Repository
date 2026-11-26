package com.platform.dentify.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.Before;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Cliente HTTP de un escenario: guarda la sesión, la última respuesta y los identificadores que
 * crean los pasos (paciente, cita, comprobante…). Cucumber lo crea de nuevo en cada escenario y
 * lo inyecta en las clases de pasos. No lleva {@code @Component} para que las otras pruebas de
 * Spring no lo registren.
 */
public class ApiClient {

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final Map<String, Object> ids = new HashMap<>();
    private String accessToken;
    private int status;
    private String body = "";

    public ApiClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    @Before(order = 0)
    public void startScenario() {
        ids.clear();
        accessToken = null;
        status = 0;
        body = "";
    }

    /** Texto único por escenario: la base H2 se comparte entre escenarios. */
    public static String unique() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public void useToken(String token) {
        this.accessToken = token;
    }

    public void clearToken() {
        this.accessToken = null;
    }

    public ApiClient get(String url) {
        return send(HttpMethod.GET, url, null);
    }

    public ApiClient post(String url, Object payload) {
        return send(HttpMethod.POST, url, payload);
    }

    public ApiClient put(String url, Object payload) {
        return send(HttpMethod.PUT, url, payload);
    }

    public ApiClient patch(String url, Object payload) {
        return send(HttpMethod.PATCH, url, payload);
    }

    public ApiClient delete(String url) {
        return send(HttpMethod.DELETE, url, null);
    }

    private ApiClient send(HttpMethod method, String url, Object payload) {
        try {
            MockHttpServletRequestBuilder req = request(method, url).accept(MediaType.APPLICATION_JSON);
            if (payload != null) {
                req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload));
            }
            if (accessToken != null) {
                req.header("Authorization", "Bearer " + accessToken);
            }
            MockHttpServletResponse response = mvc.perform(req).andReturn().getResponse();
            status = response.getStatus();
            body = response.getContentAsString(StandardCharsets.UTF_8);
            return this;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo llamar a " + method + " " + url, e);
        }
    }

    public int status() {
        return status;
    }

    public String body() {
        return body;
    }

    public <T> T read(String path) {
        return JsonPath.read(body, path);
    }

    public Long readId(String path) {
        Object value = JsonPath.read(body, path);
        return ((Number) value).longValue();
    }

    public void remember(String key, Object value) {
        ids.put(key, value);
    }

    public Long id(String key) {
        Object value = ids.get(key);
        if (value == null) {
            throw new IllegalStateException("El escenario no creó «" + key + "»");
        }
        return ((Number) value).longValue();
    }

    public Object value(String key) {
        return ids.get(key);
    }
}
