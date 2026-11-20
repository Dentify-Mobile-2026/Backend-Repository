package com.platform.dentify.iam;

import com.platform.dentify.iam.domain.services.AuthCommandService;
import com.platform.dentify.iam.interfaces.rest.AuthController;
import com.platform.dentify.iam.interfaces.rest.AuthExceptionHandler;
import com.platform.dentify.iam.interfaces.rest.resources.AuthResponseResource;
import com.platform.dentify.iam.interfaces.rest.resources.RegisterResponseResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inicio de sesión y registro: los datos inválidos y las credenciales incorrectas responden
 * 400 y 401 con {@code {"error": "..."}}, nunca 500.
 */
class AuthControllerErrorResponsesTest {

    private AuthCommandService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthCommandService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    private static final String VALID_REGISTER = "{\"firstName\":\"Valeria\",\"lastName\":\"Campos\","
            + "\"email\":\"demo@dentify.test\",\"companyName\":\"Consultorio\",\"username\":\"valeria\","
            + "\"password\":\"Clave-12345\",\"trial\":true}";

    @Test
    void emptyPasswordIsA400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password cannot be empty"));
        verifyNoInteractions(authService);
    }

    @Test
    void missingFieldsAreA400InsteadOfANullPointer() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Username cannot be empty"));
    }

    @Test
    void malformedJsonIsA400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la solicitud no es válido"));
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSame401Message() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Credenciales inválidas"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));

        doThrow(new UsernameNotFoundException("usuario no encontrado")).when(authService).login(any());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nadie\",\"password\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Usuario o contraseña incorrectos"));
    }

    @Test
    void validLoginStillReturnsTheTokens() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponseResource("acceso", "refresco"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo\",\"password\":\"buena\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acceso"));
    }

    @Test
    void duplicateUsernameOnRegisterIsA400WithItsMessage() throws Exception {
        when(authService.register(any())).thenThrow(new IllegalArgumentException("el nombre de usuario ya esta en uso"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("el nombre de usuario ya esta en uso"));
    }

    @Test
    void registerWithShortNameIsA400AndSuccessIsA201() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER.replace("\"Valeria\"", "\"Va\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("First name must be at least 3 characters"));

        when(authService.register(any())).thenReturn(new RegisterResponseResource("acceso", "refresco", "valeria"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID_REGISTER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("valeria"));
    }
}
