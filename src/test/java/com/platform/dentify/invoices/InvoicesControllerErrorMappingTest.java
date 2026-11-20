package com.platform.dentify.invoices;

import com.platform.dentify.invoices.domain.exceptions.AppointmentNotFoundException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceAlreadyExistsException;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.CreateInvoiceCommand;
import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.services.InvoiceCommandService;
import com.platform.dentify.invoices.domain.services.InvoiceQueryService;
import com.platform.dentify.invoices.interfaces.rest.InvoicesController;
import com.platform.dentify.invoices.interfaces.rest.InvoicesExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba la traducción de errores del controlador con el advice real del módulo. La
 * cadena de seguridad y el dispatch de /error se cubren en las pruebas de integración.
 */
class InvoicesControllerErrorMappingTest {

    private InvoiceCommandService commandService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        commandService = mock(InvoiceCommandService.class);
        var controller = new InvoicesController(commandService, mock(InvoiceQueryService.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new InvoicesExceptionHandler())
                .build();
    }

    private static String body(String amountJson) {
        return "{\"appointmentId\": 11, \"amount\": " + amountJson + ", \"paymentMethodId\": 9}";
    }

    private Invoice createdInvoice(CreateInvoiceCommand command) {
        var invoice = new Invoice(command);
        var method = new PaymentMethod(new CreatePaymentMethodCommand("Tarjeta de Credito", "Pago con tarjeta de credito"));
        ReflectionTestUtils.setField(method, "id", 9L);
        // Como el servicio: sin abono inicial explícito se cobra el total con el método elegido.
        invoice.openBilling(command.amount(), method, Instant.parse("2026-11-20T15:00:00Z"));
        return invoice;
    }

    @Test
    void acceptsAmountAsDecimalStringAndReturnsExactTotal() throws Exception {
        when(commandService.handle(any())).thenAnswer(inv -> Optional.of(createdInvoice(inv.getArgument(0))));

        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body("\"120.50\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(120.50))
                .andExpect(jsonPath("$.paymentMethodId").value(9))
                .andExpect(jsonPath("$.paymentMethodName").value("Tarjeta de Credito"))
                // El método informado sale del abono registrado, no de una copia en el comprobante.
                .andExpect(jsonPath("$.paymentsCount").value(1))
                .andExpect(jsonPath("$.paymentMethods[0]").value("Tarjeta de Credito"));

        ArgumentCaptor<CreateInvoiceCommand> captor = ArgumentCaptor.forClass(CreateInvoiceCommand.class);
        verify(commandService).handle(captor.capture());
        assertEquals(new BigDecimal("120.50"), captor.getValue().amount());
    }

    @Test
    void acceptsAmountAsJsonNumber() throws Exception {
        when(commandService.handle(any())).thenAnswer(inv -> Optional.of(createdInvoice(inv.getArgument(0))));

        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body("99.99")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(99.99));
    }

    @Test
    void invalidAmountIsA400WithMessage() throws Exception {
        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body("\"120.555\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El monto admite como máximo 2 decimales"));
        verifyNoInteractions(commandService);
    }

    @Test
    void malformedJsonIsA400WithoutInternalDetails() throws Exception {
        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("El cuerpo de la solicitud no es válido"));
    }

    @Test
    void appointmentNotFoundIsA404() throws Exception {
        when(commandService.handle(any())).thenThrow(new AppointmentNotFoundException(11L));

        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body("50")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void duplicateInvoiceIsA409() throws Exception {
        when(commandService.handle(any())).thenThrow(new InvoiceAlreadyExistsException(11L));

        mockMvc.perform(post("/api/v1/invoices").contentType(MediaType.APPLICATION_JSON).content(body("50")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("La cita 11 ya tiene un comprobante registrado"));
    }
}
