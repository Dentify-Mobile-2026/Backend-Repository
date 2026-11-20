package com.platform.dentify.invoices;

import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import com.platform.dentify.invoices.infrastructure.seeding.PaymentMethodDataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PaymentMethodDataSeederTest {

    private PaymentMethodRepository repository;
    private PaymentMethodDataSeeder seeder;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentMethodRepository.class);
        seeder = new PaymentMethodDataSeeder(repository);
    }

    @Test
    void seedsTheFourDefaultMethodsOnAnEmptyDatabase() {
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(false);

        int created = seeder.seed();

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(repository, times(4)).save(saved.capture());
        assertEquals(4, created);
        assertEquals(
                List.of("Efectivo", "Tarjeta de Credito", "Tarjeta de Debito", "Transferencia"),
                saved.getAllValues().stream().map(PaymentMethod::getName).toList());
        assertEquals("Transferencia bancaria / Yape / Plin", saved.getAllValues().get(3).getDescription());
    }

    @Test
    void completesAPartiallySeededDatabaseWithoutDuplicates() {
        Set<String> existing = Set.of("Efectivo", "Transferencia");
        when(repository.existsByNameIgnoreCase(anyString()))
                .thenAnswer(invocation -> existing.contains(invocation.<String>getArgument(0)));

        int created = seeder.seed();

        ArgumentCaptor<PaymentMethod> saved = ArgumentCaptor.forClass(PaymentMethod.class);
        verify(repository, times(2)).save(saved.capture());
        assertEquals(2, created);
        assertEquals(List.of("Tarjeta de Credito", "Tarjeta de Debito"),
                saved.getAllValues().stream().map(PaymentMethod::getName).toList());
    }

    @Test
    void doesNothingWhenAllMethodsAlreadyExist() {
        when(repository.existsByNameIgnoreCase(anyString())).thenReturn(true);

        assertEquals(0, seeder.seed());
        verify(repository, never()).save(any());
    }
}
