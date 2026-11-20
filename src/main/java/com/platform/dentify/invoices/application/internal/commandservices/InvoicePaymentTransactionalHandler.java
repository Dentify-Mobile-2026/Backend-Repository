package com.platform.dentify.invoices.application.internal.commandservices;

import com.platform.dentify.invoices.domain.exceptions.IdempotencyKeyConflictException;
import com.platform.dentify.invoices.domain.exceptions.InvoiceNotFoundException;
import com.platform.dentify.invoices.domain.model.aggregates.Invoice;
import com.platform.dentify.invoices.domain.model.commands.RegisterInvoicePaymentCommand;
import com.platform.dentify.invoices.domain.model.entities.InvoicePayment;
import com.platform.dentify.invoices.domain.model.entities.PaymentMethod;
import com.platform.dentify.invoices.domain.model.valueobjects.PaymentRegistrationResult;
import com.platform.dentify.invoices.infrastructure.repositories.InvoicePaymentRepository;
import com.platform.dentify.invoices.infrastructure.repositories.InvoiceRepository;
import com.platform.dentify.invoices.infrastructure.repositories.PaymentMethodRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Parte transaccional del registro de abonos. Cada método es una transacción completa:
 * si algo falla, nada queda escrito.
 */
@Component
public class InvoicePaymentTransactionalHandler {

    private final InvoiceRepository invoiceRepository;
    private final InvoicePaymentRepository invoicePaymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final Clock clock;

    public InvoicePaymentTransactionalHandler(InvoiceRepository invoiceRepository,
                                              InvoicePaymentRepository invoicePaymentRepository,
                                              PaymentMethodRepository paymentMethodRepository,
                                              Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoicePaymentRepository = invoicePaymentRepository;
        this.paymentMethodRepository = paymentMethodRepository;
        this.clock = clock;
    }

    /**
     * 1) Bloquea el comprobante del usuario ({@code FOR UPDATE}); los abonos concurrentes sobre
     * el mismo comprobante quedan serializados.
     * 2) Si el requestId ya existe, responde como repetición (mismos datos) o conflicto.
     * 3) Registra el abono con las reglas del agregado y fuerza el flush dentro de la
     * transacción para que cualquier violación de restricción ocurra aquí.
     * <p>
     * READ_COMMITTED garantiza que la búsqueda del paso 2, hecha después de obtener el bloqueo,
     * vea lo que otra transacción ya confirmó (en REPEATABLE READ podría leer una instantánea
     * anterior).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentRegistrationResult register(RegisterInvoicePaymentCommand command, Long userId) {
        Invoice invoice = invoiceRepository.findByIdAndOwnerForUpdate(command.invoiceId(), userId)
                .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));

        Optional<InvoicePayment> previous = invoicePaymentRepository.findByRequestId(command.requestId());
        if (previous.isPresent()) {
            return replayOrConflict(previous.get(), command, invoice);
        }

        PaymentMethod method = paymentMethodRepository.findPaymentMethodById(command.paymentMethodId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "El método de pago " + command.paymentMethodId() + " no existe"));

        InvoicePayment payment = invoice.registerPayment(command.amount(), method, clock.instant(),
                command.notes(), command.requestId());
        // El comprobante ya está gestionado (cargado con bloqueo): se persiste el abono nuevo de
        // forma explícita y el cambio de estado del comprobante se guarda por dirty checking.
        // Un merge del comprobante no insertaría correctamente el abono transitorio.
        invoicePaymentRepository.saveAndFlush(payment);
        return new PaymentRegistrationResult(invoice, true);
    }

    /**
     * Recuperación tras una violación de {@code uk_invoice_payment_request_id}: se ejecuta en
     * una transacción nueva, después de que la transacción fallida se revirtió por completo.
     */
    @Transactional(readOnly = true)
    public PaymentRegistrationResult replayAfterDuplicateRequest(RegisterInvoicePaymentCommand command, Long userId) {
        Invoice invoice = invoiceRepository.findByIdAndAppointment_Patient_User_Id(command.invoiceId(), userId)
                .orElseThrow(() -> new InvoiceNotFoundException(command.invoiceId()));
        InvoicePayment previous = invoicePaymentRepository.findByRequestId(command.requestId())
                .orElseThrow(() -> new IllegalStateException(
                        "Violación de requestId sin abono confirmado: " + command.requestId()));
        invoice.getPayments().size(); // inicializa los abonos dentro de la transacción
        return replayOrConflict(previous, command, invoice);
    }

    /**
     * Es la misma operación solo si coinciden comprobante (y por tanto usuario), monto, método
     * y notas. Si el requestId se reutilizó con otros datos, es un conflicto (409).
     */
    static PaymentRegistrationResult replayOrConflict(InvoicePayment previous, RegisterInvoicePaymentCommand command,
                                                     Invoice invoice) {
        boolean sameOperation = Objects.equals(previous.getInvoice().getId(), invoice.getId())
                && previous.getAmount().compareTo(command.amount()) == 0
                && Objects.equals(previous.getPaymentMethod().getId(), command.paymentMethodId())
                && Objects.equals(previous.getNotes(), command.notes());
        if (!sameOperation) {
            throw new IdempotencyKeyConflictException(command.requestId());
        }
        return new PaymentRegistrationResult(invoice, false);
    }
}
