package com.platform.dentify.invoices.infrastructure.seeding;

import com.platform.dentify.invoices.domain.model.commands.CreatePaymentMethodCommand;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Inicializa los métodos de pago base al arrancar el backend.
 * <p>
 * Es idempotente por método: solo inserta los que no existen (comparando el nombre sin
 * distinguir mayúsculas), de modo que una base vacía, parcial o ya sembrada a mano con el
 * SQL de EJECUCION.md termina siempre con los cuatro métodos y sin duplicados.
 * No depende de identificadores fijos: los clientes deben usar los ids que devuelve
 * {@code GET /api/v1/payment-methods}.
 */
@Component
public class PaymentMethodDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentMethodDataSeeder.class);

    /** Mismos nombres y descripciones que el script manual de EJECUCION.md. */
    public static final List<CreatePaymentMethodCommand> DEFAULT_PAYMENT_METHODS = List.of(
            new CreatePaymentMethodCommand("Efectivo", "Pago en efectivo"),
            new CreatePaymentMethodCommand("Tarjeta de Credito", "Pago con tarjeta de credito"),
            new CreatePaymentMethodCommand("Tarjeta de Debito", "Pago con tarjeta de debito"),
            new CreatePaymentMethodCommand("Transferencia", "Transferencia bancaria / Yape / Plin")
    );

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodDataSeeder(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = seed();
        log.info("Métodos de pago: {} creados, {} ya existían", created, DEFAULT_PAYMENT_METHODS.size() - created);
    }

    /** Inserta los métodos faltantes y devuelve cuántos se crearon. */
    public int seed() {
        int created = 0;
        for (CreatePaymentMethodCommand command : DEFAULT_PAYMENT_METHODS) {
            if (!paymentMethodRepository.existsByNameIgnoreCase(command.name())) {
                paymentMethodRepository.save(new PaymentMethod(command));
                created++;
            }
        }
        return created;
    }
}
